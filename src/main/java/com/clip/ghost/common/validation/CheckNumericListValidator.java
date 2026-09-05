package com.clip.ghost.common.validation;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link CheckNumericList} の検証ロジック。
 * <p>
 * 削除ページ番号リストにnullや1未満の値が混ざることを防ぐ。未指定または空リストは 「削除ページなし」として扱う既存仕様のため正常にする。
 */
public class CheckNumericListValidator implements ConstraintValidator<CheckNumericList, List<Integer>> {

	/**
	 * ページ番号リストにnullまたは1未満の値が含まれていないかを検証する。
	 * <p>
	 * 削除ページ未指定は既存仕様として正常扱いにする。
	 *
	 * @param list    検証対象のページ番号リスト
	 * @param context validation context
	 * @return ページ番号リストが空、または全要素が1以上の場合はtrue
	 */
	@Override
	public boolean isValid(List<Integer> list, ConstraintValidatorContext context) {
		if (CollectionUtils.isEmpty(list)) {
			return true;
		}
		return list.stream().noneMatch(CheckNumericListValidator::isInvalidPageNumber);
	}

	/**
	 * 画面・リクエスト上のページ番号として不正な値かを判定する。
	 * <p>
	 * Ghost-PDF5のページ番号は利用者向けには1始まりのため、nullまたは1未満は不正にする。
	 *
	 * @param pageNumber ページ番号
	 * @return nullまたは1未満の場合true
	 */
	private static boolean isInvalidPageNumber(Integer pageNumber) {
		return pageNumber == null || pageNumber < 1;
	}
}
