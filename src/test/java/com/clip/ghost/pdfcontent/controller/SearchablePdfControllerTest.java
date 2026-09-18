package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

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
import com.clip.ghost.common.constant.UploadConstants;
import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.exceptions.handler.GlobalExceptionErrorHandler;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.pdfcontent.dto.SearchablePdfRequest;
import com.clip.ghost.pdfcontent.exception.SearchablePdfUnavailableException;
import com.clip.ghost.pdfcontent.service.SearchablePdfService;

/**
 * {@link SearchablePdfController} のHTTP endpoint、token検証、validation入口、無効時応答を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class SearchablePdfControllerTest {
	private static final String REQUEST_PATH = "/searchablePdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";
	private static final String MODE_PARAM_NAME = "mode";

	@Mock
	private SearchablePdfService searchablePdfService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		SearchablePdfController controller = new SearchablePdfController(searchablePdfService,
				new AccessTokenValidator());
		DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
		new CodeEnumWebMvcConfig().addFormatters(conversionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setConversionService(conversionService)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler())
				.build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲しPDFを返す")
	void createSearchablePdfDelegatesToServiceWhenTokenMatches() throws Exception {
		doReturn(ResponseUtils.inlinePdf(new ByteArrayResource(new byte[] { 1, 2, 3 }))).when(searchablePdfService)
				.createSearchablePdf(any(SearchablePdfRequest.class));

		MvcResult result = performRequest(createPdfFile(), ACCESS_TOKEN, null);

		verify(searchablePdfService, times(1)).createSearchablePdf(any(SearchablePdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_PDF_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("modeを指定した場合もServiceへ処理を委譲する")
	void createSearchablePdfDelegatesToServiceWhenModeSpecified() throws Exception {
		doReturn(ResponseUtils.inlinePdf(new ByteArrayResource(new byte[] { 1 }))).when(searchablePdfService)
				.createSearchablePdf(any(SearchablePdfRequest.class));

		MvcResult result = performRequest(createPdfFile(), ACCESS_TOKEN, "FORCE_OCR");

		verify(searchablePdfService, times(1)).createSearchablePdf(any(SearchablePdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返す")
	void createSearchablePdfReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(createPdfFile(), INVALID_ACCESS_TOKEN, null);

		verify(searchablePdfService, never()).createSearchablePdf(any(SearchablePdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF未指定の場合に400を返す")
	void createSearchablePdfReturnsBadRequestWhenPdfIsMissing() throws Exception {
		MvcResult result = performRequestWithoutFile(ACCESS_TOKEN);

		verify(searchablePdfService, never()).createSearchablePdf(any(SearchablePdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("modeがAUTO / FORCE_OCR以外の場合に400を返す")
	void createSearchablePdfReturnsBadRequestWhenModeIsInvalid() throws Exception {
		MvcResult result = performRequest(createPdfFile(), ACCESS_TOKEN, "UNKNOWN");

		verify(searchablePdfService, never()).createSearchablePdf(any(SearchablePdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFサイズが既存上限以上の場合に413を返す")
	void createSearchablePdfReturnsPayloadTooLargeWhenPdfIsTooLarge() throws Exception {
		MvcResult result = performRequest(createMaxSizePdfFile(), ACCESS_TOKEN, null);

		verify(searchablePdfService, never()).createSearchablePdf(any(SearchablePdfRequest.class));
		assertEquals(HttpStatus.CONTENT_TOO_LARGE.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Tesseractが無効な場合は503を返す")
	void createSearchablePdfReturnsServiceUnavailableWhenTesseractDisabled() throws Exception {
		doThrow(new SearchablePdfUnavailableException("disabled")).when(searchablePdfService)
				.createSearchablePdf(any(SearchablePdfRequest.class));

		MvcResult result = performRequest(createPdfFile(), ACCESS_TOKEN, null);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	/**
	 * PDFを含むmultipart requestを実行する。
	 *
	 * @param originalFile 送信するPDF
	 * @param accessToken  access-token header値
	 * @param mode         変換モードのコード値。未指定はnull
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequest(MockMultipartFile originalFile, String accessToken, String mode)
			throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(REQUEST_PATH).file(originalFile);
		requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session);
		if (mode != null) {
			requestBuilder.param(MODE_PARAM_NAME, mode);
		}
		return mockMvc.perform(requestBuilder).andReturn();
	}

	/**
	 * PDFを含まないmultipart requestを実行する。
	 *
	 * @param accessToken access-token header値
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequestWithoutFile(String accessToken) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH).header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session))
				.andReturn();
	}

	/**
	 * 通常サイズのテスト用PDFを生成する。
	 *
	 * @return PDF multipartファイル
	 */
	private MockMultipartFile createPdfFile() {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "input.pdf", MediaType.APPLICATION_PDF_VALUE,
				new byte[] { 1 });
	}

	/**
	 * 既存PDF APIの上限値と同じサイズを返すテスト用PDFを生成する。
	 *
	 * @return サイズ上限値を返すPDF multipartファイル
	 */
	private MockMultipartFile createMaxSizePdfFile() {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "large.pdf", MediaType.APPLICATION_PDF_VALUE,
				new byte[] { 1 }) {
			@Override
			public long getSize() {
				return UploadConstants.MAX_UPLOAD_FILE_SIZE_BYTES;
			}
		};
	}
}
