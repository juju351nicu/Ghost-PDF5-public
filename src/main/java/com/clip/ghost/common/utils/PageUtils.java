package com.clip.ghost.common.utils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * PDFページ番号の範囲計算を扱うユーティリティクラス。
 * <p>
 * 画面・リクエストでは1始まりのページ番号を扱うため、削除対象ページと残すページの計算をここへ集約する。
 * PDFBoxへ渡す0始まりの変換はPDFロジック層で行う。
 */
public final class PageUtils {
	/** {@code 5} または {@code 5-12} の形式だけを許可する。0始まりや先頭0付き、符号付きは弾く。 */
	private static final Pattern PAGE_RANGE_TEXT = Pattern.compile("[1-9][0-9]*(-[1-9][0-9]*)?");

	/** ページ範囲の開始と終了の区切り文字。 */
	private static final String RANGE_DELIMITER = "-";

	private PageUtils() {
	}

	/**
	 * 開始ページから終了ページまでの範囲から、指定された削除対象ページを除外したページ番号リストを返す。
	 *
	 * @param startPage 最初のページ番号
	 * @param endPage   総ページ数
	 * @param pageList  「1,2,5」といった削除対象ページ番号リスト。未指定の場合は空リストとして扱う
	 * @return 削除対象を除いたページ番号リスト
	 */
	public static List<Integer> selectPageNumbers(int startPage, int endPage, List<Integer> pageList) {
		Set<Integer> deletePages = CollectionUtils.emptyIfNull(pageList).stream().filter(Objects::nonNull)
				.collect(Collectors.toSet());
		return IntStream.rangeClosed(startPage, endPage).boxed().filter(page -> !deletePages.contains(page)).toList();
	}

	/**
	 * ページ範囲文字列が形式として正しいか判定する。
	 * <p>
	 * 総ページ数との突き合わせは行わない。総ページ数はPDFを開くまで分からないため、annotation validationでは
	 * 形式だけを見て、ページ数との整合はPDFを開く層で判定する。
	 *
	 * @param rangeText {@code 5} または {@code 5-12} 形式のページ範囲文字列
	 * @return 形式が正しく、開始ページが終了ページ以下の場合はtrue
	 */
	public static boolean isValidPageRangeText(String rangeText) {
		if (StringUtils.isBlank(rangeText) || !PAGE_RANGE_TEXT.matcher(rangeText.trim()).matches()) {
			return false;
		}
		String[] rangeParts = rangeText.trim().split(RANGE_DELIMITER);
		return rangeParts.length == 1
				|| Integer.parseInt(rangeParts[0]) <= Integer.parseInt(rangeParts[1]);
	}

	/**
	 * ページ範囲文字列を {@link PageRange} へ変換する。
	 *
	 * @param rangeText {@code 5} または {@code 5-12} 形式のページ範囲文字列
	 * @return ページ範囲
	 * @throws IllegalArgumentException 形式が不正、または開始ページが終了ページより大きい場合
	 */
	public static PageRange parsePageRange(String rangeText) {
		if (!isValidPageRangeText(rangeText)) {
			throw new IllegalArgumentException("ページ範囲の形式が不正です。");
		}
		String[] rangeParts = rangeText.trim().split(RANGE_DELIMITER);
		int startPage = Integer.parseInt(rangeParts[0]);
		return new PageRange(startPage, rangeParts.length == 1 ? startPage : Integer.parseInt(rangeParts[1]));
	}

	/**
	 * ページ範囲文字列のリストを、入力順を保った {@link PageRange} のリストへ変換する。
	 * <p>
	 * 入力順を保つのは、分割後ファイルの並びを利用者の指定どおりにするため。空要素は無視し、
	 * 未指定（null・空リスト）は空リストを返す。呼び出し元は空リストを「範囲指定なし」として扱う。
	 *
	 * @param rangeTexts ページ範囲文字列のリスト
	 * @return ページ範囲のリスト
	 * @throws IllegalArgumentException いずれかの要素の形式が不正な場合
	 */
	public static List<PageRange> parsePageRanges(List<String> rangeTexts) {
		return CollectionUtils.emptyIfNull(rangeTexts).stream().filter(StringUtils::isNotBlank)
				.map(PageUtils::parsePageRange).toList();
	}

	/**
	 * ページ範囲のリストに重なりがあるか判定する。
	 *
	 * @param pageRanges ページ範囲のリスト
	 * @return 2つ以上の範囲が1ページ以上重なっている場合true
	 */
	public static boolean hasOverlappingPageRanges(List<PageRange> pageRanges) {
		List<PageRange> ranges = CollectionUtils.emptyIfNull(pageRanges).stream().filter(Objects::nonNull).toList();
		for (int index = 0; index < ranges.size(); index++) {
			for (int otherIndex = index + 1; otherIndex < ranges.size(); otherIndex++) {
				if (ranges.get(index).overlaps(ranges.get(otherIndex))) {
					return true;
				}
			}
		}
		return false;
	}
}
