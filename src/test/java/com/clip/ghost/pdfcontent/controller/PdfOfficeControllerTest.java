package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.clip.ghost.pdfcontent.service.PdfOfficeService;

/**
 * {@link PdfOfficeController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfOfficeControllerTest {
	private static final String REQUEST_PATH = "/officeFromPdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";
	private static final String FORMAT_PARAM_NAME = "format";

	@Mock
	private PdfOfficeService officeService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		PdfOfficeController controller = new PdfOfficeController(officeService, new AccessTokenValidator());
		// formatはenumで受けるため、本番と同じConverterFactoryを登録する。
		DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
		new CodeEnumWebMvcConfig().addFormatters(conversionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setConversionService(conversionService)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@ParameterizedTest
	@ValueSource(strings = { "DOCX", "XLSX", "PPTX", "docx" })
	@DisplayName("対応形式ではtoken一致時にServiceへ処理を委譲する")
	void generateOfficeDelegatesToServiceForEverySupportedFormat(String format) throws Exception {
		doReturn(ResponseUtils.downloadOffice("document.docx",
				new ByteArrayResource("office".getBytes(StandardCharsets.UTF_8)))).when(officeService)
						.generateOffice(any());

		MvcResult result = performRequest(ACCESS_TOKEN, format, true);

		verify(officeService, times(1)).generateOffice(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("対象外の出力形式を指定した場合は400を返し、Serviceを呼ばない")
	void generateOfficeReturnsBadRequestWhenFormatIsNotSupported() throws Exception {
		MvcResult result = performRequest(ACCESS_TOKEN, "DOC", true);

		verify(officeService, never()).generateOffice(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("出力形式が未指定の場合は400を返し、Serviceを呼ばない")
	void generateOfficeReturnsBadRequestWhenFormatIsMissing() throws Exception {
		MvcResult result = performRequest(ACCESS_TOKEN, null, true);

		verify(officeService, never()).generateOffice(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFファイル未指定の場合は400を返し、Serviceを呼ばない")
	void generateOfficeReturnsBadRequestWhenOriginalFileIsMissing() throws Exception {
		MvcResult result = performRequest(ACCESS_TOKEN, "DOCX", false);

		verify(officeService, never()).generateOffice(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返し、Serviceを呼ばない")
	void generateOfficeReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(INVALID_ACCESS_TOKEN, "DOCX", true);

		verify(officeService, never()).generateOffice(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	/**
	 * PDFからOffice文書出力APIへmultipartリクエストを送信する。
	 *
	 * @param accessToken     送信するaccess-token
	 * @param format          出力形式。nullの場合は送信しない
	 * @param withOriginalPdf PDFファイルを添付する場合true
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performRequest(String accessToken, String format, boolean withOriginalPdf) throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(REQUEST_PATH);
		if (withOriginalPdf) {
			requestBuilder.file(new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "sample.pdf",
					MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes(StandardCharsets.UTF_8)));
		}
		requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session);
		if (format != null) {
			requestBuilder.param(FORMAT_PARAM_NAME, format);
		}
		return mockMvc.perform(requestBuilder).andReturn();
	}
}
