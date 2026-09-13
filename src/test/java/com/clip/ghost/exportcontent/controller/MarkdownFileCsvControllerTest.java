package com.clip.ghost.exportcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.exceptions.handler.GlobalExceptionErrorHandler;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.exportcontent.service.MarkdownFileCsvService;

/**
 * {@link MarkdownFileCsvController} のHTTP endpointとtoken検証を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class MarkdownFileCsvControllerTest {
	private static final String REQUEST_PATH = "/markdownFilesCsv";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";

	@Mock
	private MarkdownFileCsvService markdownFileCsvService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		MarkdownFileCsvController controller = new MarkdownFileCsvController(markdownFileCsvService,
				new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲し、CSVを返す")
	void exportMarkdownFileListDelegatesToServiceWhenTokenMatches() throws Exception {
		stubCsvResponse();

		MvcResult result = performRequest(ACCESS_TOKEN, null);

		verify(markdownFileCsvService, times(1)).exportMarkdownFileList(anyBoolean());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("withBom未指定の場合はBOM付きで出力する")
	void exportMarkdownFileListAddsBomByDefault() throws Exception {
		stubCsvResponse();

		performRequest(ACCESS_TOKEN, null);

		// Excelで開いて化けるほうが利用者の困りごとが大きいため、既定はBOM付きにする。
		verify(markdownFileCsvService, times(1)).exportMarkdownFileList(eq(true));
	}

	@Test
	@DisplayName("withBom=falseを指定した場合はBOMを付けずに出力する")
	void exportMarkdownFileListOmitsBomWhenRequested() throws Exception {
		stubCsvResponse();

		performRequest(ACCESS_TOKEN, "false");

		verify(markdownFileCsvService, times(1)).exportMarkdownFileList(eq(false));
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返し、Serviceを呼ばない")
	void exportMarkdownFileListReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(INVALID_ACCESS_TOKEN, null);

		verify(markdownFileCsvService, never()).exportMarkdownFileList(anyBoolean());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	/**
	 * ServiceがCSVレスポンスを返すようにstubする。
	 */
	private void stubCsvResponse() {
		doReturn(ResponseUtils.downloadCsv("markdown-files.csv",
				new ByteArrayResource("\"名前\"\r\n".getBytes(StandardCharsets.UTF_8))))
						.when(markdownFileCsvService).exportMarkdownFileList(anyBoolean());
	}

	/**
	 * CSV出力APIへGETリクエストを送信する。
	 *
	 * @param accessToken 送信するaccess-token
	 * @param withBom     withBomパラメータ。nullの場合は送信しない
	 * @return 実行結果
	 * @throws Exception リクエスト送信に失敗した場合
	 */
	private MvcResult performRequest(String accessToken, String withBom) throws Exception {
		var requestBuilder = get(REQUEST_PATH).header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(session);
		if (withBom != null) {
			requestBuilder.param("withBom", withBom);
		}
		return mockMvc.perform(requestBuilder).andReturn();
	}
}
