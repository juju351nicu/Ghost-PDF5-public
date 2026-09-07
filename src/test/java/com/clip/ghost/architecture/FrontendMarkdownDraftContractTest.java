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
