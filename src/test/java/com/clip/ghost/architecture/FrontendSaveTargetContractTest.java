package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * ダウンロード保存先の選択（File System Access API）の接続を固定するテスト。
 * <p>
 * このAPIはtransient activation（利用者操作の直後）を要求し、fetch完了後では失効している。
 * そのため「クリック直後にピッカー → API呼び出し → 書き込み」という順序が崩れると動かなくなる。
 * ブラウザ差は自動テストで検証できないため、崩れやすい接続だけをソーススキャンで固定する。
 */
class FrontendSaveTargetContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * 保存先の取得が未対応ブラウザ・キャンセル・取得成功の3状態を返すことを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void saveTargetDistinguishesUnsupportedAndCancelled() throws IOException {
		String fileResponseHandler = read("static/js/api/file-response-handler.js");

		assertAll(
				() -> assertTrue(fileResponseHandler.contains("const requestSaveTarget")),
				// 未対応とキャンセルを同じ値へ寄せると、Firefoxがキャンセル扱いになって何も起きなくなる。
				() -> assertTrue(
						fileResponseHandler.contains("typeof window.showSaveFilePicker !== \"function\"")),
				() -> assertTrue(fileResponseHandler.contains("return { supported: false, cancelled: false, handle: null };")),
				() -> assertTrue(fileResponseHandler.contains("return { supported: true, cancelled: true, handle: null };")),
				() -> assertTrue(fileResponseHandler.contains("PICKER_CANCEL_ERROR_NAME")));
	}

	/**
	 * 保存先がAPI呼び出しの前に取得され、ダウンロード処理まで渡ることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void saveTargetIsRequestedBeforeApiCallAndPassedToDownload() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String apiClient = read("static/js/api/pdf-api-client.js");
		String fileResponseHandler = read("static/js/api/file-response-handler.js");
		int pickerIndex = pdfApp.indexOf("FileResponseHandler.requestSaveTarget(");
		int requestIndex = pdfApp.indexOf("PdfApiClient.requestFileAndDownload(");

		assertAll(() -> assertTrue(pickerIndex > 0, "pdf-app.jsから保存先の選択を呼んでください。"),
				() -> assertTrue(requestIndex > 0),
				// ピッカーはクリック直後に呼ぶ必要があるため、API呼び出しより前に置く。
				() -> assertTrue(pickerIndex < requestIndex,
						"保存先の選択はAPI呼び出しより前に行ってください。"),
				() -> assertTrue(pdfApp.contains("if (saveTarget.cancelled) {"),
						"キャンセル時はAPIを呼ばずに終了してください。"),
				() -> assertTrue(pdfApp.contains("saveTarget.handle")),
				() -> assertTrue(apiClient.contains("saveTarget")),
				// 書き込み失敗を成功扱いにしないため、awaitして例外を伝える。
				() -> assertTrue(apiClient.contains("await FileResponseHandler.downloadBlob(")),
				() -> assertTrue(fileResponseHandler.contains("await saveTarget.createWritable()")));
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
