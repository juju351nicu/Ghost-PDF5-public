package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.markdowncontent.logic.EpubReader;
import com.clip.ghost.markdowncontent.logic.EpubWriter;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;

/**
 * {@link PdfHtmlService} のHTML組み立てとレスポンス整形を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfHtmlServiceTest {

	@Mock
	private PdfMarkdownDraftService markdownDraftService;

	private PdfHtmlService htmlService;

	@BeforeEach
	void setup() {
		// HTMLレンダラーは実物を使う。ここで検証したいのは「画面プレビューと同じ変換結果を包むか」であり、
		// mockにすると変換規則の共有が壊れても気付けない。
		htmlService = new PdfHtmlService(markdownDraftService, new MarkdownHtmlRenderer(), new EpubWriter());
	}

	@Test
	@DisplayName("Markdown下書きをHTMLへ変換し、UTF-8のHTML文書として返す")
	void generateHtmlWrapsRenderedMarkdownInHtmlDocument() throws IOException {
		stubMarkdownDraft("## Page 1\n\n設計メモ本文");

		String html = readBody(htmlService.generateHtml(createRequest("設計書.pdf")));

		assertTrue(html.contains("<!DOCTYPE html>"));
		assertTrue(html.contains("<meta charset=\"utf-8\">"));
		assertTrue(html.contains("<h2>Page 1</h2>"));
		assertTrue(html.contains("設計メモ本文"));
	}

	@Test
	@DisplayName("GFMの表はプレビューと同じく表として描画する")
	void generateHtmlRendersGfmTable() throws IOException {
		stubMarkdownDraft("| 項目 | 値 |\n| --- | --- |\n| A | 1 |");

		String html = readBody(htmlService.generateHtml(createRequest("表.pdf")));

		assertTrue(html.contains("<table>"));
		assertTrue(html.contains("<th>項目</th>"));
	}

	@Test
	@DisplayName("Markdownに埋め込まれたscriptは出力HTMLへ残さない")
	void generateHtmlRemovesScriptFromMarkdown() throws IOException {
		stubMarkdownDraft("<script>alert(1)</script>\n\n本文");

		String html = readBody(htmlService.generateHtml(createRequest("危険.pdf")));

		assertFalse(html.contains("<script"));
		assertTrue(html.contains("本文"));
	}

	@Test
	@DisplayName("ダウンロードファイル名は元PDF名の拡張子をhtmlへ置き換える")
	void generateHtmlUsesOriginalFileNameWithHtmlExtension() {
		stubMarkdownDraft("本文");

		ResponseEntity<Resource> response = htmlService.generateHtml(createRequest("設計書.pdf"));

		assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
		assertTrue(response.getHeaders().getContentDisposition().getFilename().endsWith(".html"));
	}

	@Test
	@DisplayName("ファイル名が空の場合でも既定名でHTMLを返す")
	void generateHtmlUsesDefaultNameWhenFileNameIsBlank() {
		stubMarkdownDraft("本文");

		ResponseEntity<Resource> response = htmlService.generateHtml(createRequest(""));

		assertEquals("document.html", response.getHeaders().getContentDisposition().getFilename());
	}

	@Test
	@DisplayName("Markdown下書きをEPUBへ変換し、読み戻せる本文を持つEPUBを返す")
	void generateEpubWrapsRenderedMarkdownInEpub() throws IOException {
		stubMarkdownDraft("## Page 1\n\n設計メモ本文");

		ResponseEntity<Resource> response = htmlService.generateEpub(createRequest("設計書.pdf"));

		assertEquals("設計書.epub", response.getHeaders().getContentDisposition().getFilename());
		String bodyHtml = new EpubReader().readBodyHtml(readBodyBytes(response));
		assertTrue(bodyHtml.contains("Page 1"));
		assertTrue(bodyHtml.contains("設計メモ本文"));
	}

	@Test
	@DisplayName("EPUB出力はHTML出力と同じMarkdown変換結果を使う")
	void generateEpubUsesSameConversionAsHtml() throws IOException {
		stubMarkdownDraft("| 項目 | 値 |\n| --- | --- |\n| A | 1 |");

		String html = readBody(htmlService.generateHtml(createRequest("表.pdf")));
		String epubBody = new EpubReader()
				.readBodyHtml(readBodyBytes(htmlService.generateEpub(createRequest("表.pdf"))));

		// どちらも表として描画される。EPUB専用の変換を持たないことをここで固定する。
		assertTrue(html.contains("<th>項目</th>"));
		assertTrue(epubBody.contains("項目"));
		assertTrue(epubBody.contains("<table"));
	}

	/**
	 * レスポンス本文をbyte配列として読み出す。
	 *
	 * @param response バイナリレスポンス
	 * @return レスポンス本文
	 * @throws IOException 本文の読み込みに失敗した場合
	 */
	private byte[] readBodyBytes(ResponseEntity<Resource> response) throws IOException {
		return response.getBody().getContentAsByteArray();
	}

	/**
	 * Markdown下書きServiceが指定のMarkdownを返すようにstubする。
	 *
	 * @param markdown 返却するMarkdown本文
	 */
	private void stubMarkdownDraft(String markdown) {
		PdfMarkdownDraftResponse draft = new PdfMarkdownDraftResponse();
		draft.setMarkdown(markdown);
		doReturn(ResponseEntity.ok(ApiResult.of(draft))).when(markdownDraftService).generateMarkdownDraft(any());
	}

	/**
	 * HTML変換リクエストを生成する。
	 *
	 * @param fileName アップロードPDFのファイル名
	 * @return HTML変換リクエスト
	 */
	private PdfMarkdownDraftRequest createRequest(String fileName) {
		PdfMarkdownDraftRequest form = new PdfMarkdownDraftRequest();
		form.setOriginalFile(new MockMultipartFile("originalFile", fileName, "application/pdf",
				"pdf".getBytes(StandardCharsets.UTF_8)));
		return form;
	}

	/**
	 * レスポンス本文をUTF-8の文字列として読み出す。
	 *
	 * @param response HTMLレスポンス
	 * @return HTML本文
	 * @throws IOException 本文の読み込みに失敗した場合
	 */
	private String readBody(ResponseEntity<Resource> response) throws IOException {
		return new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}
}
