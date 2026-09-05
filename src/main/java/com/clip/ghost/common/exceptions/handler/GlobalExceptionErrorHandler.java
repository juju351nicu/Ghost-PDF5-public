package com.clip.ghost.common.exceptions.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.clip.ghost.common.exceptions.CustomFieldError;
import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

/**
 * アプリケーション共通の例外をHTTPレスポンスへ変換するREST用例外ハンドラー。
 * <p>
 * multipartアップロード失敗やPDF処理失敗を、FEの既存エラー表示で扱える {@code fieldErrors} 形式へ寄せる。
 */
@RestControllerAdvice
public class GlobalExceptionErrorHandler extends ResponseEntityExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionErrorHandler.class);
	private static final String CACHE_CONTROL_VALUE = "must-revalidate, post-check=0, pre-check=0";
	private static final String MULTIPART_ERROR_CODE = "multipartError";
	private static final String PDF_PROCESSING_ERROR_CODE = "pdfProcessingError";
	private static final String MULTIPART_ERROR_MESSAGE = "許可されないサイズのファイルが入っております。";
	private static final String PDF_PROCESSING_ERROR_MESSAGE = "PDF処理に失敗しました。入力ファイルを確認してください。";

	/**
	 * MultipartExceptionがスローされた場合、レスポンスステータスを413にする。<br>
	 * 該当するエラーメッセージを返却する。
	 * 
	 * @param ex MultipartException
	 * @return FieldErrorResponseのレスポンス
	 */
	@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
	@ExceptionHandler(MultipartException.class)
	protected ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex) {
		LOGGER.warn("アップロードファイルサイズまたはmultipart requestが不正です。message={}", ex.getMessage());
		LOGGER.debug("Multipart例外の詳細です。", ex);
		return createErrorResponse(MULTIPART_ERROR_CODE, MULTIPART_ERROR_MESSAGE, HttpStatus.CONTENT_TOO_LARGE);
	}

	/**
	 * PDF処理に失敗した場合、レスポンスステータスを500にする。<br>
	 * フロントエンドが既存のエラー表示で扱えるよう、validation errorと同じfieldErrors形式で返却する。
	 *
	 * @param ex PDF処理例外
	 * @return PDF処理エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(PdfProcessingException.class)
	protected ResponseEntity<ErrorResponse> handlePdfProcessing(PdfProcessingException ex) {
		LOGGER.error("PDF処理に失敗しました。message={}", ex.getMessage());
		LOGGER.debug("PDF処理例外の詳細です。", ex);
		return createErrorResponse(PDF_PROCESSING_ERROR_CODE, PDF_PROCESSING_ERROR_MESSAGE,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 共通エラーレスポンスを作成する。
	 *
	 * @param errorCode  エラーコード
	 * @param message    表示するエラーメッセージ
	 * @param httpStatus HTTPステータス
	 * @return エラーレスポンス
	 */
	private ResponseEntity<ErrorResponse> createErrorResponse(String errorCode, String message, HttpStatus httpStatus) {
		ErrorResponse fieldErrorResponse = new ErrorResponse();
		fieldErrorResponse.setFieldErrors(List.of(buildFieldError(errorCode, message)));

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setCacheControl(CACHE_CONTROL_VALUE);
		return new ResponseEntity<>(fieldErrorResponse, headers, httpStatus);
	}

	/**
	 * FE向け共通形式の項目エラーを作成する。
	 *
	 * @param errorCode エラーコード
	 * @param message   エラーメッセージ
	 * @return 項目エラー
	 */
	private CustomFieldError buildFieldError(String errorCode, String message) {
		CustomFieldError fieldError = new CustomFieldError();
		fieldError.setErrorCode(errorCode);
		fieldError.setField("");
		fieldError.setMessage(message);
		return fieldError;
	}
}
