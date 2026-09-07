package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.service.PdfMarkdownDraftService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link PdfMarkdownDraftController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfMarkdownDraftControllerTest {
	private static final String REQUEST_PATH = "/markdownDraftPdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";

	@Mock
	private PdfMarkdownDraftService markdownDraftService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		PdfMarkdownDraftController controller = new PdfMarkdownDraftController(markdownDraftService,
				new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲しJSONを返す")
	void generateMarkdownDraftDelegatesToServiceWhenTokenMatches() throws Exception {
		MockMultipartFile originalFile = createOriginalPdfFile();
		PdfMarkdownDraftResponse response = new PdfMarkdownDraftResponse();
		response.setFileName("sample.pdf");
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(markdownDraftService)
				.generateMarkdownDraft(any(PdfMarkdownDraftRequest.class));

		MvcResult result = performRequest(originalFile, ACCESS_TOKEN, session);

		verify(markdownDraftService, times(1)).generateMarkdownDraft(any(PdfMarkdownDraftRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		assertApiResultEnvelope(result);
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返す")
	void generateMarkdownDraftReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(createOriginalPdfFile(), INVALID_ACCESS_TOKEN, session);

		verify(markdownDraftService, never()).generateMarkdownDraft(any(PdfMarkdownDraftRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF未指定の場合に400を返す")
	void generateMarkdownDraftReturnsBadRequestWhenOriginalFileIsMissing() throws Exception {
		MvcResult result = performRequestWithoutFile(ACCESS_TOKEN, session);

		verify(markdownDraftService, never()).generateMarkdownDraft(any(PdfMarkdownDraftRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("AUTOで画像変換の対象ページ数が上限を超えた場合に400と対象ページ数・上限を返す")
	void generateMarkdownDraftReturnsBadRequestWhenPageLimitExceeded() throws Exception {
		doThrow(new PdfPageLimitExceededException(30, 20)).when(markdownDraftService)
				.generateMarkdownDraft(any(PdfMarkdownDraftRequest.class));

		MvcResult result = performRequest(createOriginalPdfFile(), ACCESS_TOKEN, session);

		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertTrue(responseBody.contains("pdfPageLimitExceeded"));
		assertTrue(responseBody.contains("30"));
		assertTrue(responseBody.contains("20"));
	}

	@Test
	@DisplayName("PDFサイズが既存上限以上の場合に413を返す")
	void generateMarkdownDraftReturnsPayloadTooLargeWhenOriginalFileIsTooLarge() throws Exception {
		MvcResult result = performRequest(createMaxSizeOriginalPdfFile(), ACCESS_TOKEN, session);

		verify(markdownDraftService, never()).generateMarkdownDraft(any(PdfMarkdownDraftRequest.class));
		assertEquals(HttpStatus.CONTENT_TOO_LARGE.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	/**
	 * PDFを含むmultipart requestを実行する。
	 *
	 * @param originalFile  生成元PDF
	 * @param accessToken   access-token header値
	 * @param requestSession HTTPセッション
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequest(MockMultipartFile originalFile, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH).file(originalFile).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession)).andReturn();
	}

	/**
	 * PDFを含まないmultipart requestを実行する。
	 *
	 * @param accessToken   access-token header値
	 * @param requestSession HTTPセッション
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequestWithoutFile(String accessToken, MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession)).andReturn();
	}

	/**
	 * 通常サイズのテスト用PDFを生成する。
	 *
	 * @return PDF multipartファイル
	 */
	private MockMultipartFile createOriginalPdfFile() {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "sample.pdf", MediaType.APPLICATION_PDF_VALUE,
				new byte[] { 1 });
	}

	/**
	 * 既存PDF APIの上限値と同じサイズを返すテスト用PDFを生成する。
	 *
	 * @return サイズ上限値を返すPDF multipartファイル
	 */
	private MockMultipartFile createMaxSizeOriginalPdfFile() {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "large.pdf", MediaType.APPLICATION_PDF_VALUE,
				new byte[] { 1 }) {
			@Override
			public long getSize() {
				return PdfConstants.MAX_PDF_FILE_SIZE_BYTES;
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
