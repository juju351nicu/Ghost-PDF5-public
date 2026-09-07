package com.clip.ghost.imagecontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.exceptions.handler.GlobalExceptionErrorHandler;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftRequest;
import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftResponse;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;
import com.clip.ghost.imagecontent.service.ImageMarkdownDraftService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link ImageMarkdownDraftController} のHTTP endpoint、token検証、validation入口、無効時応答を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class ImageMarkdownDraftControllerTest {
	private static final String REQUEST_PATH = "/markdownDraftImage";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String IMAGE_FILE_PART_NAME = "imageFile";
	private static final long MAX_IMAGE_FILE_SIZE_BYTES = 20_559_957L;

	@Mock
	private ImageMarkdownDraftService imageMarkdownDraftService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		ImageMarkdownDraftController controller = new ImageMarkdownDraftController(imageMarkdownDraftService,
				new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲しJSONを返す")
	void generateMarkdownDraftDelegatesToServiceWhenTokenMatches() throws Exception {
		MockMultipartFile imageFile = createImageFile();
		ImageMarkdownDraftResponse response = new ImageMarkdownDraftResponse();
		response.setFileName("shot.png");
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(imageMarkdownDraftService)
				.generateMarkdownDraft(any(ImageMarkdownDraftRequest.class));

		MvcResult result = performRequest(imageFile, ACCESS_TOKEN, session);

		verify(imageMarkdownDraftService, times(1)).generateMarkdownDraft(any(ImageMarkdownDraftRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		assertApiResultEnvelope(result);
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返す")
	void generateMarkdownDraftReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(createImageFile(), INVALID_ACCESS_TOKEN, session);

		verify(imageMarkdownDraftService, never()).generateMarkdownDraft(any(ImageMarkdownDraftRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像未指定の場合に400を返す")
	void generateMarkdownDraftReturnsBadRequestWhenImageIsMissing() throws Exception {
		MvcResult result = performRequestWithoutFile(ACCESS_TOKEN, session);

		verify(imageMarkdownDraftService, never()).generateMarkdownDraft(any(ImageMarkdownDraftRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("画像サイズが既存上限以上の場合に413を返す")
	void generateMarkdownDraftReturnsPayloadTooLargeWhenImageIsTooLarge() throws Exception {
		MvcResult result = performRequest(createMaxSizeImageFile(), ACCESS_TOKEN, session);

		verify(imageMarkdownDraftService, never()).generateMarkdownDraft(any(ImageMarkdownDraftRequest.class));
		assertEquals(HttpStatus.CONTENT_TOO_LARGE.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("機能無効時は503を返す")
	void generateMarkdownDraftReturnsServiceUnavailableWhenDisabled() throws Exception {
		doThrow(new OcrUnavailableException("disabled")).when(imageMarkdownDraftService)
				.generateMarkdownDraft(any(ImageMarkdownDraftRequest.class));

		MvcResult result = performRequest(createImageFile(), ACCESS_TOKEN, session);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	/**
	 * 画像を含むmultipart requestを実行する。
	 *
	 * @param imageFile      生成元画像
	 * @param accessToken    access-token header値
	 * @param requestSession HTTPセッション
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequest(MockMultipartFile imageFile, String accessToken, MockHttpSession requestSession)
			throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH).file(imageFile).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession)).andReturn();
	}

	/**
	 * 画像を含まないmultipart requestを実行する。
	 *
	 * @param accessToken    access-token header値
	 * @param requestSession HTTPセッション
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequestWithoutFile(String accessToken, MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession)).andReturn();
	}

	/**
	 * 通常サイズのテスト用画像を生成する。
	 *
	 * @return 画像multipartファイル
	 */
	private MockMultipartFile createImageFile() {
		return new MockMultipartFile(IMAGE_FILE_PART_NAME, "shot.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1 });
	}

	/**
	 * 既存PDF APIの上限値と同じサイズを返すテスト用画像を生成する。
	 *
	 * @return サイズ上限値を返す画像multipartファイル
	 */
	private MockMultipartFile createMaxSizeImageFile() {
		return new MockMultipartFile(IMAGE_FILE_PART_NAME, "large.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1 }) {
			@Override
			public long getSize() {
				return MAX_IMAGE_FILE_SIZE_BYTES;
			}
		};
	}

	/**
	 * 成功レスポンスが共通ラッパー（data / resultType / messageList）の形であることを確認する。
	 *
	 * @param result HTTP実行結果
	 * @return ラッパー内のdataノード
	 * @throws Exception レスポンス本文を読み取れない場合
	 */
	private JsonNode assertApiResultEnvelope(MvcResult result) throws Exception {
		JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertEquals("INFO", body.path("resultType").asString());
		assertTrue(body.path("messageList").isArray());
		assertEquals(0, body.path("messageList").size());
		assertFalse(body.path("data").isMissingNode());
		return body.path("data");
	}

}
