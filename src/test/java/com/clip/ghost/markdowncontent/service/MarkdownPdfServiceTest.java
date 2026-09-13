package com.clip.ghost.markdowncontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.markdowncontent.config.MarkdownPdfProperties;
import com.clip.ghost.markdowncontent.dto.MarkdownPdfRequest;
import com.clip.ghost.markdowncontent.dto.PdfFromHtmlRequest;
import com.clip.ghost.markdowncontent.logic.EpubReader;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.markdowncontent.logic.MarkdownPdfRenderer;

/**
 * {@link MarkdownPdfService} のファイル名正規化と、Markdown / HTML からのPDFレスポンス組み立てを検証するテスト。
 */
class MarkdownPdfServiceTest {

	private final MarkdownPdfService service = new MarkdownPdfService(new MarkdownHtmlRenderer(),
			new MarkdownPdfRenderer(new MarkdownPdfProperties()), new EpubReader());

	@Test
	@DisplayName("Markdown本文をPDFのダウンロードレスポンスとして返す")
	void generatePdfReturnsDownloadResponse() throws IOException {
		ResponseEntity<Resource> result = service.generatePdf(createRequest("design-note.md", "# 設計書\n\n本文"));

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(MediaType.APPLICATION_PDF, result.getHeaders().getContentType());
		// 画面はContent-Dispositionのファイル名をそのまま保存名に使う。
		assertEquals("attachment; filename=\"design-note.pdf\"; filename*=UTF-8''design-note.pdf",
				result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
		assertTrue(extractText(result).contains("設計書"));
	}

	@Test
	@DisplayName("拡張子をPDFへそろえ、ファイル名未指定なら既定名にする")
	void generatePdfNormalizesFileName() {
		assertEquals("note.pdf", extractFileName(service.generatePdf(createRequest("note.md", "本文"))));
		assertEquals("note.pdf", extractFileName(service.generatePdf(createRequest("note", "本文"))));
		assertEquals("note.pdf", extractFileName(service.generatePdf(createRequest("  note.pdf  ", "本文"))));
		assertEquals("document.pdf", extractFileName(service.generatePdf(createRequest(null, "本文"))));
		assertEquals("document.pdf", extractFileName(service.generatePdf(createRequest("   ", "本文"))));
	}

	@Test
	@DisplayName("ファイル名からパス区切りと制御文字を取り除く")
	void generatePdfRemovesUnsafeFileNameCharacters() {
		// Content-Dispositionへそのまま出るため、パスを抜け出す表現や壊れた名前を残さない。
		assertEquals("passwd.pdf", extractFileName(service.generatePdf(createRequest("../../etc/passwd", "本文"))));
		assertEquals("a_b.pdf", extractFileName(service.generatePdf(createRequest("a:b.md", "本文"))));
	}

	@Test
	@DisplayName("本文が空でもPDFを返す")
	void generatePdfAcceptsEmptyContent() throws IOException {
		ResponseEntity<Resource> result = service.generatePdf(createRequest("empty.md", ""));

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());
		assertTrue(result.getBody().contentLength() > 0);
	}

	/**
	 * テスト用のPDF出力リクエストを生成する。
	 *
	 * @param fileName ファイル名
	 * @param content  Markdown本文
	 * @return PDF出力リクエスト
	 */
	private MarkdownPdfRequest createRequest(String fileName, String content) {
		MarkdownPdfRequest request = new MarkdownPdfRequest();
		request.setFileName(fileName);
		request.setContent(content);
		return request;
	}

	@Test
	@DisplayName("HTMLファイルをPDFのダウンロードレスポンスとして返す")
	void generatePdfFromHtmlReturnsDownloadResponse() throws IOException {
		ResponseEntity<Resource> result = service
				.generatePdfFromHtml(createHtmlRequest("design.html", "<h1>設計書</h1><p>本文</p>", null));

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(MediaType.APPLICATION_PDF, result.getHeaders().getContentType());
		assertEquals("design.pdf", extractFileName(result));
		assertTrue(extractText(result).contains("設計書"));
	}

	@Test
	@DisplayName("HTMLからのPDF出力ではfileName指定がHTMLファイル名より優先される")
	void generatePdfFromHtmlPrefersRequestedFileName() {
		ResponseEntity<Resource> result = service
				.generatePdfFromHtml(createHtmlRequest("design.html", "<p>本文</p>", "報告書.pdf"));

		assertEquals("報告書.pdf", extractFileName(result));
	}

	@Test
	@DisplayName("HTMLのscriptはPDFへ描画せず、本文だけを残す")
	void generatePdfFromHtmlRemovesScript() throws IOException {
		ResponseEntity<Resource> result = service.generatePdfFromHtml(
				createHtmlRequest("danger.html", "<script>alert(1)</script><p>安全な本文</p>", null));

		String text = extractText(result);
		assertTrue(text.contains("安全な本文"));
		assertFalse(text.contains("alert"));
	}

	@Test
	@DisplayName("HTMLからのPDF出力でもファイル名からパス区切りを取り除く")
	void generatePdfFromHtmlRemovesUnsafeFileNameCharacters() {
		ResponseEntity<Resource> result = service
				.generatePdfFromHtml(createHtmlRequest("../../etc/passwd", "<p>本文</p>", null));

		assertEquals("passwd.pdf", extractFileName(result));
	}

	/**
	 * HTMLからのPDF出力リクエストを生成する。
	 *
	 * @param htmlFileName アップロードHTMLのファイル名
	 * @param html         HTML本文
	 * @param fileName     ダウンロードファイル名。nullの場合は未指定
	 * @return HTMLからのPDF出力リクエスト
	 */
	private PdfFromHtmlRequest createHtmlRequest(String htmlFileName, String html, String fileName) {
		PdfFromHtmlRequest request = new PdfFromHtmlRequest();
		request.setHtmlFile(new MockMultipartFile("htmlFile", htmlFileName, MediaType.TEXT_HTML_VALUE,
				html.getBytes(StandardCharsets.UTF_8)));
		request.setFileName(fileName);
		return request;
	}

	/**
	 * レスポンスのContent-Dispositionからファイル名を取り出す。
	 *
	 * @param result PDFレスポンス
	 * @return ダウンロードファイル名
	 */
	private String extractFileName(ResponseEntity<Resource> result) {
		return result.getHeaders().getContentDisposition().getFilename();
	}

	/**
	 * PDFレスポンスからテキストを抽出する。
	 *
	 * @param result PDFレスポンス
	 * @return 抽出したテキスト
	 * @throws IOException PDFを読み込めない場合
	 */
	private String extractText(ResponseEntity<Resource> result) throws IOException {
		assertNotNull(result.getBody());
		try (PDDocument document = Loader.loadPDF(result.getBody().getContentAsByteArray())) {
			return new PDFTextStripper().getText(document);
		}
	}
}
