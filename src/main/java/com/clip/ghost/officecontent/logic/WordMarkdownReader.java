package com.clip.ghost.officecontent.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import lombok.NoArgsConstructor;

/**
 * Word文書（.docx）をMarkdownへ起こす内部クラス。
 * <p>
 * 見出しは段落のスタイル名から判定する。Wordの見出しスタイルは既定で {@code Heading1} や
 * {@code 見出し 1} という名前を持つため、数字を手がかりに見出しレベルへ写す。
 * スタイル名を変更した文書では見出しとして拾えず本文になるが、内容は失われない。
 * <p>
 * 段落と表は本文中の出現順に並べる。段落をすべて出してから表をまとめると、
 * 「この表が何の説明なのか」という文脈が落ちてしまう。
 */
@NoArgsConstructor
final class WordMarkdownReader {
	private static final String HEADING_STYLE_PREFIX_EN = "heading";
	private static final String HEADING_STYLE_PREFIX_JA = "見出し";
	private static final int MAX_HEADING_LEVEL = 6;

	/**
	 * Word文書をMarkdownへ変換する。
	 *
	 * @param inputStream Word文書の入力ストリーム
	 * @return Markdown本文
	 * @throws IOException Word文書を読み込めない場合
	 */
	String read(InputStream inputStream) throws IOException {
		try (XWPFDocument document = new XWPFDocument(inputStream)) {
			List<String> blocks = new ArrayList<>();
			// getBodyElementsは段落と表を本文の出現順で返す。getParagraphs/getTablesを別々に回すと順序が崩れる。
			document.getBodyElements().forEach(element -> appendBlock(blocks, element));
			return MarkdownBlockJoiner.join(blocks);
		}
	}

	/**
	 * 本文要素1つ分をMarkdownブロックとして追加する。
	 *
	 * @param blocks  追加先のブロックリスト
	 * @param element 本文要素
	 */
	private void appendBlock(List<String> blocks, Object element) {
		if (element instanceof XWPFParagraph paragraph) {
			appendParagraph(blocks, paragraph);
			return;
		}
		if (element instanceof XWPFTable table) {
			appendTable(blocks, table);
		}
	}

	/**
	 * 段落をMarkdownブロックとして追加する。
	 *
	 * @param blocks    追加先のブロックリスト
	 * @param paragraph 段落
	 */
	private void appendParagraph(List<String> blocks, XWPFParagraph paragraph) {
		String text = StringUtils.trim(paragraph.getText());
		if (StringUtils.isEmpty(text)) {
			return;
		}
		int headingLevel = resolveHeadingLevel(paragraph.getStyle());
		blocks.add(headingLevel > 0 ? StringUtils.repeat('#', headingLevel) + " " + text : text);
	}

	/**
	 * 段落のスタイル名から見出しレベルを判定する。
	 *
	 * @param styleName 段落のスタイル名
	 * @return 1以上の見出しレベル。見出しでない場合は0
	 */
	private int resolveHeadingLevel(String styleName) {
		String normalized = StringUtils.deleteWhitespace(StringUtils.lowerCase(styleName));
		if (StringUtils.isEmpty(normalized)) {
			return 0;
		}
		if (!Strings.CS.startsWith(normalized, HEADING_STYLE_PREFIX_EN)
				&& !Strings.CS.startsWith(normalized, HEADING_STYLE_PREFIX_JA)) {
			return 0;
		}
		String levelText = StringUtils.getDigits(normalized);
		if (StringUtils.isEmpty(levelText)) {
			return 0;
		}
		// 見出し7以上はMarkdownに無い。最も深い見出しへ丸める。本文へ落とすと階層の手がかりが消えるため。
		return Math.min(Integer.parseInt(levelText), MAX_HEADING_LEVEL);
	}

	/**
	 * 表をGFMの表としてMarkdownブロックへ追加する。
	 *
	 * @param blocks 追加先のブロックリスト
	 * @param table  表
	 */
	private void appendTable(List<String> blocks, XWPFTable table) {
		List<List<String>> rows = new ArrayList<>();
		for (XWPFTableRow tableRow : CollectionUtils.emptyIfNull(table.getRows())) {
			rows.add(readRow(tableRow));
		}
		String markdownTable = MarkdownTableBuilder.build(rows);
		if (StringUtils.isNotEmpty(markdownTable)) {
			blocks.add(markdownTable);
		}
	}

	/**
	 * 表の1行分のセル文字列を読み出す。
	 *
	 * @param tableRow 表の行
	 * @return セル文字列のリスト
	 */
	private List<String> readRow(XWPFTableRow tableRow) {
		List<String> cells = new ArrayList<>();
		for (XWPFTableCell cell : CollectionUtils.emptyIfNull(tableRow.getTableCells())) {
			cells.add(StringUtils.trim(cell.getText()));
		}
		return cells;
	}
}
