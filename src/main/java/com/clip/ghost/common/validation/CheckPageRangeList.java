package com.clip.ghost.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * ページ範囲文字列のリスト（{@code ["1-5", "6-12", "13"]}）を検証するカスタムvalidation。
 * <p>
 * 形式・大小関係・重なり・件数だけを見る。PDFの総ページ数との突き合わせはPDFを開くまで分からないため、
 * このannotationでは判定しない。未指定（null・空リスト）は「範囲指定なし」として正常にする。
 */
@Documented
@Constraint(validatedBy = { CheckPageRangeListValidator.class })
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckPageRangeList {
	/**
	 * validationエラー時の既定メッセージ。
	 * <p>
	 * 実際には原因別のメッセージへ差し替えるため、この値は使われない。
	 *
	 * @return エラーメッセージ
	 */
	String message() default "ページ範囲の指定が正しくありません。";

	/**
	 * 受け付けるページ範囲の最大件数。
	 * <p>
	 * 範囲の件数は分割後ファイル数、つまりZIPのサイズに直結する。1ページずつの分割は範囲未指定で行えるため、
	 * 範囲指定に大きな上限は要らない。
	 *
	 * @return 最大件数
	 */
	int max() default 50;

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
