package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.clip.ghost.imagecontent.exception.ImageProcessingException;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;
import com.clip.ghost.imagecontent.logic.ImageConverterResolver;
import com.clip.ghost.imagecontent.logic.ImageToMarkdownConverter;
import com.clip.ghost.common.response.ApiMessage;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.response.ApiResultType;
import com.clip.ghost.pdfcontent.config.PdfOcrProperties;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.enums.PdfMarkdownDraftMode;
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
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of("first page", "second page")).when(pdfLogic).extractPdfPageTexts(inputPath);

		ResponseEntity<ApiResult<PdfMarkdownDraftResponse>> result = service.generateMarkdownDraft(form);

		assertNotNull(result.getBody());
		assertEquals(ApiResultType.INFO, result.getBody().getResultType());
		assertTrue(result.getBody().getMessageList().isEmpty());
		PdfMarkdownDraftResponse response = result.getBody().getData();
		assertNotNull(response);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("sample.pdf", response.getFileName());
		assertEquals(originalFile.getSize(), response.getFileSize());
		assertEquals(2, response.getPageCount());
		assertPage(response.getPages().get(0), 1, "first page");
		assertPage(response.getPages().get(1), 2, "second page");
		assertEquals("## Page 1\n\nfirst page\n\n## Page 2\n\nsecond page", response.getMarkdown());

		InOrder orderedCalls = inOrder(pdfLogic);
		orderedCalls.verify(pdfLogic).loadPdf(originalFile, null);
		orderedCalls.verify(pdfLogic).extractPdfPageTexts(inputPath);
	}

	@Test
	@DisplayName("改行と末尾空白を正規化し、空ページの見出しを残す")
	void generateMarkdownDraftNormalizesTextAndKeepsEmptyPageHeading() {
		Path inputPath = Path.of("temporary", "line-endings.pdf");
		MockMultipartFile originalFile = createPdfFile("line-endings.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of("first\r\nline  \r\n", " \t\r\n", "third\rline\t  ")).when(pdfLogic)
				.extractPdfPageTexts(inputPath);

		PdfMarkdownDraftResponse response = extractData(service.generateMarkdownDraft(form));

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
		form.setMode(PdfMarkdownDraftMode.AUTO);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of(new PdfPageContent(1, "text page", null, false),
				new PdfPageContent(2, "", "scanned\r\nmarkdown  ", false))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES),
						eq(PdfMarkdownDraftMode.AUTO), any());

		PdfMarkdownDraftResponse response = extractData(service.generateMarkdownDraft(form));

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
		form.setMode(PdfMarkdownDraftMode.AUTO);
		byte[] pngBytes = new byte[] { 9, 9 };
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(150);
		when(properties.getMaxPages()).thenReturn(5);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn("converted markdown").when(converter).convert(pngBytes, "image/png");
		ArgumentCaptor<PdfPageImageConverter> pageImageConverterCaptor = ArgumentCaptor
				.forClass(PdfPageImageConverter.class);
		doReturn(List.of(new PdfPageContent(1, "text page", null, false))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(150), eq(5), eq(PdfMarkdownDraftMode.AUTO),
						pageImageConverterCaptor.capture());

		service.generateMarkdownDraft(form);

		// Logicへ渡すlambdaが、共有の画像変換器をimage/pngで呼ぶことを確認する。
		assertEquals("converted markdown", pageImageConverterCaptor.getValue().convert(pngBytes));
		verify(converter).convert(pngBytes, "image/png");
	}

	@Test
	@DisplayName("AUTOで一部ページの変換が失敗した場合はWARNINGで成功分を返す")
	void generateMarkdownDraftAutoReturnsWarningWhenSomePagesFail() {
		Path inputPath = Path.of("temporary", "partial.pdf");
		MockMultipartFile originalFile = createPdfFile("partial.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.AUTO);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of(new PdfPageContent(1, "", "scanned markdown", false), new PdfPageContent(2, "", null, true),
				new PdfPageContent(3, "text page", null, false))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES),
						eq(PdfMarkdownDraftMode.AUTO), any());

		ResponseEntity<ApiResult<PdfMarkdownDraftResponse>> result = service.generateMarkdownDraft(form);

		assertNotNull(result.getBody());
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(ApiResultType.WARNING, result.getBody().getResultType());
		assertEquals(1, result.getBody().getMessageList().size());
		ApiMessage message = result.getBody().getMessageList().get(0);
		assertEquals("ocrPagePartiallyFailed", message.code());
		assertEquals("1ページの文字起こしに失敗しました。（失敗したページ: 2）", message.message());
		PdfMarkdownDraftResponse response = result.getBody().getData();
		assertNotNull(response);
		// 失敗ページを空本文のFAILEDにしても、成功したページの本文はそのまま返す。
		assertPageWithSource(response.getPages().get(0), 1, "scanned markdown", "OCR");
		assertPageWithSource(response.getPages().get(1), 2, "", "FAILED");
		assertPageWithSource(response.getPages().get(2), 3, "text page", "TEXT");
		assertEquals(3, response.getPageCount());
		assertEquals("## Page 1\n\nscanned markdown\n\n## Page 2\n\n## Page 3\n\ntext page", response.getMarkdown());
	}

	@Test
	@DisplayName("AUTOで複数ページの変換が失敗した場合は件数と全ページ番号を伝える")
	void generateMarkdownDraftAutoReportsEveryFailedPageNumber() {
		Path inputPath = Path.of("temporary", "partial-multi.pdf");
		MockMultipartFile originalFile = createPdfFile("partial-multi.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.AUTO);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of(new PdfPageContent(1, "", null, true), new PdfPageContent(2, "", "scanned", false),
				new PdfPageContent(3, "", null, true))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES),
						eq(PdfMarkdownDraftMode.AUTO), any());

		ResponseEntity<ApiResult<PdfMarkdownDraftResponse>> result = service.generateMarkdownDraft(form);

		assertNotNull(result.getBody());
		assertEquals(ApiResultType.WARNING, result.getBody().getResultType());
		assertEquals("2ページの文字起こしに失敗しました。（失敗したページ: 1, 3）",
				result.getBody().getMessageList().get(0).message());
	}

	@Test
	@DisplayName("AUTOで全ページ成功した場合はINFOとメッセージ空を保つ")
	void generateMarkdownDraftAutoKeepsInfoWhenEveryPageSucceeds() {
		Path inputPath = Path.of("temporary", "success.pdf");
		MockMultipartFile originalFile = createPdfFile("success.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.AUTO);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of(new PdfPageContent(1, "", "scanned markdown", false),
				new PdfPageContent(2, "text page", null, false))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES),
						eq(PdfMarkdownDraftMode.AUTO), any());

		ResponseEntity<ApiResult<PdfMarkdownDraftResponse>> result = service.generateMarkdownDraft(form);

		assertNotNull(result.getBody());
		assertEquals(ApiResultType.INFO, result.getBody().getResultType());
		assertTrue(result.getBody().getMessageList().isEmpty());
	}

	@Test
	@DisplayName("AUTOで全ページの変換が失敗した場合はLogicの例外をそのまま伝播し、200にしない")
	void generateMarkdownDraftAutoPropagatesExceptionWhenEveryPageFails() {
		Path inputPath = Path.of("temporary", "all-failed.pdf");
		MockMultipartFile originalFile = createPdfFile("all-failed.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.AUTO);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		// 全滅は部分的成功ではないため、Logicが投げた失敗をServiceで200へ丸めない。
		doThrow(new ImageProcessingException("変換に失敗しました。")).when(pdfLogic).extractPdfPageContents(eq(inputPath),
				eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES), eq(PdfMarkdownDraftMode.AUTO), any());

		assertThrows(ImageProcessingException.class, () -> service.generateMarkdownDraft(form));
	}

	@Test
	@DisplayName("AUTOでページ上限を超えた場合はLogicの例外をそのまま伝播する")
	void generateMarkdownDraftAutoPropagatesPageLimitException() {
		Path inputPath = Path.of("temporary", "limit.pdf");
		MockMultipartFile originalFile = createPdfFile("limit.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.AUTO);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(1);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doThrow(new PdfPageLimitExceededException(2, 1)).when(pdfLogic).extractPdfPageContents(eq(inputPath),
				eq(OCR_RENDER_DPI), eq(1), eq(PdfMarkdownDraftMode.AUTO), any());

		assertThrows(PdfPageLimitExceededException.class, () -> service.generateMarkdownDraft(form));

		verify(converter, never()).convert(any(byte[].class), any(String.class));
	}

	@Test
	@DisplayName("AUTOで画像変換が無効なら503相当で止め、PDFを読み込まない")
	void generateMarkdownDraftAutoThrowsWhenConverterDisabled() {
		MockMultipartFile originalFile = createPdfFile("scan.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.AUTO);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(false);

		assertThrows(OcrUnavailableException.class, () -> service.generateMarkdownDraft(form));

		verify(pdfLogic, never()).loadPdf(any(), any());
	}

	@Test
	@DisplayName("VISIONは文字レイヤーのあるページも含め全ページを変換器へ渡す")
	void generateMarkdownDraftVisionConvertsEveryPage() {
		Path inputPath = Path.of("temporary", "vision.pdf");
		MockMultipartFile originalFile = createPdfFile("vision.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.VISION);
		stubEnabledConverter(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of(new PdfPageContent(1, "text page", "| 列1 | 列2 |", false),
				new PdfPageContent(2, "", "scanned\r\nmarkdown  ", false))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES),
						eq(PdfMarkdownDraftMode.VISION), any());

		PdfMarkdownDraftResponse response = extractData(service.generateMarkdownDraft(form));

		// VISIONの中核。文字レイヤーを持つページも変換結果を採用しないと、表がフラットなテキストのままになる。
		assertNotNull(response);
		assertPageWithSource(response.getPages().get(0), 1, "| 列1 | 列2 |", "OCR");
		assertPageWithSource(response.getPages().get(1), 2, "scanned\nmarkdown", "OCR");
		verify(pdfLogic).extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES),
				eq(PdfMarkdownDraftMode.VISION), any());
	}

	@Test
	@DisplayName("VISIONで一部ページの変換が失敗した場合はWARNINGで成功分を返す")
	void generateMarkdownDraftVisionReturnsWarningWhenSomePagesFail() {
		Path inputPath = Path.of("temporary", "vision-partial.pdf");
		MockMultipartFile originalFile = createPdfFile("vision-partial.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.VISION);
		stubEnabledConverter(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doReturn(List.of(new PdfPageContent(1, "text page", "converted markdown", false),
				new PdfPageContent(2, "second page", null, true))).when(pdfLogic)
				.extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES),
						eq(PdfMarkdownDraftMode.VISION), any());

		ResponseEntity<ApiResult<PdfMarkdownDraftResponse>> result = service.generateMarkdownDraft(form);

		assertNotNull(result.getBody());
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(ApiResultType.WARNING, result.getBody().getResultType());
		assertEquals("1ページの文字起こしに失敗しました。（失敗したページ: 2）",
				result.getBody().getMessageList().get(0).message());
		PdfMarkdownDraftResponse response = result.getBody().getData();
		assertNotNull(response);
		assertPageWithSource(response.getPages().get(0), 1, "converted markdown", "OCR");
		// 失敗ページは文字レイヤーを持っていても、変換できなかったことが分かるようFAILEDにする。
		assertPageWithSource(response.getPages().get(1), 2, "", "FAILED");
	}

	@Test
	@DisplayName("VISIONで全ページの変換が失敗した場合はLogicの例外をそのまま伝播し、200にしない")
	void generateMarkdownDraftVisionPropagatesExceptionWhenEveryPageFails() {
		Path inputPath = Path.of("temporary", "vision-all-failed.pdf");
		MockMultipartFile originalFile = createPdfFile("vision-all-failed.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.VISION);
		stubEnabledConverter(OCR_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doThrow(new ImageProcessingException("変換に失敗しました。")).when(pdfLogic).extractPdfPageContents(eq(inputPath),
				eq(OCR_RENDER_DPI), eq(OCR_MAX_PAGES), eq(PdfMarkdownDraftMode.VISION), any());

		assertThrows(ImageProcessingException.class, () -> service.generateMarkdownDraft(form));
	}

	@Test
	@DisplayName("VISIONで総ページ数が上限を超えた場合はLogicの例外を伝播し、変換器を呼ばない")
	void generateMarkdownDraftVisionPropagatesPageLimitException() {
		Path inputPath = Path.of("temporary", "vision-limit.pdf");
		MockMultipartFile originalFile = createPdfFile("vision-limit.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		form.setMode(PdfMarkdownDraftMode.VISION);
		stubEnabledConverter(1);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile, null);
		doThrow(new PdfPageLimitExceededException(3, 1, PdfMarkdownDraftMode.VISION.describeConversionTarget()))
				.when(pdfLogic).extractPdfPageContents(eq(inputPath), eq(OCR_RENDER_DPI), eq(1),
						eq(PdfMarkdownDraftMode.VISION), any());

		assertThrows(PdfPageLimitExceededException.class, () -> service.generateMarkdownDraft(form));

		verify(converter, never()).convert(any(byte[].class), any(String.class));
	}

	/**
	 * 有効な画像変換器と、DPI・ページ上限の設定値をmockへ用意する。
	 *
	 * @param maxPages 画像変換にかけるページ数の上限
	 */
	private void stubEnabledConverter(int maxPages) {
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(OCR_RENDER_DPI);
		when(properties.getMaxPages()).thenReturn(maxPages);
	}

	/**
	 * 共通ラッパーからレスポンスデータを取り出す。
	 *
	 * @param result Markdown下書きレスポンス
	 * @return ラッパー内のレスポンスデータ
	 */
	private PdfMarkdownDraftResponse extractData(ResponseEntity<ApiResult<PdfMarkdownDraftResponse>> result) {
		assertNotNull(result.getBody());
		return result.getBody().getData();
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
