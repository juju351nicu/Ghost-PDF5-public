package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 処理状態の表示と、エラーコードのBE / FE整合を固定するテスト。
 * <p>
 * 以前は画面状態がbooleanの {@code isProcessing} だけで、処理中はボタンがdisabledになる以外に
 * 何も伝わらなかった。またBEのエラーコードをFEが解釈していなかったため、利用者が自分で直せる失敗
 * （サイズ超過、パスワード保護、ページ上限超過）も想定外エラーと同じモーダルへ流れていた。
 * JavaScriptのテストランナーを持たない構成のため、その再発をソーススキャンで検知する。
 */
class FrontendProcessStateContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
	private static final Path ERROR_HANDLER_SOURCE = Paths
			.get("src/main/java/com/clip/ghost/common/exceptions/handler/GlobalExceptionErrorHandler.java");
	private static final Pattern ERROR_CODE_CONSTANT = Pattern
			.compile("private static final String \\w*ERROR_CODE = \"([^\"]+)\";");

	/**
	 * BEが返すエラーコードを、FEがすべて把握していることを確認する。
	 * <p>
	 * BE側でコードを増やしたときにFEの定義を足し忘れると、そのエラーは「利用者が直せるか」の判定から
	 * 静かに漏れ、専用表示へ回らなくなる。追加漏れをここで落とす。
	 *
	 * @throws IOException ソースを読み込めない場合
	 */
	@Test
	@DisplayName("BEのエラーコードをFEがすべて把握している")
	void frontendKnowsEveryBackendErrorCode() throws IOException {
		String errorHandler = Files.readString(ERROR_HANDLER_SOURCE, StandardCharsets.UTF_8);
		String apiErrorUtils = read("static/js/api/api-error-utils.js");

		Matcher matcher = ERROR_CODE_CONSTANT.matcher(errorHandler);
		while (matcher.find()) {
			String errorCode = matcher.group(1);
			assertTrue(apiErrorUtils.contains("\"" + errorCode + "\""),
					"api-error-utils.js の ERROR_CODE へ " + errorCode + " を追加してください。");
		}
	}

	/**
	 * パスワードで保護されたPDFが、利用者が直せる失敗として扱われることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("パスワード保護PDFを利用者が直せる失敗として扱う")
	void passwordProtectedPdfIsTreatedAsRecoverableError() throws IOException {
		String apiErrorUtils = read("static/js/api/api-error-utils.js");

		assertAll(
				() -> assertTrue(apiErrorUtils.contains("ERROR_CODE.PDF_PASSWORD_PROTECTED,"),
						"パスワード保護は利用者が直せる失敗として RECOVERABLE_ERROR_CODES へ含めてください。"),
				() -> assertTrue(apiErrorUtils.contains("const isRecoverableError"),
						"利用者が直せる失敗かどうかの判定を api-error-utils.js へ置いてください。"));
	}

	/**
	 * 画面状態がbooleanではなく状態機械で表現されていることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("処理状態をbooleanではなく状態で持つ")
	void processStateIsRepresentedByStateMachine() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String processState = read("static/js/models/process-state.js");

		assertAll(
				() -> assertFalse(pdfApp.contains("this.isProcessing = "),
						"isProcessingへ直接代入せず、beginProcess / finishProcess などの状態遷移を使ってください。"),
				() -> assertTrue(pdfApp.contains("processPanel: ProcessState.createProcessPanelState()"),
						"処理状態パネルのapp stateを用意してください。"),
				// 各コンポーネントへ渡している is-processing の契約を壊さないよう、computedで公開し続ける。
				() -> assertTrue(pdfApp.contains("return ProcessState.isBusyState(this.processPanel.state);"),
						"isProcessingはcomputedとして状態から導いてください。"),
				() -> assertTrue(
						List.of("IDLE", "PROCESSING", "DONE", "NEEDS_ACTION", "PASSWORD_REQUIRED", "ERROR").stream()
								.allMatch(state -> processState.contains(state + ": \"" + state + "\"")),
						"process-state.jsへ IDLE / PROCESSING / DONE / NEEDS_ACTION / PASSWORD_REQUIRED / ERROR"
								+ " を定義してください。"));
	}

	/**
	 * パスワードで保護されたPDFを、入力して開き直せることを確認する。
	 * <p>
	 * 検出だけでは「開けない」と伝わるだけで処理できない。入力欄を出し、同じ操作をやり直すところまでを固定する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("保護されたPDFはパスワードを入力して開き直せる")
	void passwordProtectedPdfCanBeRetriedWithPassword() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String processPanel = read("static/js/components/process-panel.js");
		String pdfPayload = read("static/js/api/pdf-payload.js");

		assertAll(
				() -> assertTrue(pdfApp.contains("ApiErrorUtils.isPasswordError(errorCodes)"),
						"パスワードで解決できる失敗を、他の失敗と分けて判定してください。"),
				() -> assertTrue(pdfApp.contains("submitPdfPassword()"),
						"入力されたパスワードで操作をやり直すmethodを用意してください。"),
				() -> assertTrue(pdfApp.contains("this.pendingPasswordRetry = () =>"),
						"やり直す対象の操作を保持してください。"),
				() -> assertTrue(processPanel.contains("type=\"password\""),
						"パスワードは伏字の入力欄で受け取ってください。"),
				() -> assertTrue(pdfPayload.contains("const withPassword"),
						"payloadへパスワードを足す処理を pdf-payload.js へ置いてください。"));
	}

	/**
	 * 入力されたパスワードをブラウザへ保存しないことを確認する。
	 * <p>
	 * 認証情報のため、画面を閉じたら消える範囲に留める。別のPDFへ持ち越すこともしない。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("入力されたパスワードを保存せず、別のファイルへ持ち越さない")
	void pdfPasswordIsNeitherStoredNorCarriedOver() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertFalse(pdfApp.contains("localStorage"), "パスワードを含む画面状態をブラウザへ保存しないでください。"),
				() -> assertFalse(pdfApp.contains("sessionStorage"), "パスワードを含む画面状態をブラウザへ保存しないでください。"),
				() -> assertTrue(pdfApp.contains("clearPdfPassword()"), "保持したパスワードを破棄するmethodを用意してください。"),
				// ファイルを受け付けた時点と全クリア時の2箇所で破棄する。
				() -> assertTrue(countOccurrences(pdfApp, "this.clearPdfPassword();") >= 2,
						"ファイルの選び直しと全クリアの両方でパスワードを破棄してください。"));
	}

	/**
	 * 対象文字列に含まれる部分文字列の出現回数を数える。
	 *
	 * @param source 検索対象
	 * @param target 数える部分文字列
	 * @return 出現回数
	 */
	private int countOccurrences(String source, String target) {
		int count = 0;
		int index = source.indexOf(target);
		while (index >= 0) {
			count++;
			index = source.indexOf(target, index + target.length());
		}
		return count;
	}

	/**
	 * ドロップとファイル選択が同じ検証を通ることを確認する。
	 * <p>
	 * 入力経路ごとに検証が分かれると、ドロップだけ上限超過や拡張子違いを素通りさせる穴ができる。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("ドロップとファイル選択が同じ検証を通る")
	void droppedFileAndSelectedFileShareValidation() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertTrue(pdfApp.contains("handlePdfFilesDropped(files, fileNo)"),
						"ドロップされたファイルの受け口を用意してください。"),
				() -> assertTrue(pdfApp.contains("applyPdfFile(fileObject, fileNo)"),
						"ファイル選択とドロップの共通処理を applyPdfFile へまとめてください。"),
				() -> assertTrue(pdfApp.contains("FileTypeValidator.isPdfFile(fileObject)"),
						"ドロップは拡張子で絞れないため、PDFかどうかを検証してください。"),
				() -> assertTrue(pdfApp.contains("FileSizeValidator.isWithinPdfSizeLimit(fileObject)"),
						"上限超過は送信前に止めてください。"));
	}

	/**
	 * ドロップ領域の外へ落としたファイルでページが離脱しないことを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("領域外へ落としたファイルでページを離脱しない")
	void fileDroppedOutsideDropZoneDoesNotNavigateAway() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertTrue(pdfApp.contains("window.addEventListener(\"drop\", this.preventWindowFileDrop)"),
						"領域外へのドロップはwindow側で既定動作を止めてください。"),
				() -> assertTrue(pdfApp.contains("window.removeEventListener(\"drop\", this.preventWindowFileDrop)"),
						"登録したlistenerはbeforeUnmountで解除してください。"));
	}

	/**
	 * プロジェクト相対パスのフロントエンドresourceをUTF-8で読み込む。
	 *
	 * @param relativePath {@link #RESOURCE_ROOT} からの相対パス
	 * @return resource内容
	 * @throws IOException resourceを読み込めない場合
	 */
	private String read(String relativePath) throws IOException {
		return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
