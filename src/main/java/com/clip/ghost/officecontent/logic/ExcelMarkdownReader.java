package com.clip.ghost.officecontent.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.clip.ghost.common.utils.MarkdownBlockJoiner;
import com.clip.ghost.common.utils.MarkdownTableBuilder;

import lombok.NoArgsConstructor;

/**
 * Excelブック（.xlsx）をMarkdownへ起こす内部クラス。
 * <p>
 * シートごとに見出しを付け、シート内の使用範囲をGFMの表として出す。
 * <p>
 * セルの値は {@link DataFormatter} で「Excelの画面に表示されているとおりの文字列」にする。
 * {@code getNumericCellValue} を使うと、表示上は {@code 1,000} や {@code 2026/09/13} のセルが
 * {@code 1000.0} や {@code 46279.0} になり、設計書としては別物になってしまう。
 * <p>
 * 結合セルは左上の値を結合範囲の全セルへ展開する。Markdownの表にセル結合が無いため、
 * 展開しないと結合範囲の2セル目以降が空になり、行の意味が読めなくなる。値の重複のほうが実害が小さい。
 */
@NoArgsConstructor
final class ExcelMarkdownReader {
	private static final String SHEET_HEADING_PREFIX = "## ";
	private static final String EMPTY_SHEET_NOTE = "（空のシート）";

	/** セルの表示値を取り出すフォーマッタ。数式は評価せず、保存されているキャッシュ値を使う。 */
	private final DataFormatter dataFormatter = new DataFormatter();

	/**
	 * Excelブックをシート単位のMarkdownへ変換する。
	 *
	 * @param inputStream Excelブックの入力ストリーム
	 * @return Markdown本文
	 * @throws IOException Excelブックを読み込めない場合
	 */
	String read(InputStream inputStream) throws IOException {
		try (Workbook workbook = new XSSFWorkbook(inputStream)) {
			List<String> blocks = new ArrayList<>();
			for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
				appendSheet(blocks, workbook.getSheetAt(sheetIndex));
			}
			return MarkdownBlockJoiner.join(blocks);
		}
	}

	/**
	 * 1シート分の見出しと表をMarkdownブロックへ追加する。
	 *
	 * @param blocks 追加先のブロックリスト
	 * @param sheet  シート
	 */
	private void appendSheet(List<String> blocks, Sheet sheet) {
		blocks.add(SHEET_HEADING_PREFIX + StringUtils.trim(sheet.getSheetName()));
		String markdownTable = MarkdownTableBuilder.build(readRows(sheet));
		// 空のシートも見出しだけは残す。シートが無かったのか空だったのかを利用者が区別できるようにする。
		blocks.add(StringUtils.defaultIfEmpty(markdownTable, EMPTY_SHEET_NOTE));
	}

	/**
	 * シートの使用範囲を行ごとのセル文字列として読み出す。
	 *
	 * @param sheet シート
	 * @return 行ごとのセル文字列
	 */
	private List<List<String>> readRows(Sheet sheet) {
		List<List<String>> rows = new ArrayList<>();
		int lastRowNum = sheet.getLastRowNum();
		int columnCount = resolveColumnCount(sheet, lastRowNum);
		if (columnCount == 0) {
			return rows;
		}
		for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRowNum; rowIndex++) {
			rows.add(readRow(sheet, rowIndex, columnCount));
		}
		return rows;
	}

	/**
	 * シート内で最も右まで使われている列数を求める。
	 *
	 * @param sheet      シート
	 * @param lastRowNum 最終行の0始まりindex
	 * @return 列数
	 */
	private int resolveColumnCount(Sheet sheet, int lastRowNum) {
		int columnCount = 0;
		for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastRowNum; rowIndex++) {
			Row row = sheet.getRow(rowIndex);
			if (row != null) {
				columnCount = Math.max(columnCount, row.getLastCellNum());
			}
		}
		return columnCount;
	}

	/**
	 * 1行分のセル文字列を読み出す。
	 *
	 * @param sheet       シート
	 * @param rowIndex    行の0始まりindex
	 * @param columnCount 読み出す列数
	 * @return セル文字列のリスト
	 */
	private List<String> readRow(Sheet sheet, int rowIndex, int columnCount) {
		List<String> cells = new ArrayList<>(columnCount);
		Row row = sheet.getRow(rowIndex);
		for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
			cells.add(readCell(sheet, row, rowIndex, columnIndex));
		}
		return cells;
	}

	/**
	 * 1セル分の表示文字列を読み出す。結合セルの場合は左上の値を返す。
	 *
	 * @param sheet       シート
	 * @param row         行。存在しない場合はnull
	 * @param rowIndex    行の0始まりindex
	 * @param columnIndex 列の0始まりindex
	 * @return セルの表示文字列
	 */
	private String readCell(Sheet sheet, Row row, int rowIndex, int columnIndex) {
		String value = row == null ? StringUtils.EMPTY : formatCell(row.getCell(columnIndex));
		if (StringUtils.isNotEmpty(value)) {
			return value;
		}
		return readMergedCellValue(sheet, rowIndex, columnIndex);
	}

	/**
	 * 指定位置が結合範囲に含まれる場合、その左上セルの表示文字列を返す。
	 *
	 * @param sheet       シート
	 * @param rowIndex    行の0始まりindex
	 * @param columnIndex 列の0始まりindex
	 * @return 左上セルの表示文字列。結合範囲に含まれない場合は空文字
	 */
	private String readMergedCellValue(Sheet sheet, int rowIndex, int columnIndex) {
		for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
			if (mergedRegion.isInRange(rowIndex, columnIndex)) {
				Row firstRow = sheet.getRow(mergedRegion.getFirstRow());
				return firstRow == null ? StringUtils.EMPTY
						: formatCell(firstRow.getCell(mergedRegion.getFirstColumn()));
			}
		}
		return StringUtils.EMPTY;
	}

	/**
	 * セルの表示文字列を取得する。
	 *
	 * @param cell セル。存在しない場合はnull
	 * @return セルの表示文字列
	 */
	private String formatCell(Cell cell) {
		return cell == null ? StringUtils.EMPTY : StringUtils.trim(dataFormatter.formatCellValue(cell));
	}
}
