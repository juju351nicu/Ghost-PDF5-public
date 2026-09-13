package com.clip.ghost.officecontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OfficeWriterLogic} のOffice文書組み立てを検証するテスト。
 */
class OfficeWriterLogicTest {

	private final OfficeWriterLogic officeWriterLogic = new OfficeWriterLogic();

	@Test
	@DisplayName("ページ単位テキストを段落としてWord文書へ書き出す")
	void writeWordCreatesParagraphsFromPageTexts() throws IOException {
		byte[] docx = officeWriterLogic.writeWord(List.of("1ページ目の本文", "2ページ目の本文"));

		try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
			String text = String.join("\n", document.getParagraphs().stream().map(XWPFParagraph::getText).toList());
			assertTrue(text.contains("1ページ目の本文"));
			assertTrue(text.contains("2ページ目の本文"));
		}
	}

	@Test
	@DisplayName("Word文書ではページの区切りに改ページを入れる")
	void writeWordInsertsPageBreakBetweenPages() throws IOException {
		byte[] docx = officeWriterLogic.writeWord(List.of("A", "B"));

		try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
			// 改ページ用の空段落が1つ挟まるため、本文2段落 + 改ページ1段落になる。
			assertEquals(3, document.getParagraphs().size());
		}
	}

	@Test
	@DisplayName("テキスト内の改行コードはCRLF / CR / LFのいずれでも段落へ分ける")
	void writeWordSplitsEveryLineSeparator() throws IOException {
		byte[] docx = officeWriterLogic.writeWord(List.of("1行目\r\n2行目\r3行目\n4行目"));

		try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
			assertEquals(4, document.getParagraphs().size());
		}
	}

	@Test
	@DisplayName("ページ単位テキストを1ページ1シートのExcelブックへ書き出す")
	void writeExcelCreatesOneSheetPerPage() throws IOException {
		byte[] xlsx = officeWriterLogic.writeExcel(List.of("1行目\n2行目", "別ページ"));

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertEquals(2, workbook.getNumberOfSheets());
			assertEquals("Page1", workbook.getSheetName(0));
			assertEquals("1行目", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
			assertEquals("2行目", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
			assertEquals("別ページ", workbook.getSheetAt(1).getRow(0).getCell(0).getStringCellValue());
		}
	}

	@Test
	@DisplayName("ページが1件も無い場合でもExcelが開けるよう空シートを1枚作る")
	void writeExcelCreatesEmptySheetWhenNoPageGiven() throws IOException {
		byte[] xlsx = officeWriterLogic.writeExcel(List.of());

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertEquals(1, workbook.getNumberOfSheets());
		}
	}

	@Test
	@DisplayName("ページ画像を1枚1スライドとしてPowerPointへ貼り、1ページ目の寸法をスライドサイズにする")
	void writePowerPointCreatesOneSlidePerPageImage() throws IOException {
		byte[] pngBytes = createPng(200, 100);

		byte[] pptx = officeWriterLogic.writePowerPoint(List.of(pngBytes, pngBytes), 612f, 792f);

		try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(pptx))) {
			assertEquals(2, slideShow.getSlides().size());
			assertEquals(612, slideShow.getPageSize().width);
			assertEquals(792, slideShow.getPageSize().height);
		}
	}

	@Test
	@DisplayName("ページ寸法が0の場合でもPowerPointが開けるよう既定のスライドサイズにする")
	void writePowerPointFallsBackToDefaultSlideSize() throws IOException {
		byte[] pptx = officeWriterLogic.writePowerPoint(List.of(), 0f, 0f);

		try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(pptx))) {
			assertEquals(0, slideShow.getSlides().size());
			assertTrue(slideShow.getPageSize().width > 0);
			assertTrue(slideShow.getPageSize().height > 0);
		}
	}

	/**
	 * 指定サイズの単色PNGを生成する。
	 *
	 * @param width  幅（px）
	 * @param height 高さ（px）
	 * @return PNGのbyte配列
	 * @throws IOException 画像の生成に失敗した場合
	 */
	private byte[] createPng(int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(Color.LIGHT_GRAY);
			graphics.fillRect(0, 0, width, height);
		} finally {
			graphics.dispose();
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		}
	}
}
