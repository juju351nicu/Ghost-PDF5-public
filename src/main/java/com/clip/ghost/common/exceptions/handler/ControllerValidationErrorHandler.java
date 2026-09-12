package com.clip.ghost.common.exceptions.handler;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.clip.ghost.common.exceptions.CustomFieldError;
import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.pdfcontent.enums.CodeEnum;

/**
 * Controllerの入力validationエラーをFE向けの共通レスポンス形式へ変換する例外ハンドラー。
 * <p>
 * Spring MVCの {@link MethodArgumentNotValidException} を受け取り、既存フロントエンドが扱う
 * {@code fieldErrors} 形式に整形して {@code 400 Bad Request}
 * を返す。エラー項目は画面表示で使うため、可能な限り入力フィールド名を維持する。
 */
@ControllerAdvice
public class ControllerValidationErrorHandler extends ResponseEntityExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ControllerValidationErrorHandler.class);
	private static final String DEFAULT_VALIDATION_ERROR_CODE = "validationError";
	private static final String TYPE_MISMATCH_ERROR_CODE = "typeMismatch";

	/**
	 * validationエラーを項目単位のエラーレスポンスへ変換する。
	 *
	 * @param ex      validation例外
	 * @param headers レスポンスヘッダー
	 * @param status  Spring MVCが解決したHTTPステータス
	 * @param request リクエスト情報
	 * @return FE向け共通エラーレスポンス
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ErrorResponse fieldErrorResponse = buildErrorResponse(ex.getBindingResult());
		return handleExceptionInternal(ex, fieldErrorResponse, headers, HttpStatus.BAD_REQUEST, request);
	}

	/**
	 * Spring validationのエラー一覧をFE向け共通エラーレスポンスへ変換する。
	 *
	 * @param bindingResult Spring validationの結果
	 * @return FE向け共通エラーレスポンス
	 */
	private ErrorResponse buildErrorResponse(BindingResult bindingResult) {
		ErrorResponse fieldErrorResponse = new ErrorResponse();
		fieldErrorResponse.setFieldErrors(
				bindingResult.getAllErrors().stream().map(error -> buildCustomFieldError(error, bindingResult))
						.toList());
		return fieldErrorResponse;
	}

	/**
	 * Spring validationのエラーをFE向けの項目エラーへ変換する。
	 * <p>
	 * 通常はFieldErrorが渡るが、クラス単位validationではObjectErrorが渡る可能性があるため、
	 * FieldError前提でキャストせず安全に扱う。
	 *
	 * @param error         Spring validationのエラー
	 * @param bindingResult Spring validationの結果
	 * @return FE向け項目エラー
	 */
	private CustomFieldError buildCustomFieldError(ObjectError error, BindingResult bindingResult) {
		String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : "";
		String errorCode = resolveErrorCode(error);
		String message = resolveMessage(error, bindingResult);
		LOGGER.debug("Validation error. field={}, errorCode={}, message={}", fieldName, errorCode, message);

		CustomFieldError customFieldError = new CustomFieldError();
		customFieldError.setErrorCode(errorCode);
		customFieldError.setField(fieldName);
		customFieldError.setMessage(message);
		return customFieldError;
	}

	/**
	 * 画面へ返すエラーメッセージを取得する。
	 * <p>
	 * 区分値enumの型変換に失敗した場合、Springが組み立てるメッセージは
	 * {@code Failed to convert property value ...} のような英語の内部表現になる。画面はこのメッセージを
	 * そのままモーダルへ出すため、値域を知っているenum自身の説明へ差し替える。
	 * それ以外のエラーは、これまでどおりvalidation annotationのメッセージをそのまま使う。
	 *
	 * @param error         Spring validationのエラー
	 * @param bindingResult Spring validationの結果
	 * @return 画面表示用メッセージ
	 */
	private String resolveMessage(ObjectError error, BindingResult bindingResult) {
		if (!(error instanceof FieldError fieldError)
				|| !Strings.CS.equals(TYPE_MISMATCH_ERROR_CODE, error.getCode())) {
			return error.getDefaultMessage();
		}
		String invalidKeyMessage = CodeEnum.describeInvalidKey(bindingResult.getFieldType(fieldError.getField()));
		return StringUtils.defaultIfBlank(invalidKeyMessage, error.getDefaultMessage());
	}

	/**
	 * Spring validationのエラーコードをFE/API向けに取得する。
	 *
	 * @param error Spring validationのエラー
	 * @return エラーコード。未設定・空の場合は {@value #DEFAULT_VALIDATION_ERROR_CODE}
	 */
	private String resolveErrorCode(ObjectError error) {
		return StringUtils.defaultIfBlank(error.getCode(), DEFAULT_VALIDATION_ERROR_CODE);
	}
}
