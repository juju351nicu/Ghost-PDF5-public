package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	@DisplayName("ページ範囲_単一ページと範囲の形式を受け付ける")
	void isValidPageRangeTextAcceptsSinglePageAndRange() {
		assertTrue(PageUtils.isValidPageRangeText("7"));
		assertTrue(PageUtils.isValidPageRangeText("1-5"));
		// 開始と終了が同じ範囲は1ページ分の指定として認める。
		assertTrue(PageUtils.isValidPageRangeText("3-3"));
		assertTrue(PageUtils.isValidPageRangeText(" 2-4 "));
	}

	@Test
	@DisplayName("ページ範囲_形式不正と大小逆転を拒否する")
	void isValidPageRangeTextRejectsInvalidText() {
		assertFalse(PageUtils.isValidPageRangeText(null));
		assertFalse(PageUtils.isValidPageRangeText(""));
		assertFalse(PageUtils.isValidPageRangeText("   "));
		assertFalse(PageUtils.isValidPageRangeText("a-b"));
		assertFalse(PageUtils.isValidPageRangeText("1-"));
		assertFalse(PageUtils.isValidPageRangeText("-5"));
		assertFalse(PageUtils.isValidPageRangeText("0"));
		assertFalse(PageUtils.isValidPageRangeText("0-3"));
		assertFalse(PageUtils.isValidPageRangeText("01-3"));
		assertFalse(PageUtils.isValidPageRangeText("1-2-3"));
		assertFalse(PageUtils.isValidPageRangeText("5-1"));
	}

	@Test
	@DisplayName("ページ範囲_文字列をPageRangeへ変換する")
	void parsePageRangeConvertsText() {
		assertEquals(new PageRange(1, 5), PageUtils.parsePageRange("1-5"));
		assertEquals(new PageRange(7, 7), PageUtils.parsePageRange("7"));
	}

	@Test
	@DisplayName("ページ範囲_形式不正の変換は例外にする")
	void parsePageRangeRejectsInvalidText() {
		assertThrows(IllegalArgumentException.class, () -> PageUtils.parsePageRange("5-1"));
	}

	@Test
	@DisplayName("ページ範囲リスト_入力順を保ち、空要素を無視する")
	void parsePageRangesKeepsRequestOrder() {
		List<PageRange> pageRanges = PageUtils.parsePageRanges(Arrays.asList("6-12", "", "1-5", null));

		// 分割後ファイルの並びを利用者の指定どおりにするため、昇順へ並び替えない。
		assertEquals(List.of(new PageRange(6, 12), new PageRange(1, 5)), pageRanges);
	}

	@Test
	@DisplayName("ページ範囲リスト_未指定は空リストにする")
	void parsePageRangesReturnsEmptyListWhenNotSpecified() {
		assertTrue(PageUtils.parsePageRanges(null).isEmpty());
		assertTrue(PageUtils.parsePageRanges(List.of()).isEmpty());
	}

	@Test
	@DisplayName("ページ範囲リスト_重なりを判定する")
	void hasOverlappingPageRangesDetectsOverlap() {
		assertTrue(PageUtils.hasOverlappingPageRanges(List.of(new PageRange(1, 5), new PageRange(3, 8))));
		assertTrue(PageUtils.hasOverlappingPageRanges(List.of(new PageRange(1, 5), new PageRange(5, 5))));
		assertFalse(PageUtils.hasOverlappingPageRanges(List.of(new PageRange(1, 5), new PageRange(6, 10))));
		assertFalse(PageUtils.hasOverlappingPageRanges(List.of(new PageRange(1, 5))));
		assertFalse(PageUtils.hasOverlappingPageRanges(null));
	}

	@Test
	@DisplayName("ページ範囲_ページ番号リストとページ数と表示文字列を返す")
	void pageRangeExposesPageNumbersCountAndText() {
		PageRange multiPageRange = new PageRange(2, 4);

		assertEquals(List.of(2, 3, 4), multiPageRange.toPageNumbers());
		assertEquals(3, multiPageRange.pageCount());
		assertEquals("2-4", multiPageRange.toRangeText());
		assertEquals("6", new PageRange(6, 6).toRangeText());
	}

	@Test
	@DisplayName("ページ範囲_不変条件を満たさない値では生成できない")
	void pageRangeRejectsInvalidBounds() {
		assertThrows(IllegalArgumentException.class, () -> new PageRange(0, 3));
		assertThrows(IllegalArgumentException.class, () -> new PageRange(5, 4));
	}
}
