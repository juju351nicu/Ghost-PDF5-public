package com.clip.ghost.common.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * セルの2次元リストをGFMの表記法の表へ組み立てるユーティリティ。
 * <p>
 * Word / Excel / PowerPoint の表をすべてこの1箇所で組み立てる。形式ごとに書き分けると、
 * 「Wordの表は崩れないのにExcelの表は崩れる」といった差が後から効いてくる。
 * <p>
 * Webページ取り込み（{@code webcontent}）でも同じ組み立てを使うため、Office専用の
 * {@code officecontent.logic} から {@code common.utils} へ移した。取り込み元が増えるたびに
 * 表の組み立てが分かれると、上と同じ食い違いが入力形式の数だけ増える。
 */
public final class MarkdownTableBuilder {
	private static final String CELL_SEPARATOR = " | ";
	private static final String ROW_PREFIX = "| ";
	private static final String ROW_SUFFIX = " |";
	private static final String HEADER_DELIMITER_CELL = "---";
	private static final String LINE_SEPARATOR = "\n";
	private static final String PIPE = "|";
	private static final String ESCAPED_PIPE = "\\|";

	/**
	 * インスタンス化を禁止する。
	 */
	private MarkdownTableBuilder() {
	}

	/**
	 * セルの2次元リストからGFMの表を組み立てる。
	 * <p>
	 * 1行目を見出し行として扱う。Markdownの表は見出し行が必須で、省略すると表として描画されないため、
	 * 元の表に見出しが無くても1行目を見出しに充てる。
	 * <p>
	 * 列数は最大の行に合わせて揃える。行ごとに列数が違うと、描画側が表として解釈できない。
	 *
	 * @param rows 行ごとのセル文字列
	 * @return GFMの表。行が1件も無い場合は空文字
	 */
	public static String build(List<List<String>> rows) {
		List<List<String>> targetRows = CollectionUtils.emptyIfNull(rows).stream()
				.filter(CollectionUtils::isNotEmpty).toList();
		if (CollectionUtils.isEmpty(targetRows)) {
			return StringUtils.EMPTY;
		}
		int columnCount = targetRows.stream().mapToInt(List::size).max().orElse(0);
		List<String> lines = new ArrayList<>();
		lines.add(buildRow(targetRows.get(0), columnCount));
		lines.add(buildHeaderDelimiter(columnCount));
		targetRows.stream().skip(1).forEach(row -> lines.add(buildRow(row, columnCount)));
		return String.join(LINE_SEPARATOR, lines);
	}

	/**
	 * 1行分のMarkdown表記を組み立てる。
	 *
	 * @param row         セル文字列
	 * @param columnCount 揃える列数
	 * @return 1行分のMarkdown表記
	 */
	private static String buildRow(List<String> row, int columnCount) {
		List<String> cells = new ArrayList<>(columnCount);
		for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
			cells.add(columnIndex < row.size() ? escapeCell(row.get(columnIndex)) : StringUtils.EMPTY);
		}
		return ROW_PREFIX + String.join(CELL_SEPARATOR, cells) + ROW_SUFFIX;
	}

	/**
	 * 見出し行の下に置く区切り行を組み立てる。
	 *
	 * @param columnCount 列数
	 * @return 区切り行のMarkdown表記
	 */
	private static String buildHeaderDelimiter(int columnCount) {
		List<String> cells = new ArrayList<>(columnCount);
		for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
			cells.add(HEADER_DELIMITER_CELL);
		}
		return ROW_PREFIX + String.join(CELL_SEPARATOR, cells) + ROW_SUFFIX;
	}

	/**
	 * セル内の文字をMarkdownの表で壊れない形へ直す。
	 * <p>
	 * パイプはエスケープし、セル内改行は {@code <br>} へ置き換える。改行をそのまま残すと、
	 * そこで表の行が切れて以降の行がすべてずれる。
	 *
	 * @param cell セル文字列
	 * @return 表へ埋め込める形にしたセル文字列
	 */
	private static String escapeCell(String cell) {
		String text = StringUtils.defaultString(cell);
		text = Strings.CS.replace(text, PIPE, ESCAPED_PIPE);
		text = Strings.CS.replace(text, "\r\n", "<br>");
		text = Strings.CS.replace(text, "\n", "<br>");
		return Strings.CS.replace(text, "\r", "<br>");
	}
}
