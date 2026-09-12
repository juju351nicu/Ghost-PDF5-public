package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ページ単位Markdown下書きAPIと既存Vue画面の最小接続を固定するテスト。
 * <p>
 * npmなしのWebJar Vue構成でも、component、app、payload、API clientの接続欠落をMavenテストで検知する。
 */
class FrontendMarkdownDraftContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * Markdown下書き操作が既存の責務別JavaScript境界を通ってAPIへ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("Markdown下書き操作が責務別JavaScript境界を通ってAPIへ接続される")
	void pageMarkdownDraftUiUsesExistingFrontendBoundaries() throws IOException {
		String mainTemplate = read("templates/main.html");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String constants = read("static/js/const.js");
		String payload = read("static/js/api/pdf-payload.js");
		String apiClient = read("static/js/api/pdf-api-client.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertTrue(mainTemplate.contains("@request-markdown-draft-pdf=\"requestMarkdownDraftPdf\"")),
				() -> assertTrue(originalPdfForm.contains("this.$emit(\"request-markdown-draft-pdf\")")),
				() -> assertTrue(constants.contains("MARKDOWN_DRAFT_PDF: \"/markdownDraftPdf\"")),
				() -> assertTrue(payload.contains("const buildMarkdownDraftPayload")),
				() -> assertTrue(apiClient.contains("const requestPdfMarkdownDraft")),
				() -> assertTrue(pdfApp.contains("PdfApiClient.requestPdfMarkdownDraft(")),
				() -> assertTrue(pdfApp.contains("this.markdownContent = draftResponse.markdown || \"\";")),
				// 成功レスポンスは共通ラッパー越しに読む。ラッパーの解釈はapi-result-utils.jsへ閉じる。
				() -> assertTrue(apiClient.contains("ApiResultUtils.readApiResult(response)")),
				() -> assertTrue(apiClient.contains("markdownDraftResponse: apiResult.data")));
	}

	/**
	 * 変換モードの選択が、payload生成とAPI呼び出しまで接続されていることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("変換モードの選択がpayload生成まで接続される")
	void markdownDraftModeSelectionReachesPayload() throws IOException {
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String formState = read("static/js/models/pdf-form-state.js");
		String payload = read("static/js/api/pdf-payload.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				// 既定は従来動作。FEがmodeを送っていなかった時点の挙動を変えない。
				() -> assertTrue(formState.contains("markdownDraftMode: \"\"")),
				() -> assertTrue(formState.contains("const createMarkdownDraftModeItems")),
				() -> assertTrue(formState.contains("id: \"VISION\"")),
				() -> assertTrue(originalPdfForm.contains("v-model=\"originalFile.markdownDraftMode\"")),
				() -> assertTrue(originalPdfForm.contains("aria-label=\"Markdown下書きの変換モード\"")),
				// 費用の注意はモーダルで止めず、選択の近くへインラインで出す。
				() -> assertTrue(originalPdfForm.contains("v-if=\"isVisionModeSelected\"")),
				() -> assertTrue(originalPdfForm.contains("ページ数分の費用が発生します。")),
				() -> assertTrue(payload.contains("const buildMarkdownDraftPayload = (fileObject, mode)")),
				() -> assertTrue(payload.contains("payload.push({ key: \"mode\", value: mode })")),
				() -> assertTrue(pdfApp.contains("originalFileData.markdownDraftMode")));
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
