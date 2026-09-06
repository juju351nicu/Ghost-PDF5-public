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
 * 画像Markdown下書きAPIと既存Vue画面の最小接続を固定するテスト。
 * <p>
 * npmなしのWebJar Vue構成でも、component、app、payload、API clientの接続欠落をMavenテストで検知する。
 */
class FrontendImageMarkdownDraftContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * 画像OCR操作が既存の責務別JavaScript境界を通ってAPIへ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void imageMarkdownDraftUiUsesExistingFrontendBoundaries() throws IOException {
		String mainTemplate = read("templates/main.html");
		String imageOcrForm = read("static/js/components/image-ocr-form.js");
		String constants = read("static/js/const.js");
		String payload = read("static/js/api/image-payload.js");
		String apiClient = read("static/js/api/image-api-client.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(() -> assertTrue(mainTemplate.contains("<image-ocr-form")),
				() -> assertTrue(mainTemplate.contains("@request-image-draft=\"requestImageDraft\"")),
				() -> assertTrue(mainTemplate.contains("@image-selected=\"handleImageSelected\"")),
				() -> assertTrue(imageOcrForm.contains("this.$emit(\"request-image-draft\")")),
				() -> assertTrue(imageOcrForm.contains("this.$emit(\"image-selected\", file)")),
				() -> assertTrue(constants.contains("MARKDOWN_DRAFT_IMAGE: \"/markdownDraftImage\"")),
				() -> assertTrue(payload.contains("const buildImageDraftPayload")),
				() -> assertTrue(apiClient.contains("const requestImageMarkdownDraft")),
				() -> assertTrue(pdfApp.contains("ImageApiClient.requestImageMarkdownDraft(")),
				() -> assertTrue(pdfApp.contains("this.markdownContent = draftResponse.markdown || \"\";")));
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
