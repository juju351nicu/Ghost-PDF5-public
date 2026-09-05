package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PDFページ番号計算ユーティリティのテストクラス。
 */
class PageUtilsTest {

	@Test
	@DisplayName("ページ選択_削除ページ未指定の場合は全ページを残す")
	void selectPageNumbersWithNullDeletePagesKeepsAllPages() {
		List<Integer> selectedPages = PageUtils.selectPageNumbers(1, 3, null);

		assertEquals(List.of(1, 2, 3), selectedPages);
	}

	@Test
	@DisplayName("ページ選択_削除ページ空リストの場合は全ページを残す")
	void selectPageNumbersWithEmptyDeletePagesKeepsAllPages() {
		List<Integer> selectedPages = PageUtils.selectPageNumbers(1, 3, List.of());

		assertEquals(List.of(1, 2, 3), selectedPages);
	}

	@Test
	@DisplayName("ページ選択_削除ページに重複があっても対象ページだけ除外する")
	void selectPageNumbersWithDuplicateDeletePagesRemovesTargetPages() {
		List<Integer> selectedPages = PageUtils.selectPageNumbers(1, 5, List.of(2, 2, 4));

		assertEquals(List.of(1, 3, 5), selectedPages);
	}

	@Test
	@DisplayName("ページ選択_削除ページに範囲外があっても範囲内ページだけで計算する")
	void selectPageNumbersWithOutOfRangeDeletePagesIgnoresOutOfRangePages() {
		List<Integer> selectedPages = PageUtils.selectPageNumbers(1, 3, List.of(0, 2, 99));

		assertEquals(List.of(1, 3), selectedPages);
	}

	@Test
	@DisplayName("ページ選択_削除ページにnullが混ざっても無視する")
	void selectPageNumbersWithNullValueInDeletePagesIgnoresNull() {
		List<Integer> selectedPages = PageUtils.selectPageNumbers(1, 3, Arrays.asList(null, 2));

		assertEquals(List.of(1, 3), selectedPages);
	}

	@Test
	@DisplayName("ページ選択_開始ページが終了ページより大きい場合は空リストを返す")
	void selectPageNumbersWithInvalidRangeReturnsEmptyList() {
		List<Integer> selectedPages = PageUtils.selectPageNumbers(3, 1, List.of(1));

		assertEquals(List.of(), selectedPages);
	}
}
