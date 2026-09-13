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
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.service.PdfHtmlService;

/**
 * {@link PdfHtmlController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfHtmlControllerTest {
	private static final String REQUEST_PATH = "/htmlPdf";
	private static final String EPUB_REQUEST_PATH = "/epubPdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";
	private static final String MODE_PARAM_NAME = "mode";

	@Mock
	private PdfHtmlService htmlService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		PdfHtmlController controller = new PdfHtmlController(htmlService, new AccessTokenValidator());
		// modeはenumで受けるため、本番と同じConverterFactoryを登録する。
		DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
		new CodeEnumWebMvcConfig().addFormatters(conversionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setConversionService(conversionService)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲し、HTMLを返す")
	void generateHtmlDelegatesToServiceWhenTokenMatches() throws Exception {
		stubHtmlResponse();

		MvcResult result = performHtmlPdf(ACCESS_TOKEN, null, true);

		verify(htmlService, times(1)).generateHtml(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("変換モードを指定してもServiceへ処理を委譲する")
	void generateHtmlDelegatesToServiceWhenModeIsSpecified() throws Exception {
		stubHtmlResponse();

		MvcResult result = performHtmlPdf(ACCESS_TOKEN, "VISION", true);

		verify(htmlService, times(1)).generateHtml(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("対象外の変換モードを指定した場合は400を返し、Serviceを呼ばない")
	void generateHtmlReturnsBadRequestWhenModeIsNotSupported() throws Exception {
		MvcResult result = performHtmlPdf(ACCESS_TOKEN, "FULL", true);

		verify(htmlService, never()).generateHtml(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFファイル未指定の場合は400を返し、Serviceを呼ばない")
	void generateHtmlReturnsBadRequestWhenOriginalFileIsMissing() throws Exception {
		MvcResult result = performHtmlPdf(ACCESS_TOKEN, null, false);

		verify(htmlService, never()).generateHtml(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("変換対象ページ数が上限を超えた場合は400を返す")
	void generateHtmlReturnsBadRequestWhenPageLimitExceeded() throws Exception {
		doThrow(new PdfPageLimitExceededException(30, 20)).when(htmlService).generateHtml(any());

		MvcResult result = performHtmlPdf(ACCESS_TOKEN, "VISION", true);

		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返し、Serviceを呼ばない")
	void generateHtmlReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performHtmlPdf(INVALID_ACCESS_TOKEN, null, true);

		verify(htmlService, never()).generateHtml(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("EPUB出力ではtoken一致時にServiceへ処理を委譲する")
	void generateEpubDelegatesToServiceWhenTokenMatches() throws Exception {
		doReturn(ResponseUtils.downloadEpub("design.epub",
				new ByteArrayResource("epub".getBytes(StandardCharsets.UTF_8)))).when(htmlService)
						.generateEpub(any());

		MvcResult result = performEpubPdf(ACCESS_TOKEN, true);

		verify(htmlService, times(1)).generateEpub(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("EPUB出力ではPDFファイル未指定の場合に400を返し、Serviceを呼ばない")
	void generateEpubReturnsBadRequestWhenOriginalFileIsMissing() throws Exception {
		MvcResult result = performEpubPdf(ACCESS_TOKEN, false);

		verify(htmlService, never()).generateEpub(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("EPUB出力ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void generateEpubReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performEpubPdf(INVALID_ACCESS_TOKEN, true);

		verify(htmlService, never()).generateEpub(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	/**
	 * PDFからEPUB出力APIへmultipartリクエストを送信する。
	 *
	 * @param accessToken     送信するaccess-token
	 * @param withOriginalPdf PDFファイルを添付する場合true
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performEpubPdf(String accessToken, boolean withOriginalPdf) throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(EPUB_REQUEST_PATH);
		if (withOriginalPdf) {
			requestBuilder.file(new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "sample.pdf",
					MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes(StandardCharsets.UTF_8)));
		}
		return mockMvc.perform(requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session))
				.andReturn();
	}

	/**
	 * ServiceがHTMLレスポンスを返すようにstubする。
	 */
	private void stubHtmlResponse() {
		doReturn(ResponseUtils.downloadHtml("design.html",
				new ByteArrayResource("<html></html>".getBytes(StandardCharsets.UTF_8)))).when(htmlService)
						.generateHtml(any());
	}

	/**
	 * PDFからHTML出力APIへmultipartリクエストを送信する。
	 *
	 * @param accessToken     送信するaccess-token
	 * @param mode            変換モード。nullの場合は送信しない
	 * @param withOriginalPdf PDFファイルを添付する場合true
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performHtmlPdf(String accessToken, String mode, boolean withOriginalPdf) throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(REQUEST_PATH);
		if (withOriginalPdf) {
			requestBuilder.file(new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "sample.pdf",
					MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes(StandardCharsets.UTF_8)));
		}
		requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session);
		if (mode != null) {
			requestBuilder.param(MODE_PARAM_NAME, mode);
		}
		return mockMvc.perform(requestBuilder).andReturn();
	}
}
