package com.clip.ghost.officecontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.clip.ghost.officecontent.exception.OfficeInputException;

/**
 * {@link OfficeMarkdownLogic} のOffice文書読み取りを検証するテスト。
 * <p>
 * fixtureファイルは置かず、POIでその場で組み立てる。実在の業務資料をリポジトリへ混入させない方針
 * （{@code CONTRIBUTING.md} / {@code SECURITY.md}）を守りつつ、検証したい構造だけを明示できる。
 */
class OfficeMarkdownLogicTest {

	private final OfficeMarkdownLogic officeMarkdownLogic = new OfficeMarkdownLogic();

	@Test
	@DisplayName("Wordの見出しスタイルをMarkdownの見出しへ、本文を段落へ起こす")
	void readMarkdownConvertsWordHeadingsAndParagraphs() throws IOException {
		byte[] docx = createWord(document -> {
			addParagraph(document, "Heading1", "設計書");
			addParagraph(document, "Heading2", "概要");
			addParagraph(document, null, "この文書は設計の概要を説明する。");
		});

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("設計書.docx", docx),
				OfficeDocumentType.DOCX);

		assertTrue(markdown.contains("# 設計書"));
		assertTrue(markdown.contains("## 概要"));
		assertTrue(markdown.contains("この文書は設計の概要を説明する。"));
	}

	@Test
	@DisplayName("Wordの表をGFMの表として起こし、段落との出現順を保つ")
	void readMarkdownConvertsWordTableKeepingBodyOrder() throws IOException {
		byte[] docx = createWord(document -> {
			addParagraph(document, null, "表の前の説明");
			XWPFTable table = document.createTable(2, 2);
			table.getRow(0).getCell(0).setText("項目");
			table.getRow(0).getCell(1).setText("値");
			table.getRow(1).getCell(0).setText("A");
			table.getRow(1).getCell(1).setText("1");
		});

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("表.docx", docx), OfficeDocumentType.DOCX);

		assertTrue(markdown.contains("| 項目 | 値 |"));
		assertTrue(markdown.contains("| --- | --- |"));
		assertTrue(markdown.contains("| A | 1 |"));
		assertTrue(markdown.indexOf("表の前の説明") < markdown.indexOf("| 項目 | 値 |"));
	}

	@Test
	@DisplayName("Excelはシートごとに見出しを付け、表示どおりの文字列で表にする")
	void readMarkdownConvertsExcelSheetsAsTables() throws IOException {
		byte[] xlsx = createExcel(workbook -> {
			Sheet sheet = workbook.createSheet("画面一覧");
			writeCell(sheet, 0, 0, "画面ID");
			writeCell(sheet, 0, 1, "画面名");
			writeCell(sheet, 1, 0, "S001");
			writeCell(sheet, 1, 1, "ログイン");
		});

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("一覧.xlsx", xlsx), OfficeDocumentType.XLSX);

		assertTrue(markdown.contains("## 画面一覧"));
		assertTrue(markdown.contains("| 画面ID | 画面名 |"));
		assertTrue(markdown.contains("| S001 | ログイン |"));
	}

	@Test
	@DisplayName("Excelの数値セルは丸めずに表示どおりの文字列にする")
	void readMarkdownKeepsExcelDisplayValueForNumericCell() throws IOException {
		byte[] xlsx = createExcel(workbook -> {
			Sheet sheet = workbook.createSheet("数値");
			writeCell(sheet, 0, 0, "件数");
			Row row = sheet.createRow(1);
			row.createCell(0).setCellValue(1000);
		});

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("数値.xlsx", xlsx), OfficeDocumentType.XLSX);

		// getNumericCellValueをそのまま使うと 1000.0 になる。DataFormatter経由であることを固定する。
		assertTrue(markdown.contains("| 1000 |"));
		assertFalse(markdown.contains("1000.0"));
	}

	@Test
	@DisplayName("Excelの結合セルは左上の値を結合範囲へ展開する")
	void readMarkdownExpandsExcelMergedCellValue() throws IOException {
		byte[] xlsx = createExcel(workbook -> {
			Sheet sheet = workbook.createSheet("結合");
			writeCell(sheet, 0, 0, "区分");
			writeCell(sheet, 0, 1, "内容");
			writeCell(sheet, 1, 0, "共通");
			writeCell(sheet, 1, 1, "A");
			writeCell(sheet, 2, 1, "B");
			sheet.addMergedRegion(new CellRangeAddress(1, 2, 0, 0));
		});

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("結合.xlsx", xlsx), OfficeDocumentType.XLSX);

		// 結合範囲の2行目も「共通」で埋まる。空にすると行の意味が読めなくなるため、重複を選ぶ。
		assertTrue(markdown.contains("| 共通 | A |"));
		assertTrue(markdown.contains("| 共通 | B |"));
	}

	@Test
	@DisplayName("Excelの空シートも見出しを残し、空である旨を書く")
	void readMarkdownKeepsHeadingForEmptyExcelSheet() throws IOException {
		byte[] xlsx = createExcel(workbook -> workbook.createSheet("未記入"));

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("空.xlsx", xlsx), OfficeDocumentType.XLSX);

		assertTrue(markdown.contains("## 未記入"));
		assertTrue(markdown.contains("（空のシート）"));
	}

	@Test
	@DisplayName("PowerPointはスライドごとに見出しを付け、テキストを段落にする")
	void readMarkdownConvertsPowerPointSlides() throws IOException {
		byte[] pptx = createPowerPoint(slideShow -> {
			XSLFSlide slide = slideShow.createSlide();
			XSLFTextBox textBox = slide.createTextBox();
			textBox.setText("システム構成");
			XSLFSlide secondSlide = slideShow.createSlide();
			secondSlide.createTextBox().setText("処理フロー");
		});

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("資料.pptx", pptx), OfficeDocumentType.PPTX);

		assertTrue(markdown.contains("## スライド 1"));
		assertTrue(markdown.contains("システム構成"));
		assertTrue(markdown.contains("## スライド 2"));
		assertTrue(markdown.contains("処理フロー"));
	}

	@Test
	@DisplayName("テキストの無いスライドも見出しを残し、空である旨を書く")
	void readMarkdownKeepsHeadingForEmptySlide() throws IOException {
		byte[] pptx = createPowerPoint(XMLSlideShow::createSlide);

		String markdown = officeMarkdownLogic.readMarkdown(toMultipart("空.pptx", pptx), OfficeDocumentType.PPTX);

		assertTrue(markdown.contains("## スライド 1"));
		assertTrue(markdown.contains("（テキストの無いスライド）"));
	}

	@Test
	@DisplayName("Office文書として読めないファイルはOfficeInputExceptionになる")
	void readMarkdownThrowsWhenFileIsNotOfficeDocument() {
		MockMultipartFile notOffice = toMultipart("壊れた.docx",
				"これはOffice文書ではありません。".getBytes(StandardCharsets.UTF_8));

		OfficeInputException exception = assertThrows(OfficeInputException.class,
				() -> officeMarkdownLogic.readMarkdown(notOffice, OfficeDocumentType.DOCX));

		assertEquals("壊れた.docx", exception.getFileName());
	}

	/**
	 * Word文書を組み立ててbyte配列にする。
	 *
	 * @param builder 文書の組み立て処理
	 * @return Word文書のbyte配列
	 * @throws IOException 文書の生成に失敗した場合
	 */
	private byte[] createWord(WordBuilder builder) throws IOException {
		try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			builder.build(document);
			document.write(out);
			return out.toByteArray();
		}
	}

	/**
	 * Excelブックを組み立ててbyte配列にする。
	 *
	 * @param builder ブックの組み立て処理
	 * @return Excelブックのbyte配列
	 * @throws IOException ブックの生成に失敗した場合
	 */
	private byte[] createExcel(ExcelBuilder builder) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			builder.build(workbook);
			workbook.write(out);
			return out.toByteArray();
		}
	}

	/**
	 * PowerPointプレゼンテーションを組み立ててbyte配列にする。
	 *
	 * @param builder プレゼンテーションの組み立て処理
	 * @return プレゼンテーションのbyte配列
	 * @throws IOException プレゼンテーションの生成に失敗した場合
	 */
	private byte[] createPowerPoint(PowerPointBuilder builder) throws IOException {
		try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			builder.build(slideShow);
			slideShow.write(out);
			return out.toByteArray();
		}
	}

	/**
	 * Word文書へ段落を追加する。
	 *
	 * @param document  追加先の文書
	 * @param styleName 段落のスタイル名。nullの場合は本文
	 * @param text      段落のテキスト
	 */
	private void addParagraph(XWPFDocument document, String styleName, String text) {
		XWPFParagraph paragraph = document.createParagraph();
		if (styleName != null) {
			paragraph.setStyle(styleName);
		}
		XWPFRun run = paragraph.createRun();
		run.setText(text);
	}

	/**
	 * シートへ文字列セルを書き込む。
	 *
	 * @param sheet       書き込み先のシート
	 * @param rowIndex    行の0始まりindex
	 * @param columnIndex 列の0始まりindex
	 * @param value       セルの値
	 */
	private void writeCell(Sheet sheet, int rowIndex, int columnIndex, String value) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			row = sheet.createRow(rowIndex);
		}
		Cell cell = row.createCell(columnIndex);
		cell.setCellValue(value);
	}

	/**
	 * multipartのアップロードファイルを組み立てる。
	 *
	 * @param fileName ファイル名
	 * @param contents ファイル内容
	 * @return multipartファイル
	 */
	private MockMultipartFile toMultipart(String fileName, byte[] contents) {
		return new MockMultipartFile("officeFile", fileName, "application/octet-stream", contents);
	}

	/**
	 * テスト用Word文書の組み立て処理。
	 */
	@FunctionalInterface
	private interface WordBuilder {
		/**
		 * 文書を組み立てる。
		 *
		 * @param document 組み立て対象の文書
		 */
		void build(XWPFDocument document);
	}

	/**
	 * テスト用Excelブックの組み立て処理。
	 */
	@FunctionalInterface
	private interface ExcelBuilder {
		/**
		 * ブックを組み立てる。
		 *
		 * @param workbook 組み立て対象のブック
		 */
		void build(XSSFWorkbook workbook);
	}

	/**
	 * テスト用PowerPointプレゼンテーションの組み立て処理。
	 */
	@FunctionalInterface
	private interface PowerPointBuilder {
		/**
		 * プレゼンテーションを組み立てる。
		 *
		 * @param slideShow 組み立て対象のプレゼンテーション
		 */
		void build(XMLSlideShow slideShow);
	}
}
