package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.imagecontent.exception.OcrUnavailableException;
import com.clip.ghost.imagecontent.logic.ImageConverterResolver;
import com.clip.ghost.imagecontent.logic.ImageToMarkdownConverter;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

/**
 * {@link PdfMarkdownDraftService} のページ番号付与、テキスト正規化、Markdown生成、AUTO時の画像変換を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfMarkdownDraftServiceTest {

	private static final int OCR_RENDER_DPI = 200;

	@InjectMocks
	private PdfMarkdownDraftService service;

	@Mock
	private GhostPdfLogic pdfLogic;

	@Mock
	private ImageConverterResolver converterResolver;

	@Mock
	private ImageToMarkdownConverter converter;

	@Test
	@DisplayName("PDF情報とページ順を維持したMarkdown下書きレスポンスを返す")
	void generateMarkdownDraftReturnsMetadataAndPagesInPdfOrder() {
		Path inputPath = Path.of("temporary", "sample.pdf");
		MockMultipartFile originalFile = createPdfFile("sample.pdf", new byte[] { 1, 2, 3 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of("first page", "second page")).when(pdfLogic).extractPdfPageTexts(inputPath);

		ResponseEntity<PdfMarkdownDraftResponse> result = service.generateMarkdownDraft(form);

		PdfMarkdownDraftResponse response = result.getBody();
		assertNotNull(response);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("sample.pdf", response.getFileName());
		assertEquals(originalFile.getSize(), response.getFileSize());
		assertEquals(2, response.getPageCount());
		assertPage(response.getPages().get(0), 1, "first page");
		assertPage(response.getPages().get(1), 2, "second page");
		assertEquals("## Page 1\n\nfirst page\n\n## Page 2\n\nsecond page", response.getMarkdown());

		InOrder orderedCalls = inOrder(pdfLogic);
		orderedCalls.verify(pdfLogic).loadPdf(originalFile);
		orderedCalls.verify(pdfLogic).extractPdfPageTexts(inputPath);
	}

	@Test
	@DisplayName("改行と末尾空白を正規化し、空ページの見出しを残す")
	void generateMarkdownDraftNormalizesTextAndKeepsEmptyPageHeading() {
		Path inputPath = Path.of("temporary", "line-endings.pdf");
		MockMultipartFile originalFile = createPdfFile("line-endings.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of("first\r\nline  \r\n", " \t\r\n", "third\rline\t  ")).when(pdfLogic)
				.extractPdfPageTexts(inputPath);

		PdfMarkdownDraftResponse response = service.generateMarkdownDraft(form).getBody();

		assertNotNull(response);
		assertPage(response.getPages().get(0), 1, "first\nline");
		assertPage(response.getPages().get(1), 2, "");
		assertPage(response.getPages().get(2), 3, "third\nline");
		assertEquals("## Page 1\n\nfirst\nline\n\n## Page 2\n\n## Page 3\n\nthird\nline", response.getMarkdown());
		assertFalse(response.getMarkdown().endsWith("\n"));
	}

	@Test
	@DisplayName("AUTOは文字が無いページを画像変換で補完し、sourceを付ける")
	void generateMarkdownDraftAutoConvertsBlankPages() {
		Path inputPath = Path.of("temporary", "scan.pdf");
		MockMultipartFile originalFile = createPdfFile("scan.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode("AUTO");
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of(new PdfPageContent(1, "text page", null), new PdfPageContent(2, "", new byte[] { 9, 9 })))
				.when(pdfLogic).extractPdfPageContents(inputPath, OCR_RENDER_DPI);
		doReturn("scanned\r\nmarkdown  ").when(converter).convert(any(byte[].class), eq("image/png"));

		PdfMarkdownDraftResponse response = service.generateMarkdownDraft(form).getBody();

		assertNotNull(response);
		assertEquals(2, response.getPageCount());
		assertPageWithSource(response.getPages().get(0), 1, "text page", "TEXT");
		assertPageWithSource(response.getPages().get(1), 2, "scanned\nmarkdown", "OCR");
		assertEquals("## Page 1\n\ntext page\n\n## Page 2\n\nscanned\nmarkdown", response.getMarkdown());
	}

	@Test
	@DisplayName("AUTOで画像変換が無効なら503相当で止め、PDFを読み込まない")
	void generateMarkdownDraftAutoThrowsWhenConverterDisabled() {
		MockMultipartFile originalFile = createPdfFile("scan.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode("AUTO");
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(false);

		assertThrows(OcrUnavailableException.class, () -> service.generateMarkdownDraft(form));

		verify(pdfLogic, never()).loadPdf(any());
	}

	/**
	 * テスト用のPDFアップロードファイルを生成する。
	 *
	 * @param fileName アップロード時のファイル名
	 * @param contents ファイル内容
	 * @return PDF multipartファイル
	 */
	private MockMultipartFile createPdfFile(String fileName, byte[] contents) {
		return new MockMultipartFile("originalFile", fileName, MediaType.APPLICATION_PDF_VALUE, contents);
	}

	/**
	 * テスト用のMarkdown下書きリクエストを生成する。
	 *
	 * @param originalFile 生成元PDF
	 * @return Markdown下書きリクエスト
	 */
	private PdfMarkdownDraftRequest createRequest(MockMultipartFile originalFile) {
		PdfMarkdownDraftRequest request = new PdfMarkdownDraftRequest();
		request.setOriginalFile(originalFile);
		return request;
	}

	/**
	 * 1ページ分の番号と本文を検証する。
	 *
	 * @param page               検証対象ページ
	 * @param expectedPageNumber 期待するページ番号
	 * @param expectedText       期待するページ本文
	 */
	private void assertPage(PdfMarkdownDraftPageResponse page, int expectedPageNumber, String expectedText) {
		assertEquals(expectedPageNumber, page.getPageNumber());
		assertEquals(expectedText, page.getText());
	}

	/**
	 * 1ページ分の番号、本文、取得元を検証する。
	 *
	 * @param page               検証対象ページ
	 * @param expectedPageNumber 期待するページ番号
	 * @param expectedText       期待するページ本文
	 * @param expectedSource     期待する取得元
	 */
	private void assertPageWithSource(PdfMarkdownDraftPageResponse page, int expectedPageNumber, String expectedText,
			String expectedSource) {
		assertPage(page, expectedPageNumber, expectedText);
		assertEquals(expectedSource, page.getSource());
	}
}
