package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartException;

import jakarta.servlet.http.Cookie;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.InsertPdfRequest;
import com.clip.ghost.pdfcontent.dto.OriginalPdfRequest;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.service.GhostPdfService;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.common.utils.JsonUtils;
import com.clip.ghost.common.utils.ResponseUtils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link GhostPdfController} のHTTPエンドポイント、token検証、validation入口を検証するテスト。
 * <p>
 * Controller単体のMockMvcで、Serviceへ到達する条件と到達しない異常系を固定する。
 */
@ExtendWith(MockitoExtension.class)
class GhostPdfControllerTest {
	private static final String REQUEST_PATH_MAIN = "/";
	private static final String REQUEST_PATH_SHOW_PDF = "/showPdf";
	private static final String REQUEST_PATH_METADATA_PDF = "/metadataPdf";
	private static final String REQUEST_PATH_TEXT_PDF = "/textPdf";
	private static final String REQUEST_PATH_EXTRACT_PDF = "/extractPdf";
	private static final String REQUEST_PATH_MERGE_PDF = "/mergePdf";
	private static final String REQUEST_PATH_SPLIT_PDF = "/splitPdf";
	private static final String REQUEST_PATH_DELETE_PDF = "/deletePdf";
	private static final String REQUEST_PATH_INSERT_PDF = "/insertPdf";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";
	private static final String TOKEN_COOKIE_NAME = "token";
	private static final String SESSION_TOKEN_ATTRIBUTE = "token";
	private static final String COOKIE_PATH_ROOT = "/";
	private static final String CHARACTER_ENCODING_UTF_8 = "UTF-8";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";
	private static final String MERGE_FILES_PART_NAME = "mergeFiles";
	private static final String INSERT_FILE_PART_NAME_0 = "insertPdfForm[0].insertFile";
	private static final String ORIGINAL_DELETE_PAGES_PARAM_NAME = "originalDeletePages";
	private static final String EXTRACT_PAGES_PARAM_NAME = "extractPages";
	private static final String INSERT_PAGE_PARAM_NAME_0 = "insertPdfForm[0].insertPage";
	private static final String INSERT_OPTION_PARAM_NAME_0 = "insertPdfForm[0].insertOption";
	private static final int COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

	private GhostPdfController controller;

	@Mock
	private GhostPdfService pdfService;

	private MockMvc mockMvc;

	private MockHttpSession session;

	private Validator validator;

	/**
	 * MockMvcにテスト対象のコントローラーをセットする。
	 */
	@BeforeEach
	void setup() {
		session = new MockHttpSession();
		session.setAttribute(SESSION_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
		controller = new GhostPdfController(pdfService, new AccessTokenValidator());
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
		ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@Test
	@DisplayName("トップ画面表示ではtoken cookieとsession tokenを発行する")
	void showMainPageIssuesTokenCookieAndSessionToken() throws Exception {
		MvcResult result = sendGetRequest(REQUEST_PATH_MAIN);
		Cookie tokenCookie = result.getResponse().getCookie(TOKEN_COOKIE_NAME);
		String sessionToken = (String) result.getRequest().getSession().getAttribute(SESSION_TOKEN_ATTRIBUTE);

		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals("main", result.getResponse().getForwardedUrl());
		assertNotNull(tokenCookie);
		assertNotNull(sessionToken);
		assertEquals(sessionToken, tokenCookie.getValue());
		assertEquals(COOKIE_MAX_AGE_SECONDS, tokenCookie.getMaxAge());
		assertEquals(COOKIE_PATH_ROOT, tokenCookie.getPath());
		assertFalse(tokenCookie.getSecure());
	}

	@Test
	@DisplayName("PDFプレビューではtoken一致時にServiceへ処理を委譲する")
	void showPdfPreviewDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubShowPdfResponse(contents);

		MvcResult result = performShowPdf(mockPdfFile, ACCESS_TOKEN, session);

		verify(pdfService, times(1)).showPdf(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFプレビューでは編集元PDF未指定の場合に400を返す")
	void showPdfPreviewReturnsBadRequestWhenOriginalFileIsNull() throws Exception {
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(null);
		MvcResult result = sendPostRequest(REQUEST_PATH_SHOW_PDF, form);
		Set<ConstraintViolation<OriginalPdfRequest>> violations = validator.validate(form);

		verify(pdfService, never()).showPdf(any(OriginalPdfRequest.class));
		assertEquals(1, violations.size());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFプレビューでは編集元PDFのサイズ超過時にMultipartExceptionを投げる")
	void showPdfPreviewThrowsMultipartExceptionWhenOriginalFileIsTooLarge() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createMaxSizeOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);

		verify(pdfService, never()).showPdf(any(OriginalPdfRequest.class));
		assertThrows(MultipartException.class, () -> controller.showPdfPreview(ACCESS_TOKEN, form, session));
	}

	@Test
	@DisplayName("PDFテキスト抽出では編集元PDFのサイズ超過時にMultipartExceptionを投げる")
	void extractPdfTextThrowsMultipartExceptionWhenOriginalFileIsTooLarge() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createMaxSizeOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);

		assertThrows(MultipartException.class, () -> controller.extractPdfText(ACCESS_TOKEN, form, session));
		verify(pdfService, never()).extractPdfText(any(OriginalPdfRequest.class));
	}

