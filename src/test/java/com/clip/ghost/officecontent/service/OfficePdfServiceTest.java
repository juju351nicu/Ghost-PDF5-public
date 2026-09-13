package com.clip.ghost.officecontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.markdowncontent.config.MarkdownPdfProperties;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.markdowncontent.logic.MarkdownPdfRenderer;
import com.clip.ghost.officecontent.dto.OfficeMarkdownRequest;
import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.logic.OfficeMarkdownLogic;
import com.clip.ghost.officecontent.logic.PowerPointPdfLogic;

/**
 * {@link OfficePdfService} の形式ごとの変換経路とファイル名正規化を検証するテスト。
 */
class OfficePdfServiceTest {

	private final OfficePdfService officePdfService = new OfficePdfService(new OfficeMarkdownLogic(),
			new PowerPointPdfLogic(), new MarkdownHtmlRenderer(),
			new MarkdownPdfRenderer(new MarkdownPdfProperties()));

	@Test
	@DisplayName("WordはMarkdown経由でPDF化し、本文のテキストを選択できる形で残す")
	void generatePdfConvertsWordViaMarkdown() throws IOException {
		byte[] docx = createWord("設計書の本文です");

		ResponseEntity<Resource> result = officePdfService.generatePdf(createRequest("設計書.docx", docx));

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(MediaType.APPLICATION_PDF, result.getHeaders().getContentType());
		assertEquals("設計書.pdf", result.getHeaders().getContentDisposition().getFilename());
		// Markdown経由の変換ではPDFのテキストが残る。スライド画像経由との違いをここで固定する。
		assertTrue(extractText(result).contains("設計書の本文です"));
	}

	@Test
	@DisplayName("PowerPointはスライド画像経由でPDF化し、スライド数と同じページ数になる")
	void generatePdfConvertsPowerPointAsSlideImages() throws IOException {
		byte[] pptx = createPowerPoint(2);

		ResponseEntity<Resource> result = officePdfService.generatePdf(createRequest("資料.pptx", pptx));

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("資料.pdf", result.getHeaders().getContentDisposition().getFilename());
		assertEquals(2, countPages(result));
	}

	@Test
	@DisplayName("対応していない拡張子はOfficeInputExceptionになる")
	void generatePdfThrowsWhenExtensionIsNotSupported() {
		OfficeMarkdownRequest form = createRequest("設計書.doc", "old format".getBytes(StandardCharsets.UTF_8));

		OfficeInputException exception = assertThrows(OfficeInputException.class,
				() -> officePdfService.generatePdf(form));

		assertEquals("設計書.doc", exception.getFileName());
	}

	@Test
	@DisplayName("ファイル名からパス区切りを取り除いてPDF拡張子へそろえる")
	void generatePdfRemovesUnsafeFileNameCharacters() throws IOException {
		byte[] docx = createWord("本文");

		ResponseEntity<Resource> result = officePdfService
				.generatePdf(createRequest("../../etc/passwd.docx", docx));

		assertEquals("passwd.pdf", result.getHeaders().getContentDisposition().getFilename());
	}

	/**
	 * 1段落のWord文書を組み立ててbyte配列にする。
	 *
	 * @param text 段落のテキスト
	 * @return Word文書のbyte配列
	 * @throws IOException 文書の生成に失敗した場合
	 */
	private byte[] createWord(String text) throws IOException {
		try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			XWPFParagraph paragraph = document.createParagraph();
			XWPFRun run = paragraph.createRun();
			run.setText(text);
			document.write(out);
			return out.toByteArray();
		}
	}

	/**
	 * 指定枚数のスライドを持つプレゼンテーションを組み立ててbyte配列にする。
	 *
	 * @param slideCount スライド枚数
	 * @return プレゼンテーションのbyte配列
	 * @throws IOException プレゼンテーションの生成に失敗した場合
	 */
	private byte[] createPowerPoint(int slideCount) throws IOException {
		try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (int slideIndex = 0; slideIndex < slideCount; slideIndex++) {
				XSLFSlide slide = slideShow.createSlide();
				slide.createTextBox().setText("スライド" + (slideIndex + 1));
			}
			slideShow.write(out);
			return out.toByteArray();
		}
	}

	/**
	 * Office文書からのPDF出力リクエストを生成する。
	 *
	 * @param fileName ファイル名
	 * @param contents ファイル内容
	 * @return PDF出力リクエスト
	 */
	private OfficeMarkdownRequest createRequest(String fileName, byte[] contents) {
		OfficeMarkdownRequest form = new OfficeMarkdownRequest();
		form.setOfficeFile(new MockMultipartFile("officeFile", fileName, "application/octet-stream", contents));
		return form;
	}

	/**
	 * PDFレスポンスからテキストを抽出する。
	 *
	 * @param result PDFレスポンス
	 * @return 抽出したテキスト
	 * @throws IOException PDFを読み込めない場合
	 */
	private String extractText(ResponseEntity<Resource> result) throws IOException {
		try (PDDocument document = Loader.loadPDF(result.getBody().getContentAsByteArray())) {
			return new PDFTextStripper().getText(document);
		}
	}

	/**
	 * PDFレスポンスのページ数を数える。
	 *
	 * @param result PDFレスポンス
	 * @return ページ数
	 * @throws IOException PDFを読み込めない場合
	 */
	private int countPages(ResponseEntity<Resource> result) throws IOException {
		try (PDDocument document = Loader.loadPDF(result.getBody().getContentAsByteArray())) {
			return document.getNumberOfPages();
		}
	}
}
