package com.clip.ghost.officecontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.exceptions.handler.GlobalExceptionErrorHandler;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.officecontent.dto.OfficeMarkdownResponse;
import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.service.OfficeMarkdownService;
import com.clip.ghost.officecontent.service.OfficePdfService;

/**
 * {@link OfficeMarkdownController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class OfficeMarkdownControllerTest {
	private static final String MARKDOWN_REQUEST_PATH = "/markdownDraftOffice";
	private static final String PDF_REQUEST_PATH = "/pdfFromOffice";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String OFFICE_FILE_PART_NAME = "officeFile";
	private static final String CHARACTER_ENCODING_UTF_8 = "UTF-8";

	@Mock
	private OfficeMarkdownService officeMarkdownService;

	@Mock
	private OfficePdfService officePdfService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		OfficeMarkdownController controller = new OfficeMarkdownController(officeMarkdownService, officePdfService,
				new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("Markdown生成ではtoken一致時にServiceへ処理を委譲し、共通ラッパーのJSONを返す")
	void generateMarkdownDelegatesToServiceWhenTokenMatches() throws Exception {
		OfficeMarkdownResponse response = new OfficeMarkdownResponse();
		response.setMarkdown("# 本文");
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(officeMarkdownService).generateMarkdown(any());

		MvcResult result = performRequest(MARKDOWN_REQUEST_PATH, ACCESS_TOKEN, "設計書.docx", true);

		verify(officeMarkdownService, times(1)).generateMarkdown(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown生成ではファイル未指定の場合に400を返し、Serviceを呼ばない")
	void generateMarkdownReturnsBadRequestWhenFileIsMissing() throws Exception {
		MvcResult result = performRequest(MARKDOWN_REQUEST_PATH, ACCESS_TOKEN, "設計書.docx", false);

		verify(officeMarkdownService, never()).generateMarkdown(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown生成では対応していない形式の場合に400と対応形式の案内を返す")
	void generateMarkdownReturnsBadRequestWhenFormatIsNotSupported() throws Exception {
		doThrow(new OfficeInputException("設計書.doc")).when(officeMarkdownService).generateMarkdown(any());

		MvcResult result = performRequest(MARKDOWN_REQUEST_PATH, ACCESS_TOKEN, "設計書.doc", true);

		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertTrue(responseBody.contains("officeInputError"));
		// 次に何をすれば通るのかが分かるよう、対応形式を名指しで返す。
		assertTrue(responseBody.contains(".docx / .xlsx / .pptx"));
	}

	@Test
	@DisplayName("Markdown生成ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void generateMarkdownReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(MARKDOWN_REQUEST_PATH, INVALID_ACCESS_TOKEN, "設計書.docx", true);

		verify(officeMarkdownService, never()).generateMarkdown(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF出力ではtoken一致時にServiceへ処理を委譲する")
	void generatePdfDelegatesToServiceWhenTokenMatches() throws Exception {
		doReturn(ResponseUtils.downloadPdf("設計書.pdf",
				new ByteArrayResource("%PDF-1.7".getBytes(StandardCharsets.UTF_8)))).when(officePdfService)
						.generatePdf(any());

		MvcResult result = performRequest(PDF_REQUEST_PATH, ACCESS_TOKEN, "設計書.docx", true);

		verify(officePdfService, times(1)).generatePdf(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF出力ではファイル未指定の場合に400を返し、Serviceを呼ばない")
	void generatePdfReturnsBadRequestWhenFileIsMissing() throws Exception {
		MvcResult result = performRequest(PDF_REQUEST_PATH, ACCESS_TOKEN, "設計書.docx", false);

		verify(officePdfService, never()).generatePdf(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF出力ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void generatePdfReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(PDF_REQUEST_PATH, INVALID_ACCESS_TOKEN, "設計書.docx", true);

		verify(officePdfService, never()).generatePdf(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	/**
	 * Office変換APIへmultipartリクエストを送信する。
	 *
	 * @param requestPath  送信先のパス
	 * @param accessToken  送信するaccess-token
	 * @param fileName     アップロードファイル名
	 * @param withFile     ファイルを添付する場合true
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performRequest(String requestPath, String accessToken, String fileName, boolean withFile)
			throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(requestPath);
		if (withFile) {
			requestBuilder.file(new MockMultipartFile(OFFICE_FILE_PART_NAME, fileName,
					MediaType.APPLICATION_OCTET_STREAM_VALUE, "office".getBytes(StandardCharsets.UTF_8)));
		}
		return mockMvc.perform(requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session)
				.characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}
}