	@Test
	@DisplayName("PDFページ削除では編集元PDFのサイズ超過時にMultipartExceptionを投げる")
	void deletePdfPagesThrowsMultipartExceptionWhenOriginalFileIsTooLarge() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createMaxSizeOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		form.setOriginalDeletePages(List.of(1));

		assertThrows(MultipartException.class, () -> controller.deletePdfPages(ACCESS_TOKEN, form, session));
		verify(pdfService, never()).deletePdfByPages(any(OriginalPdfRequest.class));
	}

	@Test
	@DisplayName("PDF挿入では編集元PDFのサイズ超過時にMultipartExceptionを投げる")
	void insertPdfFilesThrowsMultipartExceptionWhenOriginalFileIsTooLarge() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createMaxSizeOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		form.setInsertPdfForm(List.of());

		assertThrows(MultipartException.class, () -> controller.insertPdfFiles(ACCESS_TOKEN, form, session));
		verify(pdfService, never()).insertPdfs(any(OriginalPdfRequest.class));
	}

	@Test
	@DisplayName("PDF挿入では差し込みPDFのサイズ超過時にMultipartExceptionを投げる")
	void insertPdfFilesThrowsMultipartExceptionWhenInsertFileIsTooLarge() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createMaxSizeInsertPdfFile(contents);
		InsertPdfRequest insertForm = new InsertPdfRequest();
		insertForm.setInsertFile(insertPdfFile);
		insertForm.setInsertPage(1);
		insertForm.setInsertOption(1);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		form.setInsertPdfForm(List.of(insertForm));

		assertThrows(MultipartException.class, () -> controller.insertPdfFiles(ACCESS_TOKEN, form, session));
		verify(pdfService, never()).insertPdfs(any(OriginalPdfRequest.class));
	}

	@Test
	@DisplayName("PDFメタデータ取得ではtoken一致時にServiceへ処理を委譲する")
	void getPdfMetadataDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubMetadataResponse();

		MvcResult result = performMetadataPdf(mockPdfFile, ACCESS_TOKEN, session);

		verify(pdfService, times(1)).getPdfMetadata(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		assertApiResultEnvelope(result);
	}

	@Test
	@DisplayName("PDFメタデータ取得ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void getPdfMetadataReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performMetadataPdf(mockPdfFile, INVALID_ACCESS_TOKEN, session);

		verify(pdfService, never()).getPdfMetadata(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFテキスト抽出ではtoken一致時にServiceへ処理を委譲する")
	void extractPdfTextDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubTextResponse();

		MvcResult result = performTextPdf(mockPdfFile, ACCESS_TOKEN, session);

		verify(pdfService, times(1)).extractPdfText(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(MediaType.APPLICATION_JSON_VALUE, result.getResponse().getContentType());
		assertApiResultEnvelope(result);
	}

	@Test
	@DisplayName("PDFテキスト抽出ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void extractPdfTextReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performTextPdf(mockPdfFile, INVALID_ACCESS_TOKEN, session);

		verify(pdfService, never()).extractPdfText(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFページ抽出ではtoken一致時にServiceへ処理を委譲する")
	void extractPdfPagesDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubExtractPdfResponse(contents);

		MvcResult result = performExtractPdf(mockPdfFile, ACCESS_TOKEN, session, "1");

		verify(pdfService, times(1)).extractPdfByPages(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFページ抽出では抽出ページ番号が0の場合に400を返す")
	void extractPdfPagesReturnsBadRequestWhenExtractPageIsZero() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performExtractPdf(mockPdfFile, ACCESS_TOKEN, session, "0");

		verify(pdfService, never()).extractPdfByPages(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFページ抽出ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void extractPdfPagesReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performExtractPdf(mockPdfFile, INVALID_ACCESS_TOKEN, session, "1");

		verify(pdfService, never()).extractPdfByPages(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF結合ではtoken一致時にServiceへ処理を委譲する")
	void mergePdfFilesDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile firstFile = createMergePdfFile("first.pdf", contents);
		MockMultipartFile secondFile = createMergePdfFile("second.pdf", contents);

		stubMergePdfResponse(contents);

		MvcResult result = performMergePdf(firstFile, secondFile, ACCESS_TOKEN, session);

		verify(pdfService, times(1)).mergePdfs(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF結合では結合対象PDF未指定の場合に400を返す")
	void mergePdfFilesReturnsBadRequestWhenMergeFilesAreMissing() throws Exception {
		MvcResult result = performMergePdfWithoutFiles(ACCESS_TOKEN, session);

		verify(pdfService, never()).mergePdfs(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF結合ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void mergePdfFilesReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile firstFile = createMergePdfFile("first.pdf", contents);
		MockMultipartFile secondFile = createMergePdfFile("second.pdf", contents);

		MvcResult result = performMergePdf(firstFile, secondFile, INVALID_ACCESS_TOKEN, session);

		verify(pdfService, never()).mergePdfs(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF分割ではtoken一致時にServiceへ処理を委譲する")
	void splitPdfDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubSplitPdfResponse(contents);

		MvcResult result = performSplitPdf(mockPdfFile, ACCESS_TOKEN, session);

		verify(pdfService, times(1)).splitPdf(any());
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals("application/zip", result.getResponse().getContentType());
	}

	@Test
	@DisplayName("PDF分割では分割対象PDF未指定の場合に400を返す")
	void splitPdfReturnsBadRequestWhenOriginalFileIsMissing() throws Exception {
		MvcResult result = performSplitPdfWithoutFile(ACCESS_TOKEN, session);

		verify(pdfService, never()).splitPdf(any());
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF分割ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void splitPdfReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performSplitPdf(mockPdfFile, INVALID_ACCESS_TOKEN, session);

		verify(pdfService, never()).splitPdf(any());
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFページ削除ではtoken一致時にServiceへ処理を委譲する")
	void deletePdfPagesDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubDeletePdfResponse(contents);

		MvcResult result = performDeletePdf(mockPdfFile, ACCESS_TOKEN, session, "1");

		verify(pdfService, times(1)).deletePdfByPages(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFページ削除では削除ページ番号が0の場合に400を返す")
	void deletePdfPagesReturnsBadRequestWhenDeletePageIsZero() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performDeletePdf(mockPdfFile, ACCESS_TOKEN, session, "0");

		verify(pdfService, never()).deletePdfByPages(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF挿入ではtoken一致時にServiceへ処理を委譲する")
	void insertPdfFilesDelegatesToServiceWhenTokenMatches() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile mockPdfFile2 = createInsertPdfFile(contents);
		stubInsertPdfResponse(contents);

		MvcResult result = performInsertPdf(mockPdfFile, mockPdfFile2, ACCESS_TOKEN, session, "1", "1", "1");

		verify(pdfService, times(1)).insertPdfs(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF挿入では10ページ以上の差し込みページ番号を許可する")
	void insertPdfFilesAcceptsTwoDigitInsertPage() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);

		stubInsertPdfResponse(contents);

		MvcResult result = performInsertPdf(mockPdfFile, insertPdfFile, ACCESS_TOKEN, session, "1", "10", "1");

		verify(pdfService, times(1)).insertPdfs(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFプレビューではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void showPdfPreviewReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performShowPdf(mockPdfFile, INVALID_ACCESS_TOKEN, session);

		verify(pdfService, never()).showPdf(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFプレビューではsession token未設定の場合に403を返す")
	void showPdfPreviewReturnsForbiddenWhenSessionTokenIsMissing() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockHttpSession noTokenSession = new MockHttpSession();

		MvcResult result = performShowPdf(mockPdfFile, ACCESS_TOKEN, noTokenSession);

		verify(pdfService, never()).showPdf(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDFページ削除ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void deletePdfPagesReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		MvcResult result = performDeletePdf(mockPdfFile, INVALID_ACCESS_TOKEN, session, "1");

		verify(pdfService, never()).deletePdfByPages(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF挿入ではaccess-tokenがsession tokenと不一致の場合に403を返す")
	void insertPdfFilesReturnsForbiddenWhenAccessTokenDoesNotMatch() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);

		MvcResult result = performInsertPdf(mockPdfFile, insertPdfFile, INVALID_ACCESS_TOKEN, session, "1", "1", "1");

		verify(pdfService, never()).insertPdfs(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF挿入では差し込みページ番号が数値でない場合に400を返す")
	void insertPdfFilesReturnsBadRequestWhenInsertPageIsNotNumeric() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);

		MvcResult result = performInsertPdf(mockPdfFile, insertPdfFile, ACCESS_TOKEN, session, "1", "abc", "1");

		verify(pdfService, never()).insertPdfs(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF挿入では差し込みページ番号がInteger範囲外の場合に400を返す")
	void insertPdfFilesReturnsBadRequestWhenInsertPageIsOutOfIntegerRange() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);

		MvcResult result = performInsertPdf(mockPdfFile, insertPdfFile, ACCESS_TOKEN, session, "1", "12345678911", "1");

		verify(pdfService, never()).insertPdfs(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF挿入では差し込みオプションが数値でない場合に400を返す")
	void insertPdfFilesReturnsBadRequestWhenInsertOptionIsNotNumeric() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);

		MvcResult result = performInsertPdf(mockPdfFile, insertPdfFile, ACCESS_TOKEN, session, "1", "1", "abc");

		verify(pdfService, never()).insertPdfs(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	@Test
	@DisplayName("PDF挿入では差し込みオプションがInteger範囲外の場合に400を返す")
	void insertPdfFilesReturnsBadRequestWhenInsertOptionIsOutOfIntegerRange() throws Exception {
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);

		MvcResult result = performInsertPdf(mockPdfFile, insertPdfFile, ACCESS_TOKEN, session, "1", "1", "12345678911");

		verify(pdfService, never()).insertPdfs(any(OriginalPdfRequest.class));
		assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
	}

	/**
	 * PDFプレビューServiceの正常レスポンスをmockする。
	 *
	 * @param contents PDFレスポンス本文
	 */
	private void stubShowPdfResponse(byte[] contents) {
		doReturn(ResponseUtils.getResponseBytes(contents)).when(pdfService).showPdf(any(OriginalPdfRequest.class));
	}

	private void stubMetadataResponse() {
		PdfMetadataResponse response = new PdfMetadataResponse();
		response.setFileName("sample.pdf");
		response.setFileSize(123L);
		response.setPageCount(1);
		response.setEncrypted(false);
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(pdfService)
				.getPdfMetadata(any(OriginalPdfRequest.class));
	}

	private void stubTextResponse() {
		PdfTextResponse response = new PdfTextResponse();
		response.setFileName("sample.pdf");
		response.setFileSize(123L);
		response.setPageCount(1);
		response.setText("sample text");
		doReturn(ResponseEntity.ok(ApiResult.of(response))).when(pdfService)
				.extractPdfText(any(OriginalPdfRequest.class));
	}

	private void stubExtractPdfResponse(byte[] contents) {
		doReturn(ResponseUtils.getResponseBytes(contents)).when(pdfService).extractPdfByPages(any());
	}

	private void stubMergePdfResponse(byte[] contents) {
		doReturn(ResponseUtils.getResponseBytes(contents)).when(pdfService).mergePdfs(any());
	}

	private void stubSplitPdfResponse(byte[] contents) {
		doReturn(ResponseUtils.downloadZip("split.zip", contents)).when(pdfService).splitPdf(any());
	}

	/**
	 * PDFページ削除Serviceの正常レスポンスをmockする。
	 *
	 * @param contents PDFレスポンス本文
	 */
	private void stubDeletePdfResponse(byte[] contents) {
		doReturn(ResponseUtils.getResponseBytes(contents)).when(pdfService)
				.deletePdfByPages(any(OriginalPdfRequest.class));
	}

	/**
	 * PDF挿入Serviceの正常レスポンスをmockする。
	 *
	 * @param contents PDFレスポンス本文
	 */
	private void stubInsertPdfResponse(byte[] contents) {
		doReturn(ResponseUtils.getResponseBytes(contents)).when(pdfService).insertPdfs(any(OriginalPdfRequest.class));
	}

	/**
	 * PDFプレビューのmultipartリクエストを送信する。
	 *
	 * @param originalFile   編集元PDF
	 * @param accessToken    access-tokenヘッダー値
	 * @param requestSession リクエストに設定するsession
	 * @return リクエスト結果
	 * @throws Exception 例外
	 */
	private MvcResult performShowPdf(MockMultipartFile originalFile, String accessToken, MockHttpSession requestSession)
			throws Exception {
		return mockMvc.perform(
				multipart(REQUEST_PATH_SHOW_PDF).file(originalFile).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performMetadataPdf(MockMultipartFile originalFile, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(
				multipart(REQUEST_PATH_METADATA_PDF).file(originalFile).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performTextPdf(MockMultipartFile originalFile, String accessToken, MockHttpSession requestSession)
			throws Exception {
		return mockMvc.perform(
				multipart(REQUEST_PATH_TEXT_PDF).file(originalFile).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performExtractPdf(MockMultipartFile originalFile, String accessToken,
			MockHttpSession requestSession, String extractPages) throws Exception {
		return mockMvc
				.perform(multipart(REQUEST_PATH_EXTRACT_PDF).file(originalFile)
						.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(requestSession)
						.param(EXTRACT_PAGES_PARAM_NAME, extractPages).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performMergePdf(MockMultipartFile firstFile, MockMultipartFile secondFile, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH_MERGE_PDF).file(firstFile).file(secondFile)
				.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(requestSession)
				.characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	private MvcResult performMergePdfWithoutFiles(String accessToken, MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH_MERGE_PDF).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	private MvcResult performSplitPdf(MockMultipartFile originalFile, String accessToken,
			MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(
				multipart(REQUEST_PATH_SPLIT_PDF).file(originalFile).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
						.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	private MvcResult performSplitPdfWithoutFile(String accessToken, MockHttpSession requestSession) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH_SPLIT_PDF).header(ACCESS_TOKEN_HEADER_NAME, accessToken)
				.session(requestSession).characterEncoding(CHARACTER_ENCODING_UTF_8)).andReturn();
	}

	/**
	 * PDFページ削除のmultipartリクエストを送信する。
	 *
	 * @param originalFile   編集元PDF
	 * @param accessToken    access-tokenヘッダー値
	 * @param requestSession リクエストに設定するsession
	 * @param deletePages    削除ページ指定
	 * @return リクエスト結果
	 * @throws Exception 例外
	 */
	private MvcResult performDeletePdf(MockMultipartFile originalFile, String accessToken,
			MockHttpSession requestSession, String deletePages) throws Exception {
		return mockMvc.perform(multipart(REQUEST_PATH_DELETE_PDF).file(originalFile)
				.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(requestSession)
				.param(ORIGINAL_DELETE_PAGES_PARAM_NAME, deletePages).characterEncoding(CHARACTER_ENCODING_UTF_8))
				.andReturn();
	}

	/**
	 * PDF挿入のmultipartリクエストを送信する。
	 *
	 * @param originalFile   編集元PDF
	 * @param insertFile     差し込みPDF
	 * @param accessToken    access-tokenヘッダー値
	 * @param requestSession リクエストに設定するsession
	 * @param deletePages    削除ページ指定
	 * @param insertPage     差し込みページ指定
	 * @param insertOption   差し込み種別
	 * @return リクエスト結果
	 * @throws Exception 例外
	 */
	private MvcResult performInsertPdf(MockMultipartFile originalFile, MockMultipartFile insertFile, String accessToken,
			MockHttpSession requestSession, String deletePages, String insertPage, String insertOption)
			throws Exception {
		MockMultipartHttpServletRequestBuilder requestBuilder = (MockMultipartHttpServletRequestBuilder) multipart(
				REQUEST_PATH_INSERT_PDF).file(originalFile).file(insertFile)
				.header(ACCESS_TOKEN_HEADER_NAME, accessToken).session(requestSession)
				.param(ORIGINAL_DELETE_PAGES_PARAM_NAME, deletePages).param(INSERT_PAGE_PARAM_NAME_0, insertPage)
				.param(INSERT_OPTION_PARAM_NAME_0, insertOption).characterEncoding(CHARACTER_ENCODING_UTF_8);
		return mockMvc.perform(requestBuilder).andReturn();
	}

	/**
	 * MockMvcでGETリクエストを行う。
	 *
	 * @param url URL
	 * @return リクエスト結果
	 * @throws Exception 例外
	 */
	private MvcResult sendGetRequest(String url) throws Exception {
		return mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON_VALUE)).andReturn();
	}

	/**
	 * MockMvcでJSON body付きPOSTリクエストを行う。
	 *
	 * @param url  URL
	 * @param data JSON化して送信するリクエストデータ
	 * @return リクエスト結果
	 * @throws Exception 例外
	 */
	private MvcResult sendPostRequest(String url, OriginalPdfRequest data) throws Exception {
		return mockMvc
				.perform(post(url).content(JsonUtils.toJsonOrThrow(data)).contentType(MediaType.APPLICATION_JSON_VALUE))
				.andReturn();
	}

	/**
	 * 編集元PDFとして送信するテスト用MultipartFileを作成する。
	 *
	 * @param contents テストPDFの内容
	 * @return 編集元PDFのMultipartFile
	 */
	private MockMultipartFile createOriginalPdfFile(byte[] contents) {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "", MediaType.APPLICATION_PDF_VALUE, contents);
	}

	private MockMultipartFile createMergePdfFile(String fileName, byte[] contents) {
		return new MockMultipartFile(MERGE_FILES_PART_NAME, fileName, MediaType.APPLICATION_PDF_VALUE, contents);
	}

	/**
	 * 差し込みPDFとして送信するテスト用MultipartFileを作成する。
	 *
	 * @param contents テストPDFの内容
	 * @return 差し込みPDFのMultipartFile
	 */
	private MockMultipartFile createInsertPdfFile(byte[] contents) {
		return new MockMultipartFile(INSERT_FILE_PART_NAME_0, "", MediaType.APPLICATION_PDF_VALUE, contents);
	}

	/**
	 * ファイルサイズ上限チェック用の編集元PDFを作成する。
	 *
	 * @param contents テストPDFの内容
	 * @return サイズ上限値を返す編集元PDFのMultipartFile
	 */
	private MockMultipartFile createMaxSizeOriginalPdfFile(byte[] contents) {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "", MediaType.APPLICATION_PDF_VALUE, contents) {
			@Override
			public long getSize() {
				return PdfConstants.MAX_PDF_FILE_SIZE_BYTES;
			}
		};
	}

	private MockMultipartFile createMaxSizeInsertPdfFile(byte[] contents) {
		return new MockMultipartFile(INSERT_FILE_PART_NAME_0, "", MediaType.APPLICATION_PDF_VALUE, contents) {
			@Override
			public long getSize() {
				return PdfConstants.MAX_PDF_FILE_SIZE_BYTES;
			}
		};
	}

	private byte[] readTestPdf() throws IOException {
		try (InputStream inputStream = getClass().getResourceAsStream("/pdf/sample.pdf")) {
			assertNotNull(inputStream, "テストPDFリソースが見つかりません。");
			return inputStream.readAllBytes();
		}
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
