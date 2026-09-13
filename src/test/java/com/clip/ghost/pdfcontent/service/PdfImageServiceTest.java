package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.pdfcontent.config.PdfImageProperties;
import com.clip.ghost.pdfcontent.dto.PdfImagesRequest;
import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.clip.ghost.pdfcontent.exception.PdfRenderDpiException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

/**
 * {@link PdfImageService} の解像度の既定値補完と上限検証を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfImageServiceTest {
	private static final int DEFAULT_DPI = 150;
	private static final int MAX_DPI = 600;
	private static final int MAX_PAGES = 200;

	@Mock
	private GhostPdfLogic pdfLogic;

	private PdfImageService imageService;

	@BeforeEach
	void setup() {
		PdfImageProperties properties = new PdfImageProperties();
		properties.setDefaultDpi(DEFAULT_DPI);
		properties.setMaxDpi(MAX_DPI);
		properties.setMaxPages(MAX_PAGES);
		imageService = new PdfImageService(pdfLogic, properties);
	}

	@Test
	@DisplayName("解像度が未指定の場合は設定の既定値で画像化する")
	void exportPdfImagesUsesDefaultDpiWhenNotSpecified() {
		stubPdfLogic();

		imageService.exportPdfImages(createRequest(null));

		verify(pdfLogic, times(1)).exportPdfImages(any(), eq(PdfImageFormat.PNG), eq(DEFAULT_DPI), eq(MAX_PAGES),
				any());
	}

	@Test
	@DisplayName("解像度が上限以内の場合は指定値をそのまま使う")
	void exportPdfImagesUsesRequestedDpiWhenWithinLimit() {
		stubPdfLogic();

		imageService.exportPdfImages(createRequest(MAX_DPI));

		verify(pdfLogic, times(1)).exportPdfImages(any(), eq(PdfImageFormat.PNG), eq(MAX_DPI), eq(MAX_PAGES), any());
	}

	@Test
	@DisplayName("解像度が上限を超えた場合は例外になり、PDFを読み込まない")
	void exportPdfImagesThrowsWhenDpiExceedsLimit() {
		PdfImagesRequest form = createRequest(MAX_DPI + 1);

		PdfRenderDpiException exception = assertThrows(PdfRenderDpiException.class,
				() -> imageService.exportPdfImages(form));

		assertEquals(MAX_DPI + 1, exception.getRequestedDpi());
		assertEquals(MAX_DPI, exception.getMaxDpi());
		// 上限判定はPDFを保存する前に行うため、一時ファイルが残らない。
		verify(pdfLogic, never()).loadPdf(any(), any());
	}

	@Test
	@DisplayName("画像化に成功した場合はZIPのダウンロードレスポンスを返す")
	void exportPdfImagesReturnsZipDownloadResponse() {
		stubPdfLogic();

		assertEquals(HttpStatus.OK, imageService.exportPdfImages(createRequest(null)).getStatusCode());
	}

	/**
	 * PDF処理ロジックが一時ファイルとリソースを返すようにstubする。
	 */
	private void stubPdfLogic() {
		Path temporaryPath = Path.of("images.zip");
		doReturn(temporaryPath).when(pdfLogic).loadPdf(any(), any());
		doReturn(temporaryPath).when(pdfLogic).exportPdfImages(any(), any(), anyInt(), anyInt(), any());
		doReturn(new ByteArrayResource("zip".getBytes(StandardCharsets.UTF_8))).when(pdfLogic)
				.openTemporaryFileForResponse(any());
	}

	/**
	 * ページ画像化リクエストを生成する。
	 *
	 * @param dpi 指定する解像度。nullの場合は未指定
	 * @return ページ画像化リクエスト
	 */
	private PdfImagesRequest createRequest(Integer dpi) {
		PdfImagesRequest form = new PdfImagesRequest();
		form.setOriginalFile(new MockMultipartFile("originalFile", "sample.pdf", "application/pdf",
				"pdf".getBytes(StandardCharsets.UTF_8)));
		form.setFormat(PdfImageFormat.PNG);
		form.setDpi(dpi);
		form.setImagePages(List.of(1));
		return form;
	}
}
