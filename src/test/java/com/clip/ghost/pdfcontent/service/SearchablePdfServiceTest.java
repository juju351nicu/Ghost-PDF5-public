package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.imagecontent.dto.OcrWordBox;
import com.clip.ghost.imagecontent.logic.TesseractWordBoxExtractor;
import com.clip.ghost.pdfcontent.config.PdfOcrProperties;
import com.clip.ghost.pdfcontent.dto.SearchablePdfRequest;
import com.clip.ghost.pdfcontent.enums.SearchablePdfMode;
import com.clip.ghost.pdfcontent.exception.SearchablePdfUnavailableException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;
import com.clip.ghost.pdfcontent.logic.SearchablePdfPageOcr;

/**
 * {@link SearchablePdfService} の有効性確認、変換モードの既定値解決、PDFレスポンス組み立てを検証するテスト。
 * <p>
 * Tesseractは呼ばず、{@link TesseractWordBoxExtractor} と {@link GhostPdfLogic} をmockする。
 */
@ExtendWith(MockitoExtension.class)
class SearchablePdfServiceTest {
	@InjectMocks
	private SearchablePdfService searchablePdfService;

	@Mock
	private GhostPdfLogic pdfLogic;

	@Mock
	private TesseractWordBoxExtractor wordBoxExtractor;

	@Mock
	private PdfOcrProperties properties;

	@Test
	@DisplayName("Tesseractが無効な場合は503相当の例外を投げ、PDFを読み込まない")
	void createSearchablePdfThrowsWhenTesseractDisabled() {
		when(wordBoxExtractor.isEnabled()).thenReturn(false);

		assertThrows(SearchablePdfUnavailableException.class,
				() -> searchablePdfService.createSearchablePdf(createRequest(null)));

		verify(pdfLogic, never()).loadPdf(any(), any());
	}

	@Test
	@DisplayName("modeを省略した場合はAUTOとしてLogicへ渡す")
	void createSearchablePdfDefaultsToAutoModeWhenOmitted() {
		when(wordBoxExtractor.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(200);
		when(properties.getMaxPages()).thenReturn(20);
		Path inputPath = Paths.get("input.pdf");
		Path outputPath = Paths.get("output.pdf");
		when(pdfLogic.loadPdf(any(), any())).thenReturn(inputPath);
		when(pdfLogic.createSearchablePdf(any(), anyInt(), anyInt(), eq(inputPath), any())).thenReturn(outputPath);
		doReturn(new ByteArrayResource(new byte[] { 1, 2, 3 })).when(pdfLogic).openTemporaryFileForResponse(outputPath);

		ResponseEntity<Resource> result = searchablePdfService.createSearchablePdf(createRequest(null));

		verify(pdfLogic).createSearchablePdf(eq(SearchablePdfMode.AUTO), eq(200), eq(20), eq(inputPath), any());
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(MediaType.APPLICATION_PDF, result.getHeaders().getContentType());
	}

	@Test
	@DisplayName("mode=FORCE_OCRを指定した場合はそのままLogicへ渡す")
	void createSearchablePdfPassesForceOcrModeThrough() {
		when(wordBoxExtractor.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(200);
		when(properties.getMaxPages()).thenReturn(20);
		Path inputPath = Paths.get("input.pdf");
		Path outputPath = Paths.get("output.pdf");
		when(pdfLogic.loadPdf(any(), any())).thenReturn(inputPath);
		when(pdfLogic.createSearchablePdf(any(), anyInt(), anyInt(), eq(inputPath), any())).thenReturn(outputPath);
		doReturn(new ByteArrayResource(new byte[] { 1 })).when(pdfLogic).openTemporaryFileForResponse(outputPath);

		searchablePdfService.createSearchablePdf(createRequest(SearchablePdfMode.FORCE_OCR));

		verify(pdfLogic).createSearchablePdf(eq(SearchablePdfMode.FORCE_OCR), eq(200), eq(20), eq(inputPath), any());
	}

	@Test
	@DisplayName("Logicへ渡すOCRコールバックは共有の単語ボックス抽出器へ委譲する")
	void createSearchablePdfDelegatesPageOcrToWordBoxExtractor() {
		when(wordBoxExtractor.isEnabled()).thenReturn(true);
		when(properties.getRenderDpi()).thenReturn(200);
		when(properties.getMaxPages()).thenReturn(20);
		Path inputPath = Paths.get("input.pdf");
		Path outputPath = Paths.get("output.pdf");
		when(pdfLogic.loadPdf(any(), any())).thenReturn(inputPath);
		when(pdfLogic.createSearchablePdf(any(), anyInt(), anyInt(), eq(inputPath), any())).thenReturn(outputPath);
		doReturn(new ByteArrayResource(new byte[] { 1 })).when(pdfLogic).openTemporaryFileForResponse(outputPath);
		List<OcrWordBox> expectedWordBoxes = List.of(new OcrWordBox("text", 0, 0, 10, 10));
		byte[] pngBytes = { 9, 9, 9 };
		when(wordBoxExtractor.extractWordBoxes(pngBytes)).thenReturn(expectedWordBoxes);

		searchablePdfService.createSearchablePdf(createRequest(null));

		ArgumentCaptor<SearchablePdfPageOcr> pageOcrCaptor = ArgumentCaptor.forClass(SearchablePdfPageOcr.class);
		verify(pdfLogic).createSearchablePdf(any(), anyInt(), anyInt(), eq(inputPath), pageOcrCaptor.capture());
		assertEquals(expectedWordBoxes, pageOcrCaptor.getValue().recognize(pngBytes));
	}

	/**
	 * テスト用の検索可能PDFリクエストを生成する。
	 *
	 * @param mode 変換モード
	 * @return 検索可能PDFリクエスト
	 */
	private SearchablePdfRequest createRequest(SearchablePdfMode mode) {
		SearchablePdfRequest request = new SearchablePdfRequest();
		request.setOriginalFile(new MockMultipartFile("originalFile", "input.pdf", "application/pdf", new byte[] { 1 }));
		request.setMode(mode);
		return request;
	}
}
