package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * サムネイル一覧からのページ選択で、画面とAPIの接続を固定するテスト。
 * <p>
 * サムネイルはコンポーネント・payload・API client・app stateをまたぐため、
 * JavaScriptのテストランナーを持たない構成でも接続欠落をMavenテストで検知する。
 */
class FrontendThumbnailContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * サムネイル取得が既存の責務別JavaScript境界を通ってAPIへ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void thumbnailUiUsesExistingFrontendBoundaries() throws IOException {
		String mainTemplate = read("templates/main.html");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String thumbnailList = read("static/js/components/pdf-thumbnail-list.js");
		String constants = read("static/js/const.js");
		String payload = read("static/js/api/pdf-payload.js");
		String apiClient = read("static/js/api/pdf-api-client.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertTrue(mainTemplate.contains("@request-thumbnails-pdf=\"requestPdfThumbnails\""),
						"サムネイル取得のイベントをpdf-app.jsへつないでください。"),
				() -> assertTrue(originalPdfForm.contains("<pdf-thumbnail-list"),
						"サムネイル一覧は編集元PDFカードへ置いてください。"),
				() -> assertTrue(thumbnailList.contains("this.$emit(\"request-thumbnails\")"),
						"サムネイル取得はコンポーネントからイベントで通知してください。"),
				() -> assertTrue(constants.contains("THUMBNAILS_PDF: \"/thumbnailsPdf\""),
						"サムネイルAPIのURLはconst.jsで持ってください。"),
				() -> assertTrue(payload.contains("const buildThumbnailPayload"),
						"サムネイルpayloadの生成はpdf-payload.jsへ置いてください。"),
				() -> assertTrue(apiClient.contains("const requestPdfThumbnails"),
						"サムネイルAPI呼び出しはpdf-api-client.jsへ置いてください。"),
				() -> assertTrue(apiClient.contains("thumbnailResponse: apiResult.data"),
						"成功レスポンスは共通ラッパー越しに読んでください。"),
				() -> assertTrue(pdfApp.contains("PdfApiClient.requestPdfThumbnails("),
						"サムネイル取得はapi client経由にしてください。"));
	}

	/**
	 * サムネイルの取得が利用者操作のときだけ、1リクエストで行われることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void thumbnailsAreFetchedOncePerUserAction() throws IOException {
		String thumbnailList = read("static/js/components/pdf-thumbnail-list.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String payload = read("static/js/api/pdf-payload.js");

		assertAll(
				// サーバー側に文書セッションが無く、取得のたびにPDF全体をアップロードするため、
				// mountedやwatchでの自動取得を禁止する。
				() -> assertFalse(thumbnailList.contains("mounted"), "サムネイルを自動取得しないでください。"),
				() -> assertFalse(thumbnailList.contains("watch:"), "サムネイルを自動取得しないでください。"),
				() -> assertEquals(1, countOccurrences(pdfApp, "PdfApiClient.requestPdfThumbnails("),
						"サムネイル取得の呼び出し箇所は1つにしてください。"),
				// ページ番号を送らないことで、ページごとに取得する形にならないよう固定する。
				() -> assertFalse(payload.contains("key: \"pageNumber\""),
						"サムネイルはページごとに取得しないでください。"));
	}

	/**
	 * サムネイル画像とページ選択の見せ方が、既存のFE規約に沿っていることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void thumbnailRenderingFollowsFrontendConventions() throws IOException {
		String thumbnailList = read("static/js/components/pdf-thumbnail-list.js");
		String styles = read("static/css/main.css");

		assertAll(
				// data URIはinnerHTMLではなくsrcバインディングで渡す。
				() -> assertTrue(thumbnailList.contains(":src=\"page.dataUri\""),
						"サムネイル画像はsrcバインディングで渡してください。"),
				() -> assertFalse(thumbnailList.contains("innerHTML"), "HTMLを直接挿入しないでください。"),
				() -> assertTrue(thumbnailList.contains("v-for=\"page in thumbnailState.pages\" :key=\"page.pageNumber\""),
						"サムネイルの繰り返しにはkeyを付けてください。"),
				() -> assertTrue(thumbnailList.contains("pdf-thumbnail-list__item--selected"),
						"選択状態はclassで示してください。"),
				() -> assertTrue(styles.contains(".pdf-thumbnail-list__item--selected {"),
						"選択状態の見た目はmain.cssへ置いてください。"));
	}

	/**
	 * 選択したページが既存の「ページ指定」入力欄へ反映されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void selectedPagesAreAppliedToExistingPageInput() throws IOException {
		String validator = read("static/js/validation/page-number-validator.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");

		assertAll(
				() -> assertTrue(validator.contains("const buildPagesText"),
						"ページ番号からページ指定表記への変換はpage-number-validator.jsへ置いてください。"),
				() -> assertTrue(pdfApp.contains("PageNumberValidator.buildPagesText("),
						"選択ページの表記変換はpage-number-validator.js経由にしてください。"),
				() -> assertTrue(pdfApp.contains("this.originalFile.delPagesText.text = pagesText;"),
						"選択ページを既存のページ指定入力欄へ反映してください。"),
				// 手入力の導線を消さない。既存操作を壊さないため入力欄は残す。
				() -> assertTrue(originalPdfForm.contains("v-model=\"originalFile.delPagesText.text\""),
						"ページ指定の手入力欄は残してください。"));
	}

	/**
	 * 指定文字列の出現回数を数える。
	 *
	 * @param source 検索対象
	 * @param token  検索する文字列
	 * @return 出現回数
	 */
	private int countOccurrences(String source, String token) {
		int count = 0;
		int index = source.indexOf(token);
		while (index >= 0) {
			count++;
			index = source.indexOf(token, index + token.length());
		}
		return count;
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
