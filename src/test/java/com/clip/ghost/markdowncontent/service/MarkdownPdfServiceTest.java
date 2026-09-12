package com.clip.ghost.markdowncontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

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

import com.clip.ghost.markdowncontent.config.MarkdownPdfProperties;
import com.clip.ghost.markdowncontent.dto.MarkdownPdfRequest;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.markdowncontent.logic.MarkdownPdfRenderer;

/**
 * {@link MarkdownPdfService} のファイル名正規化とPDFレスポンス組み立てを検証するテスト。
 */
class MarkdownPdfServiceTest {

	private final MarkdownPdfService service = new MarkdownPdfService(new MarkdownHtmlRenderer(),
			new MarkdownPdfRenderer(new MarkdownPdfProperties()));

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
