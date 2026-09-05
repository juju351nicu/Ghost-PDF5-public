package com.clip.ghost.common.exceptions.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartException;

import com.clip.ghost.common.exceptions.CustomFieldError;
import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

/**
 * {@link GlobalExceptionErrorHandler} の単体テスト。
 */
class GlobalExceptionErrorHandlerTest {

	private static final String CACHE_CONTROL_VALUE = "must-revalidate, post-check=0, pre-check=0";
	private static final String MULTIPART_ERROR_CODE = "multipartError";
	private static final String PDF_PROCESSING_ERROR_CODE = "pdfProcessingError";
	private static final String MULTIPART_ERROR_MESSAGE = "許可されないサイズのファイルが入っております。";
	private static final String PDF_PROCESSING_ERROR_MESSAGE = "PDF処理に失敗しました。入力ファイルを確認してください。";

	private GlobalExceptionErrorHandler handler;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionErrorHandler();
	}

	@Test
	@DisplayName("Multipart例外_ペイロード超過と共通エラー形式を返す")
	void handleMultipartReturnsPayloadTooLargeAndCommonErrorResponse() {
		ResponseEntity<ErrorResponse> response = handler.handleMultipart(new MultipartException("too large"));

		assertCommonErrorResponse(response, HttpStatus.CONTENT_TOO_LARGE, MULTIPART_ERROR_CODE,
				MULTIPART_ERROR_MESSAGE);
	}

	@Test
	@DisplayName("PDF処理例外_内部サーバーエラーと共通エラー形式を返す")
	void handlePdfProcessingReturnsInternalServerErrorAndCommonErrorResponse() {
		ResponseEntity<ErrorResponse> response = handler
				.handlePdfProcessing(new PdfProcessingException("PDF処理に失敗しました。"));

		assertCommonErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, PDF_PROCESSING_ERROR_CODE,
				PDF_PROCESSING_ERROR_MESSAGE);
	}

	/**
	 * 共通エラーレスポンスのHTTPステータス、ヘッダー、本文を検証する。
	 *
	 * @param response        検証対象レスポンス
	 * @param expectedStatus  期待するHTTPステータス
	 * @param expectedCode    期待するエラーコード
	 * @param expectedMessage 期待するエラーメッセージ
	 */
	private void assertCommonErrorResponse(ResponseEntity<ErrorResponse> response, HttpStatus expectedStatus,
			String expectedCode, String expectedMessage) {
		assertEquals(expectedStatus, response.getStatusCode());
		assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
		assertEquals(CACHE_CONTROL_VALUE, response.getHeaders().getCacheControl());
		assertNotNull(response.getBody());

		CustomFieldError fieldError = response.getBody().getFieldErrors().getFirst();
		assertEquals(expectedCode, fieldError.getErrorCode());
		assertEquals("", fieldError.getField());
		assertEquals(expectedMessage, fieldError.getMessage());
	}
}
