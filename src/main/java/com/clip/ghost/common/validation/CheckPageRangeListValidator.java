package com.clip.ghost.common.validation;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.clip.ghost.common.utils.PageRange;
import com.clip.ghost.common.utils.PageUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link CheckPageRangeList} の検証ロジック。
 * <p>
 * 利用者が自分で直せるエラーのため、原因別のメッセージへ差し替える。1つのメッセージにまとめると
 * 「形式が違うのか、範囲が重なっているのか」が画面から分からない。
 */
public class CheckPageRangeListValidator implements ConstraintValidator<CheckPageRangeList, List<String>> {
	private static final String INVALID_FORMAT_MESSAGE = "分割範囲は「1-5」「7」の形式で入力してください。";
	private static final String OVERLAPPING_MESSAGE = "分割範囲が重複しています。同じページを複数の範囲へ含めないでください。";
	private static final String TOO_MANY_RANGES_MESSAGE = "分割範囲は%d件までにしてください。";

	private int maxRanges;

	/**
	 * annotationの設定値を取り込む。
	 *
	 * @param constraintAnnotation 検証対象のannotation
	 */
	@Override
	public void initialize(CheckPageRangeList constraintAnnotation) {
		this.maxRanges = constraintAnnotation.max();
	}

	/**
	 * ページ範囲文字列のリストが形式・大小関係・重なり・件数の条件を満たすか検証する。
	 * <p>
	 * 未指定は「範囲指定なし（1ページずつ分割）」として正常にする。
	 *
	 * @param rangeTexts 検証対象のページ範囲文字列リスト
	 * @param context    validation context
	 * @return すべての条件を満たす場合はtrue
	 */
	@Override
	public boolean isValid(List<String> rangeTexts, ConstraintValidatorContext context) {
		List<String> targetRangeTexts = CollectionUtils.emptyIfNull(rangeTexts).stream()
				.filter(StringUtils::isNotBlank).toList();
		if (targetRangeTexts.isEmpty()) {
			return true;
		}
		if (targetRangeTexts.size() > maxRanges) {
			return reject(context, TOO_MANY_RANGES_MESSAGE.formatted(maxRanges));
		}
		if (!targetRangeTexts.stream().allMatch(PageUtils::isValidPageRangeText)) {
			return reject(context, INVALID_FORMAT_MESSAGE);
		}
		List<PageRange> pageRanges = PageUtils.parsePageRanges(targetRangeTexts);
		if (PageUtils.hasOverlappingPageRanges(pageRanges)) {
			return reject(context, OVERLAPPING_MESSAGE);
		}
		return true;
	}

	/**
	 * 既定メッセージを原因別メッセージへ差し替えて検証エラーにする。
	 *
	 * @param context validation context
	 * @param message 画面へ表示するエラーメッセージ
	 * @return 常にfalse
	 */
	private boolean reject(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
		return false;
	}
}
