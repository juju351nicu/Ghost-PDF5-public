package com.clip.ghost.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * ページ番号リストが正の数として扱えることを検証するためのカスタムvalidation。
 */
@Documented
@Constraint(validatedBy = { CheckNumericListValidator.class })
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckNumericList {
	/**
	 * validationエラー時のメッセージ。
	 *
	 * @return エラーメッセージ
	 */
	String message() default "正の数の値を入力してください";

	/**
	 * Bean Validationのvalidation group。
	 *
	 * @return validation group
	 */
	Class<?>[] groups() default {};

	/**
	 * Bean Validationのpayload。
	 *
	 * @return payload
	 */
	Class<? extends Payload>[] payload() default {};
}
