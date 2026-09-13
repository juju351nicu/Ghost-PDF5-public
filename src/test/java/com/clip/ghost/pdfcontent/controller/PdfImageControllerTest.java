package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clip.ghost.common.config.CodeEnumWebMvcConfig;
import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.exceptions.handler.GlobalExceptionErrorHandler;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.pdfcontent.exception.PdfRenderDpiException;
import com.clip.ghost.pdfcontent.service.PdfImageService;

/**
 * {@link PdfImageController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfImageControllerTest {
	private static final String REQUEST_PATH = "/imagesPdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";
	private static final String FORMAT_PARAM_NAME = "format";
	private static final String DPI_PARAM_NAME = "dpi";
	private static final String IMAGE_PAGES_PARAM_NAME = "imagePages";
	private static final String PDF_FROM_IMAGES_REQUEST_PATH = "/pdfFromImages";
	private static final String IMAGE_FILES_PART_NAME = "imageFiles";
	private static final String PAGE_SIZE_PARAM_NAME = "pageSize";

	@Mock
	private PdfImageService imageService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		PdfImageController controller = new PdfImageController(imageService, new AccessTokenValidator());
		// formatはenumで受けるため、本番と同じConverterFactoryを登録する。
		DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
		new CodeEnumWebMvcConfig().addFormatters(conversionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setConversionService(conversionService)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲し、ZIPを返す")
	void exportPdfImagesDelegatesToServiceWhenTokenMatches() throws Exception {
		stubImagesZipResponse();

		MvcResult result = performImagesPdf(ACCESS_TOKEN, "PNG", null, null);

		verify(imageService, times(1)).exportPdfImages(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像形式は小文字でも受け付ける")
	void exportPdfImagesAcceptsLowerCaseFormat() throws Exception {
		stubImagesZipResponse();

		MvcResult result = performImagesPdf(ACCESS_TOKEN, "png", null, null);

		verify(imageService, times(1)).exportPdfImages(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("対象外の画像形式を指定した場合は400を返し、Serviceを呼ばない")
	void exportPdfImagesReturnsBadRequestWhenFormatIsNotSupported() throws Exception {
		MvcResult result = performImagesPdf(ACCESS_TOKEN, "GIF", null, null);

		verify(imageService, never()).exportPdfImages(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像形式が未指定の場合は400を返し、Serviceを呼ばない")
	void exportPdfImagesReturnsBadRequestWhenFormatIsMissing() throws Exception {
		MvcResult result = performImagesPdf(ACCESS_TOKEN, null, null, null);

		verify(imageService, never()).exportPdfImages(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("解像度が0以下の場合は400を返し、Serviceを呼ばない")
	void exportPdfImagesReturnsBadRequestWhenDpiIsNotPositive() throws Exception {
		MvcResult result = performImagesPdf(ACCESS_TOKEN, "PNG", "0", null);

		verify(imageService, never()).exportPdfImages(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像化ページ番号が0の場合は400を返し、Serviceを呼ばない")
	void exportPdfImagesReturnsBadRequestWhenImagePageIsZero() throws Exception {
		MvcResult result = performImagesPdf(ACCESS_TOKEN, "PNG", null, "0");

		verify(imageService, never()).exportPdfImages(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("解像度が上限を超えた場合は400を返す")
	void exportPdfImagesReturnsBadRequestWhenDpiExceedsLimit() throws Exception {
		doThrow(new PdfRenderDpiException(1200, 600)).when(imageService).exportPdfImages(any());

		MvcResult result = performImagesPdf(ACCESS_TOKEN, "PNG", "1200", null);

		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返し、Serviceを呼ばない")
	void exportPdfImagesReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performImagesPdf(INVALID_ACCESS_TOKEN, "PNG", null, null);

		verify(imageService, never()).exportPdfImages(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像からPDF作成ではtoken一致時にServiceへ処理を委譲する")
	void createPdfFromImagesDelegatesToServiceWhenTokenMatches() throws Exception {
		doReturn(ResponseUtils.inlinePdf(new ByteArrayResource("pdf".getBytes(StandardCharsets.UTF_8))))
				.when(imageService).createPdfFromImages(any());

		MvcResult result = performPdfFromImages(ACCESS_TOKEN, "A4", true);

		verify(imageService, times(1)).createPdfFromImages(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像からPDF作成では画像が1件も無い場合に400を返し、Serviceを呼ばない")
	void createPdfFromImagesReturnsBadRequestWhenNoImageGiven() throws Exception {
		MvcResult result = performPdfFromImages(ACCESS_TOKEN, "A4", false);

		verify(imageService, never()).createPdfFromImages(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像からPDF作成では対象外のページサイズを指定した場合に400を返す")
	void createPdfFromImagesReturnsBadRequestWhenPageSizeIsNotSupported() throws Exception {
		MvcResult result = performPdfFromImages(ACCESS_TOKEN, "B5", true);

		verify(imageService, never()).createPdfFromImages(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像からPDF作成ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void createPdfFromImagesReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performPdfFromImages(INVALID_ACCESS_TOKEN, "A4", true);

		verify(imageService, never()).createPdfFromImages(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	/**
	 * ServiceがZIPレスポンスを返すようにstubする。
	 */
	private void stubImagesZipResponse() {
		doReturn(ResponseUtils.downloadZip("images.zip",
				new ByteArrayResource("zip".getBytes(StandardCharsets.UTF_8)))).when(imageService)
						.exportPdfImages(any());
	}

	/**
	 * ページ画像化APIへmultipartリクエストを送信する。
	 *
	 * @param accessToken 送信するaccess-token
	 * @param format      画像形式。nullの場合は送信しない
	 * @param dpi         解像度。nullの場合は送信しない
	 * @param imagePages  画像化ページ番号。nullの場合は送信しない
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performImagesPdf(String accessToken, String format, String dpi, String imagePages)
			throws Exception {
		MockMultipartFile originalFile = new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "sample.pdf",
				MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes(StandardCharsets.UTF_8));
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(REQUEST_PATH).file(originalFile);
		requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session);
		if (format != null) {
			requestBuilder.param(FORMAT_PARAM_NAME, format);
		}
		if (dpi != null) {
			requestBuilder.param(DPI_PARAM_NAME, dpi);
		}
		if (imagePages != null) {
			requestBuilder.param(IMAGE_PAGES_PARAM_NAME, imagePages);
		}
		return mockMvc.perform(requestBuilder).andReturn();
	}

	/**
	 * 画像からPDF作成APIへmultipartリクエストを送信する。
	 *
	 * @param accessToken 送信するaccess-token
	 * @param pageSize    ページサイズ。nullの場合は送信しない
	 * @param withImage   画像ファイルを添付する場合true
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performPdfFromImages(String accessToken, String pageSize, boolean withImage) throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(PDF_FROM_IMAGES_REQUEST_PATH);
		if (withImage) {
			requestBuilder.file(new MockMultipartFile(IMAGE_FILES_PART_NAME, "sample.png", MediaType.IMAGE_PNG_VALUE,
					"png".getBytes(StandardCharsets.UTF_8)));
		}
		requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session);
		if (pageSize != null) {
			requestBuilder.param(PAGE_SIZE_PARAM_NAME, pageSize);
		}
		return mockMvc.perform(requestBuilder).andReturn();
	}
}
