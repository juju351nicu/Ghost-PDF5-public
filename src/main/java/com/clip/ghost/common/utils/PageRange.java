package com.clip.ghost.common.utils;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 画面・APIと同じ1始まりで表したページ範囲。
 * <p>
 * 単一ページも {@code startPage == endPage} の範囲として扱い、「1ページ」と「1ページ分の範囲」を
 * 別の型に分けない。分けると呼び出し側で分岐が増える。
 * 範囲の不変条件（1以上、開始 ≦ 終了）は生成時に固定し、範囲外の値を持つインスタンスを作れないようにする。
 * PDFの総ページ数との突き合わせは、この型では行わない。総ページ数はPDFを開くまで分からないため。
 *
 * @param startPage 範囲の開始ページ番号（1始まり）
 * @param endPage   範囲の終了ページ番号（1始まり、開始ページ以上）
 */
public record PageRange(int startPage, int endPage) {

	/**
	 * ページ範囲を生成する。
	 *
	 * @throws IllegalArgumentException 開始ページが1未満、または終了ページが開始ページより小さい場合
	 */
	public PageRange {
		if (startPage < 1) {
			throw new IllegalArgumentException("開始ページは1以上にしてください。startPage=" + startPage);
		}
		if (endPage < startPage) {
			throw new IllegalArgumentException(
					"終了ページは開始ページ以上にしてください。startPage=" + startPage + ", endPage=" + endPage);
		}
	}

	/**
	 * 範囲に含まれるページ番号を昇順で返す。
	 *
	 * @return 範囲に含まれる1始まりページ番号
	 */
	public List<Integer> toPageNumbers() {
		return IntStream.rangeClosed(startPage, endPage).boxed().toList();
	}

	/**
	 * 範囲に含まれるページ数を返す。
	 *
	 * @return ページ数
	 */
	public int pageCount() {
		return endPage - startPage + 1;
	}

	/**
	 * 範囲を入力形式と同じ文字列へ戻す。
	 * <p>
	 * 単一ページは {@code 3}、複数ページは {@code 3-7} と表す。分割後ファイル名に使う。
	 *
	 * @return 範囲を表す文字列
	 */
	public String toRangeText() {
		return startPage == endPage ? String.valueOf(startPage) : startPage + "-" + endPage;
	}

	/**
	 * 別の範囲と1ページ以上重なっているか判定する。
	 *
	 * @param other 比較対象の範囲
	 * @return 1ページ以上重なっている場合true
	 */
	public boolean overlaps(PageRange other) {
		return startPage <= other.endPage() && other.startPage() <= endPage;
	}
}
