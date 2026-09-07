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
import java.util.List;

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
import com.clip.ghost.pdfcontent.dto.PdfThumbnailPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailRequest;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailResponse;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.service.PdfThumbnailService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link PdfThumbnailController} のHTTP endpoint、token検証、validation入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfThumbnailControllerTest {
	private static final String REQUEST_PATH = "/thumbnailsPdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";

	@Mock
	private PdfThumbnailService thumbnailService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		PdfThumbnailController controller = new PdfThumbnailController(thumbnailService, new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler(), new GlobalExceptionErrorHandler()).build();
	}

	@Test
	@DisplayName("token一致時にServiceへ処理を委譲し、共通ラッパーのJSONを返す")
	void generateThumbnailsDelegatesToServiceWhenTokenMatches() throws Exception {
		doReturn(ResponseEntity.ok(ApiResult.of(createResponse()))).when(thumbnailService)
				.generateThumbnails(any(PdfThumbnailRequest.class));

		MvcResult result = performRequest(createOriginalPdfFile(), ACCESS_TOKEN, session);

		verify(thumbnailService, times(1)).generateThumbnails(any(PdfThumbnailRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		JsonNode data = assertApiResultEnvelope(result);
		assertEquals("sample.pdf", data.path("fileName").asString());
		assertEquals(1, data.path("pages").size());
		assertTrue(data.path("pages").path(0).path("dataUri").asString().startsWith(PdfConstants.BASE64_PNG));
	}

	@Test
	@DisplayName("access-tokenがsession tokenと不一致の場合に403を返す")
	void generateThumbnailsReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performRequest(createOriginalPdfFile(), INVALID_ACCESS_TOKEN, session);

		verify(thumbnailService, never()).generateThumbnails(any(PdfThumbnailRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF未指定の場合に400を返す")
	void generateThumbnailsReturnsBadRequestWhenOriginalFileIsMissing() throws Exception {
		MvcResult result = mockMvc.perform(
				multipart(REQUEST_PATH).header(ACCESS_TOKEN_HEADER_NAME, ACCESS_TOKEN).session(session)).andReturn();

		verify(thumbnailService, never()).generateThumbnails(any(PdfThumbnailRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("総ページ数が上限を超えた場合に400とページ数・上限を返す")
	void generateThumbnailsReturnsBadRequestWhenPageLimitExceeded() throws Exception {
		doThrow(new PdfPageLimitExceededException(120, 100)).when(thumbnailService)
				.generateThumbnails(any(PdfThumbnailRequest.class));

		MvcResult result = performRequest(createOriginalPdfFile(), ACCESS_TOKEN, session);

		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		String responseBody = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertTrue(responseBody.contains("pdfPageLimitExceeded"));
		assertTrue(responseBody.contains("120"));
		assertTrue(responseBody.contains("100"));
	}

	@Test
	@DisplayName("PDFサイズが既存上限以上の場合に413を返す")
	void generateThumbnailsReturnsPayloadTooLargeWhenOriginalFileIsTooLarge() throws Exception {
		MvcResult result = performRequest(createMaxSizeOriginalPdfFile(), ACCESS_TOKEN, session);

		verify(thumbnailService, never()).generateThumbnails(any(PdfThumbnailRequest.class));
		assertEquals(HttpStatus.CONTENT_TOO_LARGE.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	/**
	 * PDFを含むmultipart requestを実行する。
	 *
	 * @param originalFile    生成元PDF
	 * @param accessToken     access-token header値
	 * @param requestSession  HTTPセッション
	 * @return HTTP実行結果
	 * @throws Exception MockMvc実行に失敗した場合
	 */
	private MvcResult performRequest(MockMultipartFile originalFile, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH).file(originalFile).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession)).andReturn();
	}

	/**
	 * テスト用のサムネイルレスポンスを生成する。
	 *
	 * @return サムネイルレスポンス
	 */
	private PdfThumbnailResponse createResponse() {
		PdfThumbnailPageResponse page = new PdfThumbnailPageResponse();
		page.setPageNumber(1);
		page.setDataUri(PdfConstants.BASE64_PNG + "iVBORw0KGgo=");
		page.setWidth(331);
		page.setHeight(468);
		PdfThumbnailResponse response = new PdfThumbnailResponse();
		response.setFileName("sample.pdf");
		response.setPageCount(1);
		response.setPages(List.of(page));
		return response;
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
