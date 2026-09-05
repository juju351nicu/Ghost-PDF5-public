package com.clip.ghost.markdowncontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.clip.ghost.common.exceptions.handler.ControllerValidationErrorHandler;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.utils.JsonUtils;
import com.clip.ghost.markdowncontent.dto.MarkdownDeleteResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownDocumentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownFileResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewContentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownSaveRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownUpdateRequest;
import com.clip.ghost.markdowncontent.service.MarkdownDocumentService;

/**
 * {@link MarkdownController} のHTTPエンドポイントとtoken検証入口を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class MarkdownControllerTest {
	private static final String REQUEST_PATH_SAVE_MARKDOWN = "/saveMarkdown";
	private static final String REQUEST_PATH_MARKDOWN_FILES = "/markdownFiles";
	private static final String REQUEST_PATH_MARKDOWN_FILE = "/markdownFile";
	private static final String REQUEST_PATH_MARKDOWN_PREVIEW = "/markdownPreview";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String CHARACTER_ENCODING_UTF_8 = "UTF-8";

	@Mock
	private MarkdownDocumentService markdownDocumentService;

	private MockMvc mockMvc;
	private MockHttpSession session;

	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		MarkdownController controller = new MarkdownController(markdownDocumentService, new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new ControllerValidationErrorHandler()).build();
	}

	@Test
	@DisplayName("Markdown保存ではtoken一致時にServiceへ処理を委譲する")
	void saveMarkdownDelegatesToServiceWhenTokenMatches() throws Exception {
		MarkdownSaveRequest request = createRequest();
		stubSaveMarkdownResponse();

		MvcResult result = performSaveMarkdown(request, ACCESS_TOKEN, session);

		verify(markdownDocumentService, times(1)).saveMarkdown(any(MarkdownSaveRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("Markdown保存ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void saveMarkdownReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MarkdownSaveRequest request = createRequest();

		MvcResult result = performSaveMarkdown(request, INVALID_ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).saveMarkdown(any(MarkdownSaveRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown保存ではfileNameが空白だけの場合に400を返す")
	void saveMarkdownReturnsBadRequestWhenFileNameIsBlank() throws Exception {
		MarkdownSaveRequest request = createRequest();
		request.setFileName("   ");

		MvcResult result = performSaveMarkdown(request, ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).saveMarkdown(any(MarkdownSaveRequest.class));
		assertValidationError(result, "fileName");
	}

	@Test
	@DisplayName("Markdown保存ではfileNameが上限を超えた場合に400を返す")
	void saveMarkdownReturnsBadRequestWhenFileNameIsTooLong() throws Exception {
		MarkdownSaveRequest request = createRequest();
		request.setFileName("a".repeat(256));

		MvcResult result = performSaveMarkdown(request, ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).saveMarkdown(any(MarkdownSaveRequest.class));
		assertValidationError(result, "fileName");
	}

	@Test
	@DisplayName("Markdown一覧取得ではtoken一致時にServiceへ処理を委譲する")
	void listMarkdownFilesDelegatesToServiceWhenTokenMatches() throws Exception {
		stubListMarkdownFilesResponse();

		MvcResult result = performListMarkdownFiles(ACCESS_TOKEN, session);

		verify(markdownDocumentService, times(1)).listMarkdownFiles();
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("Markdown一覧取得ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void listMarkdownFilesReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performListMarkdownFiles(INVALID_ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).listMarkdownFiles();
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown本文取得ではtoken一致時にServiceへ処理を委譲する")
	void getMarkdownFileDelegatesToServiceWhenTokenMatches() throws Exception {
		stubGetMarkdownFileResponse();

		MvcResult result = performGetMarkdownFile(ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, times(1)).getMarkdownFile("design-note.md");
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("Markdown本文取得ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void getMarkdownFileReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performGetMarkdownFile(INVALID_ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, never()).getMarkdownFile(any(String.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown本文取得ではfileName未指定の場合に400を返す")
	void getMarkdownFileReturnsBadRequestWhenFileNameIsMissing() throws Exception {
		MvcResult result = performGetMarkdownFileWithoutFileName(ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).getMarkdownFile(any(String.class));
		assertBadRequest(result);
	}

	@Test
	@DisplayName("Markdownプレビュー取得ではtoken一致時にServiceへ処理を委譲する")
	void previewMarkdownFileDelegatesToServiceWhenTokenMatches() throws Exception {
		stubPreviewMarkdownFileResponse();

		MvcResult result = performPreviewMarkdownFile(ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, times(1)).previewMarkdownFile("design-note.md");
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("Markdownプレビュー取得ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void previewMarkdownFileReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performPreviewMarkdownFile(INVALID_ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, never()).previewMarkdownFile(any(String.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdownプレビュー取得ではfileName未指定の場合に400を返す")
	void previewMarkdownFileReturnsBadRequestWhenFileNameIsMissing() throws Exception {
		MvcResult result = performPreviewMarkdownFileWithoutFileName(ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).previewMarkdownFile(any(String.class));
		assertBadRequest(result);
	}

	@Test
	@DisplayName("Markdown本文プレビューではtoken一致時にServiceへ処理を委譲する")
	void previewMarkdownContentDelegatesToServiceWhenTokenMatches() throws Exception {
		MarkdownPreviewRequest request = createPreviewRequest();
		stubPreviewMarkdownContentResponse();

		MvcResult result = performPreviewMarkdownContent(request, ACCESS_TOKEN, session);

		verify(markdownDocumentService, times(1)).previewMarkdownContent(any(MarkdownPreviewRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("Markdown本文プレビューではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void previewMarkdownContentReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MarkdownPreviewRequest request = createPreviewRequest();

		MvcResult result = performPreviewMarkdownContent(request, INVALID_ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).previewMarkdownContent(any(MarkdownPreviewRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown本文プレビューではcontent未指定の場合に400を返す")
	void previewMarkdownContentReturnsBadRequestWhenContentIsMissing() throws Exception {
		MarkdownPreviewRequest request = createPreviewRequest();
		request.setContent(null);

		MvcResult result = performPreviewMarkdownContent(request, ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).previewMarkdownContent(any(MarkdownPreviewRequest.class));
		assertValidationError(result, "content");
	}

	@Test
	@DisplayName("Markdown更新ではtoken一致時にServiceへ処理を委譲する")
	void updateMarkdownFileDelegatesToServiceWhenTokenMatches() throws Exception {
		MarkdownUpdateRequest request = createUpdateRequest();
		stubUpdateMarkdownFileResponse();

		MvcResult result = performUpdateMarkdownFile(request, ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, times(1)).updateMarkdownFile(eq("design-note.md"),
				any(MarkdownUpdateRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("Markdown更新ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void updateMarkdownFileReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MarkdownUpdateRequest request = createUpdateRequest();

		MvcResult result = performUpdateMarkdownFile(request, INVALID_ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, never()).updateMarkdownFile(any(String.class),
				any(MarkdownUpdateRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown更新ではcontent未指定の場合に400を返す")
	void updateMarkdownFileReturnsBadRequestWhenContentIsMissing() throws Exception {
		MarkdownUpdateRequest request = createUpdateRequest();
		request.setContent(null);

		MvcResult result = performUpdateMarkdownFile(request, ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, never()).updateMarkdownFile(any(String.class),
				any(MarkdownUpdateRequest.class));
		assertValidationError(result, "content");
	}

	@Test
	@DisplayName("Markdown更新ではfileName未指定の場合に400を返す")
	void updateMarkdownFileReturnsBadRequestWhenFileNameIsMissing() throws Exception {
		MarkdownUpdateRequest request = createUpdateRequest();

		MvcResult result = performUpdateMarkdownFileWithoutFileName(request, ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).updateMarkdownFile(any(String.class),
				any(MarkdownUpdateRequest.class));
		assertBadRequest(result);
	}

	@Test
	@DisplayName("Markdown削除ではtoken一致時にServiceへ処理を委譲する")
	void deleteMarkdownFileDelegatesToServiceWhenTokenMatches() throws Exception {
		stubDeleteMarkdownFileResponse();

		MvcResult result = performDeleteMarkdownFile(ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, times(1)).deleteMarkdownFile("design-note.md");
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
	}

	@Test
	@DisplayName("Markdown削除ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void deleteMarkdownFileReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		MvcResult result = performDeleteMarkdownFile(INVALID_ACCESS_TOKEN, session, "design-note.md");

		verify(markdownDocumentService, never()).deleteMarkdownFile(any(String.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("Markdown削除ではfileName未指定の場合に400を返す")
	void deleteMarkdownFileReturnsBadRequestWhenFileNameIsMissing() throws Exception {
		MvcResult result = performDeleteMarkdownFileWithoutFileName(ACCESS_TOKEN, session);

		verify(markdownDocumentService, never()).deleteMarkdownFile(any(String.class));
		assertBadRequest(result);
	}

	private void stubSaveMarkdownResponse() {
		MarkdownFileResponse response = new MarkdownFileResponse();
		response.setFileName("design-note.md");
		response.setByteSize(123L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:45:00");
		doReturn(ResponseEntity.ok(response)).when(markdownDocumentService)
				.saveMarkdown(any(MarkdownSaveRequest.class));
	}

	private void stubGetMarkdownFileResponse() {
		MarkdownDocumentResponse response = new MarkdownDocumentResponse();
		response.setFileName("design-note.md");
		response.setByteSize(123L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:45:00");
		response.setContent("# Title");
		doReturn(ResponseEntity.ok(response)).when(markdownDocumentService).getMarkdownFile("design-note.md");
	}

	private void stubPreviewMarkdownFileResponse() {
		MarkdownPreviewResponse response = new MarkdownPreviewResponse();
		response.setFileName("design-note.md");
		response.setByteSize(123L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:45:00");
		response.setHtml("<h1>Title</h1>");
		doReturn(ResponseEntity.ok(response)).when(markdownDocumentService).previewMarkdownFile("design-note.md");
	}

	private void stubPreviewMarkdownContentResponse() {
		MarkdownPreviewContentResponse response = new MarkdownPreviewContentResponse();
		response.setHtml("<h1>Title</h1>");
		doReturn(ResponseEntity.ok(response)).when(markdownDocumentService)
				.previewMarkdownContent(any(MarkdownPreviewRequest.class));
	}

	private void stubUpdateMarkdownFileResponse() {
		MarkdownFileResponse response = new MarkdownFileResponse();
		response.setFileName("design-note.md");
		response.setByteSize(321L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:50:00");
		doReturn(ResponseEntity.ok(response)).when(markdownDocumentService).updateMarkdownFile(eq("design-note.md"),
				any(MarkdownUpdateRequest.class));
	}

	private void stubDeleteMarkdownFileResponse() {
		MarkdownDeleteResponse response = new MarkdownDeleteResponse();
		response.setFileName("design-note.md");
		response.setDeleted(Boolean.TRUE);
		doReturn(ResponseEntity.ok(response)).when(markdownDocumentService).deleteMarkdownFile("design-note.md");
	}

	private void stubListMarkdownFilesResponse() {
		MarkdownFileResponse response = new MarkdownFileResponse();
		response.setFileName("design-note.md");
		response.setByteSize(123L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:45:00");
		doReturn(ResponseEntity.ok(List.of(response))).when(markdownDocumentService).listMarkdownFiles();
	}

	private MvcResult performSaveMarkdown(MarkdownSaveRequest request, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc
				.perform(post(REQUEST_PATH_SAVE_MARKDOWN).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).contentType(MediaType.APPLICATION_JSON)
						.characterEncoding(CHARACTER_ENCODING_UTF_8).content(JsonUtils.toJsonOrThrow(request)))
				.andReturn();
	}

	private MvcResult performGetMarkdownFile(String accessToken, MockHttpSession requestSession, String fileName)
			throws Exception {
		return mockMvc.perform(get(REQUEST_PATH_MARKDOWN_FILE).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).param("fileName", fileName).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performGetMarkdownFileWithoutFileName(String accessToken, MockHttpSession requestSession)
			throws Exception {
		return mockMvc.perform(get(REQUEST_PATH_MARKDOWN_FILE).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	private MvcResult performUpdateMarkdownFile(MarkdownUpdateRequest request, String accessToken,
			MockHttpSession requestSession, String fileName) throws Exception {
		return mockMvc
				.perform(put(REQUEST_PATH_MARKDOWN_FILE).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).param("fileName", fileName).contentType(MediaType.APPLICATION_JSON)
						.characterEncoding(CHARACTER_ENCODING_UTF_8).content(JsonUtils.toJsonOrThrow(request)))
				.andReturn();
	}

	private MvcResult performUpdateMarkdownFileWithoutFileName(MarkdownUpdateRequest request, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc
				.perform(put(REQUEST_PATH_MARKDOWN_FILE).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).contentType(MediaType.APPLICATION_JSON)
						.characterEncoding(CHARACTER_ENCODING_UTF_8).content(JsonUtils.toJsonOrThrow(request)))
				.andReturn();
	}

	private MvcResult performDeleteMarkdownFile(String accessToken, MockHttpSession requestSession, String fileName)
			throws Exception {
		return mockMvc.perform(delete(REQUEST_PATH_MARKDOWN_FILE).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).param("fileName", fileName).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performDeleteMarkdownFileWithoutFileName(String accessToken, MockHttpSession requestSession)
			throws Exception {
		return mockMvc.perform(delete(REQUEST_PATH_MARKDOWN_FILE).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	private MvcResult performPreviewMarkdownFile(String accessToken, MockHttpSession requestSession, String fileName)
			throws Exception {
		return mockMvc.perform(get(REQUEST_PATH_MARKDOWN_PREVIEW).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).param("fileName", fileName).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performPreviewMarkdownFileWithoutFileName(String accessToken, MockHttpSession requestSession)
			throws Exception {
		return mockMvc.perform(get(REQUEST_PATH_MARKDOWN_PREVIEW).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	private MvcResult performPreviewMarkdownContent(MarkdownPreviewRequest request, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc
				.perform(post(REQUEST_PATH_MARKDOWN_PREVIEW).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).contentType(MediaType.APPLICATION_JSON)
						.characterEncoding(CHARACTER_ENCODING_UTF_8).content(JsonUtils.toJsonOrThrow(request)))
				.andReturn();
	}

	private MvcResult performListMarkdownFiles(String accessToken, MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(get(REQUEST_PATH_MARKDOWN_FILES).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	private void assertValidationError(MvcResult result, String fieldName) throws Exception {
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
		String responseBody = result.getResponse().getContentAsString();
		assertTrue(responseBody.contains("\"fieldErrors\""));
		assertTrue(responseBody.contains("\"field\":\"" + fieldName + "\""));
	}

	private void assertBadRequest(MvcResult result) {
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	private MarkdownSaveRequest createRequest() {
		MarkdownSaveRequest request = new MarkdownSaveRequest();
		request.setFileName("design-note.md");
		request.setContent("# Title");
		return request;
	}

	private MarkdownUpdateRequest createUpdateRequest() {
		MarkdownUpdateRequest request = new MarkdownUpdateRequest();
		request.setContent("# Updated");
		return request;
	}

	private MarkdownPreviewRequest createPreviewRequest() {
		MarkdownPreviewRequest request = new MarkdownPreviewRequest();
		request.setContent("# Title");
		return request;
	}
}
