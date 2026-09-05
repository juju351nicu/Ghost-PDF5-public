package com.clip.ghost.common.utils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;

/**
 * PDFページ番号の範囲計算を扱うユーティリティクラス。
 * <p>
 * 画面・リクエストでは1始まりのページ番号を扱うため、削除対象ページと残すページの計算をここへ集約する。
 * PDFBoxへ渡す0始まりの変換はPDFロジック層で行う。
 */
public final class PageUtils {

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
}
