package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import com.clip.ghost.pdfcontent.config.PdfOcrProperties;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;
import com.clip.ghost.pdfcontent.logic.PdfPageImageConverter;

/**
 * {@link PdfMarkdownDraftService} のページ番号付与、テキスト正規化、Markdown生成、AUTO時の画像変換を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfMarkdownDraftServiceTest {

	private static final int OCR_RENDER_DPI = 200;
	private static final int OCR_MAX_PAGES = 20;

	@InjectMocks
	private PdfMarkdownDraftService service;

	@Mock
	private GhostPdfLogic pdfLogic;

	@Mock
	private ImageConverterResolver converterResolver;

	@Mock
	private ImageToMarkdownConverter converter;

	@Mock
	private PdfOcrProperties properties;

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
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of(new PdfPageContent(1, "text page", null),
				new PdfPageContent(2, "", "scanned\r\nmarkdown  "))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES), any());

		PdfMarkdownDraftResponse response = service.generateMarkdownDraft(form).getBody();

		assertNotNull(response);
		assertEquals(2, response.getPageCount());
		assertPageWithSource(response.getPages().get(0), 1, "text page", "TEXT");
		assertPageWithSource(response.getPages().get(1), 2, "scanned\nmarkdown", "OCR");
		assertEquals("## Page 1\n\ntext page\n\n## Page 2\n\nscanned\nmarkdown", response.getMarkdown());
	}

	@Test
	@DisplayName("AUTOは設定のDPIとページ上限をLogicへ渡し、変換自体は共有の画像変換器へ委譲する")
	void generateMarkdownDraftAutoPassesSettingsAndDelegatesConversion() {
		Path inputPath = Path.of("temporary", "settings.pdf");
		MockMultipartFile originalFile = createPdfFile("settings.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode("AUTO");
		byte[] pngBytes = new byte[] { 9, 9 };
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(150);
		when(properties.getMaxPages()).thenReturn(5);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn("converted markdown").when(converter).convert(pngBytes, "image/png");
		ArgumentCaptor<PdfPageImageConverter> pageImageConverterCaptor = ArgumentCaptor
				.forClass(PdfPageImageConverter.class);
		doReturn(List.of(new PdfPageContent(1, "text page", null))).when(pdfLogic).extractPdfPageContents(eq(inputPath),
				eq(150), eq(5), pageImageConverterCaptor.capture());

		service.generateMarkdownDraft(form);

		// Logicへ渡すlambdaが、共有の画像変換器をimage/pngで呼ぶことを確認する。
		assertEquals("converted markdown", pageImageConverterCaptor.getValue().convert(pngBytes));
		verify(converter).convert(pngBytes, "image/png");
	}

	@Test
	@DisplayName("AUTOでページ上限を超えた場合はLogicの例外をそのまま伝播する")
	void generateMarkdownDraftAutoPropagatesPageLimitException() {
		Path inputPath = Path.of("temporary", "limit.pdf");
		MockMultipartFile originalFile = createPdfFile("limit.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode("AUTO");
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(1);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doThrow(new PdfPageLimitExceededException(2, 1)).when(pdfLogic).extractPdfPageContents(eq(inputPath),
				eq(OCR_RENDER_DPI), eq(1), any());

		assertThrows(PdfPageLimitExceededException.class, () -> service.generateMarkdownDraft(form));

		verify(converter, never()).convert(any(byte[].class), any(String.class));
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
