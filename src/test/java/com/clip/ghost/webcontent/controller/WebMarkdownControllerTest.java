package com.clip.ghost.webcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftRequest;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftResponse;
import com.clip.ghost.webcontent.dto.WebUrlMarkdownDraftRequest;
import com.clip.ghost.webcontent.enums.WebMarkdownDraftMode;
import com.clip.ghost.webcontent.exception.WebFetchBlockedException;
import com.clip.ghost.webcontent.exception.WebFetchException;
import com.clip.ghost.webcontent.exception.WebInputException;
import com.clip.ghost.webcontent.exception.WebUnavailableException;
import com.clip.ghost.webcontent.service.WebMarkdownService;

/**
 * {@link WebMarkdownController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class WebMarkdownControllerTest {
	private static final String REQUEST_PATH = "/markdownDraftHtml";
	private static final String URL_REQUEST_PATH = "/markdownDraftUrl";
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
		// modeはenumで受けるため、本番と同じConverterFactoryを登録する。
		DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
		new CodeEnumWebMvcConfig().addFormatters(conversionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setConversionService(conversionService)
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
				(int) UploadConstants.MAX_UPLOAD_FILE_SIZE_BYTES);

		verify(webMarkdownService, never()).generateMarkdownFromHtml(any());
		assertEquals(HttpStatus.CONTENT_TOO_LARGE.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("URL取得ではtoken一致時にServiceへ処理を委譲する")
	void generateMarkdownFromUrlDelegatesToServiceWhenTokenMatches() throws Exception {
		WebMarkdownDraftResponse response = new WebMarkdownDraftResponse();
		response.setMarkdown("# 設計メモ");
		response.setSourceUrl("https://example.test/article");
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(webMarkdownService).generateMarkdownFromUrl(any());

		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":\"https://example.test/article\"}");

		verify(webMarkdownService, times(1)).generateMarkdownFromUrl(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("URL取得ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void generateMarkdownFromUrlReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performUrlRequest(INVALID_ACCESS_TOKEN, "{\"url\":\"https://example.test/article\"}");

		verify(webMarkdownService, never()).generateMarkdownFromUrl(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("URL取得ではURLが空の場合に400を返し、Serviceを呼ばない")
	void generateMarkdownFromUrlReturnsBadRequestWhenUrlIsBlank() throws Exception {
		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":\"\"}");

		verify(webMarkdownService, never()).generateMarkdownFromUrl(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("URL取得機能が無効な場合は503を返す")
	void generateMarkdownFromUrlReturnsServiceUnavailableWhenDisabled() throws Exception {
		doThrow(new WebUnavailableException("URLからのWebページ取得機能は無効です。")).when(webMarkdownService)
				.generateMarkdownFromUrl(any());

		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":\"https://example.test/article\"}");

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), result.getResponse().getStatus());
		assertTrue(Strings.CS.contains(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
				"webUnavailable"));
	}

	@Test
	@DisplayName("接続が許可されない宛先の場合は400を返し、解決済みIPをレスポンスへ出さない")
	void generateMarkdownFromUrlReturnsBadRequestWhenDestinationIsBlocked() throws Exception {
		doThrow(new WebFetchBlockedException("metadata.internal")).when(webMarkdownService)
				.generateMarkdownFromUrl(any());

		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":\"https://metadata.internal/\"}");

		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertTrue(Strings.CS.contains(responseBody, "webFetchBlocked"));
		// 宛先の解決結果（IP）と、どの禁止範囲だったかはレスポンスへ出さない。
		assertFalse(Strings.CS.contains(responseBody, "169.254"));
	}

	@Test
	@DisplayName("取得先からページを取得できない場合は502を返す")
	void generateMarkdownFromUrlReturnsBadGatewayWhenFetchFails() throws Exception {
		doThrow(new WebFetchException("Webページを取得できませんでした。URLと接続を確認してください。")).when(webMarkdownService)
				.generateMarkdownFromUrl(any());

		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":\"https://example.test/article\"}");

		assertEquals(HttpStatus.BAD_GATEWAY.value(), result.getResponse().getStatus());
		assertTrue(Strings.CS.contains(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
				"webFetchError"));
	}

	@ParameterizedTest
	@DisplayName("アップロードのmodeはコード値のままenumへ変換してServiceへ渡す")
	@CsvSource({ "STRUCTURE, STRUCTURE", "structure, STRUCTURE", "Both, BOTH", "ARTICLE, ARTICLE" })
	void generateMarkdownFromHtmlConvertsMode(String requestMode, String expectedMode) throws Exception {
		doReturn(ResponseEntity.ok(ApiResult.of(new WebMarkdownDraftResponse()))).when(webMarkdownService)
				.generateMarkdownFromHtml(any());

		performRequestWithMode(ACCESS_TOKEN, requestMode);

		ArgumentCaptor<WebMarkdownDraftRequest> formCaptor = ArgumentCaptor.forClass(WebMarkdownDraftRequest.class);
		verify(webMarkdownService, times(1)).generateMarkdownFromHtml(formCaptor.capture());
		assertEquals(WebMarkdownDraftMode.fromKey(expectedMode), formCaptor.getValue().getMode());
	}

	@Test
	@DisplayName("アップロードのmode未指定はnullのままServiceへ渡し、既定の判断をServiceに任せる")
	void generateMarkdownFromHtmlKeepsModeNullWhenNotSpecified() throws Exception {
		doReturn(ResponseEntity.ok(ApiResult.of(new WebMarkdownDraftResponse()))).when(webMarkdownService)
				.generateMarkdownFromHtml(any());

		performRequestWithMode(ACCESS_TOKEN, StringUtils.EMPTY);

		ArgumentCaptor<WebMarkdownDraftRequest> formCaptor = ArgumentCaptor.forClass(WebMarkdownDraftRequest.class);
		verify(webMarkdownService, times(1)).generateMarkdownFromHtml(formCaptor.capture());
		assertNull(formCaptor.getValue().getMode());
	}

	@Test
	@DisplayName("アップロードの未知のmodeは400で拒否し、選べる値を説明する")
	void generateMarkdownFromHtmlReturnsBadRequestWhenModeIsUnknown() throws Exception {
		MvcResult result = performRequestWithMode(ACCESS_TOKEN, "FOO");

		verify(webMarkdownService, never()).generateMarkdownFromHtml(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		assertTrue(Strings.CS.contains(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
				"ARTICLE、STRUCTURE、BOTH"));
	}

	@Test
	@DisplayName("URL取得のmodeはJSONのコード値からenumへ変換してServiceへ渡す")
	void generateMarkdownFromUrlConvertsMode() throws Exception {
		doReturn(ResponseEntity.ok(ApiResult.of(new WebMarkdownDraftResponse()))).when(webMarkdownService)
				.generateMarkdownFromUrl(any());

		performUrlRequest(ACCESS_TOKEN, "{\"url\":\"https://example.test/a\",\"mode\":\"BOTH\"}");

		ArgumentCaptor<WebUrlMarkdownDraftRequest> captor = ArgumentCaptor
				.forClass(WebUrlMarkdownDraftRequest.class);
		verify(webMarkdownService, times(1)).generateMarkdownFromUrl(captor.capture());
		assertEquals(WebMarkdownDraftMode.BOTH, captor.getValue().getMode());
	}

	@Test
	@DisplayName("URL取得の未知のmodeは400で拒否し、Serviceを呼ばない")
	void generateMarkdownFromUrlReturnsBadRequestWhenModeIsUnknown() throws Exception {
		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":\"https://example.test/a\",\"mode\":\"FOO\"}");

		verify(webMarkdownService, never()).generateMarkdownFromUrl(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("URL取得ではJSONが壊れていても500にせず400で返す")
	void generateMarkdownFromUrlReturnsBadRequestWhenJsonIsBroken() throws Exception {
		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":");

		verify(webMarkdownService, never()).generateMarkdownFromUrl(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("URL取得ではURLが上限文字数を超える場合に400を返す")
	void generateMarkdownFromUrlReturnsBadRequestWhenUrlIsTooLong() throws Exception {
		String longUrl = "https://example.test/" + "a".repeat(2000);

		MvcResult result = performUrlRequest(ACCESS_TOKEN, "{\"url\":\"" + longUrl + "\"}");

		verify(webMarkdownService, never()).generateMarkdownFromUrl(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	/**
	 * Webページ取り込みAPIへ、出力モード付きのmultipartリクエストを送信する。
	 *
	 * @param accessToken 送信するaccess-token
	 * @param mode        送信する出力モード
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performRequestWithMode(String accessToken, String mode) throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = multipart(REQUEST_PATH);
		requestBuilder.file(new MockMultipartFile(HTML_FILE_PART_NAME, HTML_FILE_NAME, MediaType.TEXT_HTML_VALUE,
				new byte[1024]));
		return mockMvc.perform(requestBuilder.param("mode", mode).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(session).characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	/**
	 * URL取り込みAPIへJSONリクエストを送信する。
	 *
	 * @param accessToken 送信するaccess-token
	 * @param requestBody 送信するJSON
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performUrlRequest(String accessToken, String requestBody) throws Exception {
		return mockMvc.perform(post(URL_REQUEST_PATH).header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session)
				.contentType(MediaType.APPLICATION_JSON).characterEncoding(CHARACTER_ENCODING_UTF_8)
				.content(requestBody)).andReturn();
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
