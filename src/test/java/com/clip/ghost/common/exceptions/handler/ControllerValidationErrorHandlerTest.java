package com.clip.ghost.common.exceptions.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import com.clip.ghost.common.exceptions.CustomFieldError;
import com.clip.ghost.common.exceptions.ErrorResponse;

/**
 * {@link ControllerValidationErrorHandler} の単体テスト。
 */
class ControllerValidationErrorHandlerTest {

	private static final String REQUEST_OBJECT_NAME = "request";
	private static final String DEFAULT_VALIDATION_ERROR_CODE = "validationError";
	private static final String FIELD_ERROR_CODE = "NotNull";
	private static final String FIELD_NAME = "originalFile";
	private static final String FIELD_ERROR_MESSAGE = "PDFファイルを指定してください。";
	private static final String OBJECT_ERROR_MESSAGE = "リクエスト内容が不正です。";
	private static final String SECOND_FIELD_NAME = "insertPdfForm[0].insertPage";
	private static final String SECOND_FIELD_ERROR_MESSAGE = "差し込みページ番号が不正です。";

	private final ControllerValidationErrorHandler handler = new ControllerValidationErrorHandler();

	@Test
	@DisplayName("項目単位validationエラー_FE向けfieldErrors形式で返す")
	void handleMethodArgumentNotValidWithFieldErrorReturnsFieldErrors() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), REQUEST_OBJECT_NAME);
		bindingResult.addError(buildFieldErrorWithCode(FIELD_NAME, FIELD_ERROR_CODE, FIELD_ERROR_MESSAGE));

		ResponseEntity<Object> response = handle(bindingResult);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		CustomFieldError fieldError = getFirstFieldError(response);
		assertEquals(FIELD_ERROR_CODE, fieldError.getErrorCode());
		assertEquals(FIELD_NAME, fieldError.getField());
		assertEquals(FIELD_ERROR_MESSAGE, fieldError.getMessage());
	}

	@Test
	@DisplayName("複数validationエラー_追加順のfieldErrors形式で返す")
	void handleMethodArgumentNotValidWithMultipleErrorsKeepsErrorOrder() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), REQUEST_OBJECT_NAME);
		bindingResult.addError(new FieldError(REQUEST_OBJECT_NAME, FIELD_NAME, FIELD_ERROR_MESSAGE));
		bindingResult.addError(new FieldError(REQUEST_OBJECT_NAME, SECOND_FIELD_NAME, SECOND_FIELD_ERROR_MESSAGE));

		ResponseEntity<Object> response = handle(bindingResult);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		ErrorResponse errorResponse = getErrorResponse(response);
		assertEquals(2, errorResponse.getFieldErrors().size());
		assertFieldError(errorResponse.getFieldErrors().get(0), DEFAULT_VALIDATION_ERROR_CODE, FIELD_NAME,
				FIELD_ERROR_MESSAGE);
		assertFieldError(errorResponse.getFieldErrors().get(1), DEFAULT_VALIDATION_ERROR_CODE, SECOND_FIELD_NAME,
				SECOND_FIELD_ERROR_MESSAGE);
	}

	@Test
	@DisplayName("エラーコードが空文字_既定のエラーコードで返す")
	void handleMethodArgumentNotValidWithBlankCodeUsesDefaultErrorCode() throws Exception {
		// 空のエラーコードはFEが分岐に使えないため、未設定と同じく既定値へ寄せる。
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), REQUEST_OBJECT_NAME);
		bindingResult.addError(buildFieldErrorWithCode(FIELD_NAME, "", FIELD_ERROR_MESSAGE));

		ResponseEntity<Object> response = handle(bindingResult);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(DEFAULT_VALIDATION_ERROR_CODE, getFirstFieldError(response).getErrorCode());
	}

	@Test
	@DisplayName("クラス単位validationエラー_FieldErrorへキャストせず共通形式で返す")
	void handleMethodArgumentNotValidWithObjectErrorReturnsFieldErrors() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), REQUEST_OBJECT_NAME);
		bindingResult.addError(new ObjectError(REQUEST_OBJECT_NAME, OBJECT_ERROR_MESSAGE));

		ResponseEntity<Object> response = handle(bindingResult);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		CustomFieldError fieldError = getFirstFieldError(response);
		assertEquals(DEFAULT_VALIDATION_ERROR_CODE, fieldError.getErrorCode());
		assertEquals("", fieldError.getField());
		assertEquals(OBJECT_ERROR_MESSAGE, fieldError.getMessage());
	}

	/**
	 * Spring validationが持つエラーコード付きFieldErrorを作成する。
	 *
	 * @param fieldName フィールド名
	 * @param errorCode エラーコード
	 * @param message   エラーメッセージ
	 * @return エラーコード付きFieldError
	 */
	private FieldError buildFieldErrorWithCode(String fieldName, String errorCode, String message) {
		return new FieldError(REQUEST_OBJECT_NAME, fieldName, null, false, new String[] { errorCode }, null, message);
	}

	/**
	 * テスト用のBindingResultを例外ハンドラーへ渡す。
	 *
	 * @param bindingResult validationエラーを保持するBindingResult
	 * @return ハンドラーのレスポンス
	 * @throws Exception テスト用MethodParameterの生成に失敗した場合
	 */
	private ResponseEntity<Object> handle(BeanPropertyBindingResult bindingResult) throws Exception {
		MethodArgumentNotValidException exception = new MethodArgumentNotValidException(buildMethodParameter(),
				bindingResult);
		WebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());
		return handler.handleMethodArgumentNotValid(exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);
	}

	/**
	 * MethodArgumentNotValidExceptionの生成に必要なMethodParameterを作成する。
	 *
	 * @return テスト用MethodParameter
	 * @throws NoSuchMethodException テスト用メソッドが見つからない場合
	 */
	private MethodParameter buildMethodParameter() throws NoSuchMethodException {
		Method method = ControllerValidationErrorHandlerTest.class.getDeclaredMethod("dummyControllerMethod",
				Object.class);
		return new MethodParameter(method, 0);
	}

	/**
	 * レスポンス本文から先頭の項目エラーを取得する。
	 *
	 * @param response 例外ハンドラーのレスポンス
	 * @return 先頭の項目エラー
	 */
	private CustomFieldError getFirstFieldError(ResponseEntity<Object> response) {
		return getErrorResponse(response).getFieldErrors().getFirst();
	}

	/**
	 * レスポンス本文を共通エラーレスポンスとして取得する。
	 *
	 * @param response 例外ハンドラーのレスポンス
	 * @return 共通エラーレスポンス
	 */
	private ErrorResponse getErrorResponse(ResponseEntity<Object> response) {
		ErrorResponse errorResponse = assertInstanceOf(ErrorResponse.class, response.getBody());
		assertNotNull(errorResponse.getFieldErrors());
		return errorResponse;
	}

	/**
	 * 項目エラーのフィールド名とメッセージを検証する。
	 *
	 * @param fieldError      項目エラー
	 * @param expectedCode    期待するエラーコード
	 * @param expectedField   期待するフィールド名
	 * @param expectedMessage 期待するメッセージ
	 */
	private void assertFieldError(CustomFieldError fieldError, String expectedCode, String expectedField,
			String expectedMessage) {
		assertEquals(expectedCode, fieldError.getErrorCode());
		assertEquals(expectedField, fieldError.getField());
		assertEquals(expectedMessage, fieldError.getMessage());
	}

	@SuppressWarnings("unused")
	private void dummyControllerMethod(Object request) {
		// MethodArgumentNotValidException生成用のダミーメソッドです。
	}
}
