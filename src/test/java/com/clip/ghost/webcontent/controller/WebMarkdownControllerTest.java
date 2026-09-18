package com.clip.ghost.webcontent.controller;

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

import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftResponse;
import com.clip.ghost.webcontent.exception.WebInputException;
import com.clip.ghost.webcontent.service.WebMarkdownService;

/**
 * {@link WebMarkdownController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class WebMarkdownControllerTest {
	private static final String REQUEST_PATH = "/markdownDraftHtml";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String HTML_FILE_PART_NAME = "htmlFile";
	private static final String CHARACTER_ENCODING_UTF_8 = "UTF-8";
	private static final String HTML_FILE_NAME = "page.html";

	@Mock
	private WebMarkdownService webMarkdownService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		WebMarkdownController controller = new WebMarkdownController(webMarkdownService, new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲し、共通ラッパーのJSONを返す")
	void generateMarkdownFromHtmlDelegatesToServiceWhenTokenMatches() throws Exception {
		WebMarkdownDraftResponse response = new WebMarkdownDraftResponse();
		response.setMarkdown("# 設計メモ");
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(webMarkdownService).generateMarkdownFromHtml(any());

		MvcResult result = performRequest(ACCESS_TOKEN, HTML_FILE_NAME, true, 1024);

		verify(webMarkdownService, times(1)).generateMarkdownFromHtml(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("ファイル未指定の場合は400を返し、Serviceを呼ばない")
	void generateMarkdownFromHtmlReturnsBadRequestWhenFileIsMissing() throws Exception {
		MvcResult result = performRequest(ACCESS_TOKEN, HTML_FILE_NAME, false, 0);

		verify(webMarkdownService, never()).generateMarkdownFromHtml(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合は403を返す")
	void generateMarkdownFromHtmlReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(INVALID_ACCESS_TOKEN, HTML_FILE_NAME, true, 1024);

		verify(webMarkdownService, never()).generateMarkdownFromHtml(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("入力が不正な場合は400と、何を直せば通るかの説明を返す")
	void generateMarkdownFromHtmlReturnsBadRequestWhenInputIsInvalid() throws Exception {
		doThrow(new WebInputException("HTMLとして読み込めないファイルです。.html / .htm を指定してください。")).when(webMarkdownService)
				.generateMarkdownFromHtml(any());

		MvcResult result = performRequest(ACCESS_TOKEN, "page.pdf", true, 1024);

		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertTrue(Strings.CS.contains(responseBody, "webInputError"));
		assertTrue(Strings.CS.contains(responseBody, ".html / .htm"));
	}

	@Test
	@DisplayName("アップロードサイズが上限以上の場合は413を返し、Serviceを呼ばない")
	void generateMarkdownFromHtmlReturnsPayloadTooLargeWhenFileIsTooLarge() throws Exception {
		MvcResult result = performRequest(ACCESS_TOKEN, HTML_FILE_NAME, true,
				(int) PdfConstants.MAX_PDF_FILE_SIZE_BYTES);

		verify(webMarkdownService, never()).generateMarkdownFromHtml(any());
		assertEquals(HttpStatus.CONTENT_TOO_LARGE.value(), result.getResponse().getStatus());
	}

	/**
	 * Webページ取り込みAPIへmultipartリクエストを送信する。
	 *
	 * @param accessToken 送信するaccess-token
	 * @param fileName    アップロードファイル名
	 * @param withFile    ファイルを添付する場合true
	 * @param fileSize    添付するファイルのバイト数
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performRequest(String accessToken, String fileName, boolean withFile, int fileSize)
			throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(REQUEST_PATH);
		if (withFile) {
			requestBuilder.file(new MockMultipartFile(HTML_FILE_PART_NAME, fileName, MediaType.TEXT_HTML_VALUE,
					new byte[fileSize]));
		}
		return mockMvc.perform(requestBuilder.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session)
				.characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}
}
