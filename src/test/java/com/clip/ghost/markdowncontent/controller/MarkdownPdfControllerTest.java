package com.clip.ghost.markdowncontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.exceptions.handler.GlobalExceptionErrorHandler;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.markdowncontent.dto.MarkdownPdfRequest;
import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;
import com.clip.ghost.markdowncontent.service.MarkdownPdfService;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link MarkdownPdfController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class MarkdownPdfControllerTest {
	private static final String REQUEST_PATH = "/markdownPdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String CHARACTER_ENCODING_UTF_8 = "UTF-8";

	@Mock
	private MarkdownPdfService markdownPdfService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		MarkdownPdfController controller = new MarkdownPdfController(markdownPdfService, new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲しPDFを返す")
	void generateMarkdownPdfDelegatesToServiceWhenTokenMatches() throws Exception {
		stubPdfResponse();

		MvcResult result = performRequest(createRequestJson("design-note.md", "# 設計書"), ACCESS_TOKEN, session);

		verify(markdownPdfService, times(1)).generatePdf(any(MarkdownPdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_PDF_VALUE, result.getResponse().getContentType());
		assertTrue(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION).contains("design-note.pdf"));
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返す")
	void generateMarkdownPdfReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(createRequestJson("design-note.md", "# 設計書"), INVALID_ACCESS_TOKEN,
				session);

		verify(markdownPdfService, never()).generatePdf(any(MarkdownPdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown本文が未指定の場合に400を返す")
	void generateMarkdownPdfReturnsBadRequestWhenContentIsMissing() throws Exception {
		MvcResult result = performRequest("{\"fileName\":\"design-note.md\"}", ACCESS_TOKEN, session);

		verify(markdownPdfService, never()).generatePdf(any(MarkdownPdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Markdown本文を入力してください。"));
	}

	@Test
	@DisplayName("PDF出力に失敗した場合に500と共通エラー形式を返す")
	void generateMarkdownPdfReturnsInternalServerErrorWhenRenderingFails() throws Exception {
		doThrow(new MarkdownPdfException("描画に失敗しました。")).when(markdownPdfService)
				.generatePdf(any(MarkdownPdfRequest.class));

		MvcResult result = performRequest(createRequestJson("design-note.md", "# 設計書"), ACCESS_TOKEN, session);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.getResponse().getStatus());
		String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertTrue(responseBody.contains("markdownPdfError"));
		// 失敗の詳細（本文やローカルパス）は画面へ出さない。
		assertTrue(responseBody.contains("MarkdownからのPDF出力に失敗しました。"));
	}

	/**
	 * PDF生成Serviceの正常レスポンスをmockする。
	 */
	private void stubPdfResponse() {
		Resource contents = new ByteArrayResource("%PDF-1.7".getBytes(StandardCharsets.UTF_8));
		ResponseEntity<Resource> response = ResponseUtils.downloadPdf("design-note.pdf", contents);
		doReturn(response).when(markdownPdfService).generatePdf(any(MarkdownPdfRequest.class));
	}

	/**
	 * テスト用のリクエストJSONを組み立てる。
	 *
	 * @param fileName ファイル名
	 * @param content  Markdown本文
	 * @return リクエストJSON
	 */
	private String createRequestJson(String fileName, String content) {
		MarkdownPdfRequest request = new MarkdownPdfRequest();
		request.setFileName(fileName);
		request.setContent(content);
		return new ObjectMapper().writeValueAsString(request);
	}

	/**
	 * JSON bodyのPOST requestを実行する。
	 *
	 * @param requestJson    リクエストJSON
	 * @param accessToken    access-token header値
	 * @param requestSession HTTPセッション
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequest(String requestJson, String accessToken, MockHttpSession requestSession)
			throws Exception {
		return mockMvc.perform(post(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON)
				.characterEncoding(CHARACTER_ENCODING_UTF_8).content(requestJson)
				.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(requestSession)).andReturn();
	}
}
