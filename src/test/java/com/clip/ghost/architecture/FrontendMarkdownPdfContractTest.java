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
 * MarkdownからのPDF出力APIと既存Vue画面の最小接続を固定するテスト。
 * <p>
 * npmなしのWebJar Vue構成でも、ボタン、app、API client、ダウンロード処理の接続欠落をMavenテストで検知する。
 */
class FrontendMarkdownPdfContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * PDF出力操作が既存の責務別JavaScript境界を通ってAPIへ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void markdownPdfUiUsesExistingFrontendBoundaries() throws IOException {
		String mainTemplate = read("templates/main.html");
		String constants = read("static/js/const.js");
		String apiClient = read("static/js/api/markdown-api-client.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(() -> assertTrue(mainTemplate.contains("@click=\"requestMarkdownPdf\"")),
				() -> assertTrue(constants.contains("MARKDOWN_PDF: \"/markdownPdf\"")),
				() -> assertTrue(apiClient.contains("const requestMarkdownPdf")),
				// HTTPはfetch-client経由に限定する。API clientが自前でfetchしないことを固定する。
				// 既定のAcceptはapplication/jsonで、そのままではPDFを返すAPIが406になる。
				() -> assertTrue(apiClient.contains("FetchClient.postRequestForFile(")),
				() -> assertTrue(apiClient.contains("application/pdf")),
				() -> assertTrue(pdfApp.contains("MarkdownApiClient.requestMarkdownPdf(")),
				// 保存はfile-response-handlerへ寄せ、Object URLと<a>の扱いを1箇所に閉じる。
				() -> assertTrue(pdfApp.contains("FileResponseHandler.downloadBlob(")));
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
