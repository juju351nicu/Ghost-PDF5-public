package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.response.ApiResultType;
import com.clip.ghost.pdfcontent.config.PdfThumbnailProperties;
import com.clip.ghost.pdfcontent.dto.PdfPageThumbnail;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailRequest;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailResponse;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

/**
 * {@link PdfThumbnailService} のレスポンス組み立てと設定値の受け渡しを検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfThumbnailServiceTest {

	private static final int THUMBNAIL_DPI = 40;
	private static final int THUMBNAIL_MAX_PAGES = 100;
	private static final String DATA_URI_PREFIX = "data:image/png;base64,";

	@InjectMocks
	private PdfThumbnailService service;

	@Mock
	private GhostPdfLogic pdfLogic;

	@Mock
	private PdfThumbnailProperties properties;

	@Test
	@DisplayName("PDF情報とページ順のサムネイルを返す")
	void generateThumbnailsReturnsMetadataAndPagesInPdfOrder() {
		Path inputPath = Path.of("temporary", "sample.pdf");
		MockMultipartFile originalFile = createPdfFile("sample.pdf");
		PdfThumbnailRequest form = createRequest(originalFile);
		when(properties.getDpi()).thenReturn(THUMBNAIL_DPI);
		when(properties.getMaxPages()).thenReturn(THUMBNAIL_MAX_PAGES);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of(new PdfPageThumbnail(1, DATA_URI_PREFIX + "first", 331, 468),
				new PdfPageThumbnail(2, DATA_URI_PREFIX + "second", 331, 468))).when(pdfLogic)
				.extractPdfThumbnails(eq(inputPath), eq(THUMBNAIL_DPI), eq(THUMBNAIL_MAX_PAGES));

		ResponseEntity<ApiResult<PdfThumbnailResponse>> result = service.generateThumbnails(form);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());
		assertEquals(ApiResultType.INFO, result.getBody().getResultType());
		assertTrue(result.getBody().getMessageList().isEmpty());
		PdfThumbnailResponse response = result.getBody().getData();
		assertNotNull(response);
		assertEquals("sample.pdf", response.getFileName());
		assertEquals(2, response.getPageCount());
		assertPage(response.getPages().get(0), 1, DATA_URI_PREFIX + "first");
		assertPage(response.getPages().get(1), 2, DATA_URI_PREFIX + "second");
		assertEquals(331, response.getPages().get(0).getWidth());
		assertEquals(468, response.getPages().get(0).getHeight());
	}

	@Test
	@DisplayName("設定のDPIとページ数上限をLogicへ渡す")
	void generateThumbnailsPassesConfiguredDpiAndMaxPages() {
		Path inputPath = Path.of("temporary", "settings.pdf");
		MockMultipartFile originalFile = createPdfFile("settings.pdf");
		PdfThumbnailRequest form = createRequest(originalFile);
		when(properties.getDpi()).thenReturn(72);
		when(properties.getMaxPages()).thenReturn(10);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of(new PdfPageThumbnail(1, DATA_URI_PREFIX + "first", 595, 842))).when(pdfLogic)
				.extractPdfThumbnails(eq(inputPath), eq(72), eq(10));

		service.generateThumbnails(form);

		verify(pdfLogic, times(1)).extractPdfThumbnails(inputPath, 72, 10);
	}

	@Test
	@DisplayName("ページ数が上限を超えた場合はLogicの例外をそのまま伝播する")
	void generateThumbnailsPropagatesPageLimitException() {
		Path inputPath = Path.of("temporary", "limit.pdf");
		MockMultipartFile originalFile = createPdfFile("limit.pdf");
		PdfThumbnailRequest form = createRequest(originalFile);
		when(properties.getDpi()).thenReturn(THUMBNAIL_DPI);
		when(properties.getMaxPages()).thenReturn(1);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doThrow(new PdfPageLimitExceededException(3, 1)).when(pdfLogic).extractPdfThumbnails(eq(inputPath),
				eq(THUMBNAIL_DPI), eq(1));

		assertThrows(PdfPageLimitExceededException.class, () -> service.generateThumbnails(form));
	}

	/**
	 * テスト用のPDFアップロードファイルを生成する。
	 *
	 * @param fileName アップロード時のファイル名
	 * @return PDF multipartファイル
	 */
	private MockMultipartFile createPdfFile(String fileName) {
		return new MockMultipartFile("originalFile", fileName, MediaType.APPLICATION_PDF_VALUE, new byte[] { 1 });
	}

	/**
	 * テスト用のサムネイルリクエストを生成する。
	 *
	 * @param originalFile 生成元PDF
	 * @return サムネイルリクエスト
	 */
	private PdfThumbnailRequest createRequest(MockMultipartFile originalFile) {
		PdfThumbnailRequest request = new PdfThumbnailRequest();
		request.setOriginalFile(originalFile);
		return request;
	}

	/**
	 * 1ページ分のページ番号とdata URIを検証する。
	 *
	 * @param page               検証対象ページ
	 * @param expectedPageNumber 期待するページ番号
	 * @param expectedDataUri    期待するdata URI
	 */
	private void assertPage(PdfThumbnailPageResponse page, int expectedPageNumber, String expectedDataUri) {
		assertEquals(expectedPageNumber, page.getPageNumber());
		assertEquals(expectedDataUri, page.getDataUri());
	}
}
