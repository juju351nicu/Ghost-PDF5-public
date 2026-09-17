package com.clip.ghost.aicontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clip.ghost.aicontent.dto.MarkdownAiTransformRequest;
import com.clip.ghost.aicontent.dto.MarkdownAiTransformResponse;
import com.clip.ghost.aicontent.enums.AiTaskType;
import com.clip.ghost.aicontent.exception.AiUnavailableException;
import com.clip.ghost.aicontent.service.MarkdownAiService;
import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.exceptions.handler.GlobalExceptionErrorHandler;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link MarkdownAiController} のHTTP endpoint、token検証、validation入口、無効時応答を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class MarkdownAiControllerTest {
	private static final String REQUEST_PATH = "/markdownAiTransform";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";

	@Mock
	private MarkdownAiService markdownAiService;

	private MockMvc mockMvc;
	private MockHttpSession session;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		MarkdownAiController controller = new MarkdownAiController(markdownAiService, new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲しJSONを返す")
	void transformMarkdownDelegatesToServiceWhenTokenMatches() throws Exception {
		MarkdownAiTransformResponse response = new MarkdownAiTransformResponse();
		response.setTask(AiTaskType.REFINE);
		response.setMarkdown("整形後本文");
		response.setInputCharacterCount(4);
		response.setOutputCharacterCount(5);
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(markdownAiService)
				.transformMarkdown(any(MarkdownAiTransformRequest.class));

		MvcResult result = performRequest(requestBody("本文です", "REFINE"), ACCESS_TOKEN);

		verify(markdownAiService, times(1)).transformMarkdown(any(MarkdownAiTransformRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		JsonNode data = assertApiResultEnvelope(result);
		assertEquals("REFINE", data.path("task").asString());
		assertEquals("整形後本文", data.path("markdown").asString());
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返す")
	void transformMarkdownReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(requestBody("本文です", "REFINE"), INVALID_ACCESS_TOKEN);

		verify(markdownAiService, never()).transformMarkdown(any(MarkdownAiTransformRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("contentが未指定の場合に400を返す")
	void transformMarkdownReturnsBadRequestWhenContentIsMissing() throws Exception {
		MvcResult result = performRequest("{\"task\":\"REFINE\"}", ACCESS_TOKEN);

		verify(markdownAiService, never()).transformMarkdown(any(MarkdownAiTransformRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("taskが未指定の場合に400を返す")
	void transformMarkdownReturnsBadRequestWhenTaskIsMissing() throws Exception {
		MvcResult result = performRequest("{\"content\":\"本文です\"}", ACCESS_TOKEN);

		verify(markdownAiService, never()).transformMarkdown(any(MarkdownAiTransformRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("機能無効時は503を返す")
	void transformMarkdownReturnsServiceUnavailableWhenDisabled() throws Exception {
		doThrow(new AiUnavailableException("disabled")).when(markdownAiService)
				.transformMarkdown(any(MarkdownAiTransformRequest.class));

		MvcResult result = performRequest(requestBody("本文です", "REFINE"), ACCESS_TOKEN);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	/**
	 * JSON本文でリクエストを実行する。
	 *
	 * @param body        リクエストJSON本文
	 * @param accessToken access-token header値
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequest(String body, String accessToken) throws Exception {
		return mockMvc
				.perform(post(REQUEST_PATH).contentType(MediaType.APPLICATION_JSON).content(body)
						.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session))
				.andReturn();
	}

	/**
	 * テスト用のリクエストJSON本文を組み立てる。
	 *
	 * @param content 変換対象Markdown本文
	 * @param task    変換タスクコード
	 * @return リクエストJSON本文
	 * @throws Exception JSON組み立てに失敗した場合
	 */
	private String requestBody(String content, String task) throws Exception {
		return objectMapper.writeValueAsString(new TestRequestBody(content, task));
	}

	/**
	 * 成功レスポンスが共通ラッパー（data / resultType / messageList）の形であることを確認する。
	 *
	 * @param result HTTP実行結果
	 * @return ラッパー内のdataノード
	 * @throws Exception レスポンス本文を読み取れない場合
	 */
	private JsonNode assertApiResultEnvelope(MvcResult result) throws Exception {
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertEquals("INFO", body.path("resultType").asString());
		assertTrue(body.path("messageList").isArray());
		assertEquals(0, body.path("messageList").size());
		assertFalse(body.path("data").isMissingNode());
		return body.path("data");
	}

	/**
	 * リクエストJSON組み立て専用のテストレコード。
	 *
	 * @param content 変換対象Markdown本文
	 * @param task    変換タスクコード
	 */
	private record TestRequestBody(String content, String task) {
	}
}
