package com.clip.ghost.officecontent.logic;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.officecontent.exception.OfficeProcessingException;

import lombok.NoArgsConstructor;

/**
 * ページ単位の内容からOffice文書（.docx / .xlsx / .pptx）を組み立てる内部ロジック。
 * <p>
 * PDFから変換する場合の書き出し側を担当する。Apache POIへの依存をこのpackageへ閉じ込めるため、
 * 呼び出し元（{@code pdfcontent.service}）はテキストと画像だけを渡す。
 * <p>
 * どの形式も元PDFのレイアウトは再現しない。段組み、罫線、フォント、図の位置はPDFの描画命令の中にあり、
 * 構造として取り出せないため。再現が要る場合はPDFのまま扱うか、PowerPoint（ページ画像）を使う。
 */
@Component
@NoArgsConstructor
public class OfficeWriterLogic {
	private static final Logger LOGGER = LoggerFactory.getLogger(OfficeWriterLogic.class);
	private static final String SHEET_NAME_FORMAT = "Page%d";
	private static final String LINE_SEPARATOR_PATTERN = "\\r\\n|\\r|\\n";
	/** PowerPointの座標単位。POIのスライドはEMU（1ポイント = 12700 EMU）ではなくポイントで扱えるためそのまま使う。 */
	private static final int DEFAULT_SLIDE_WIDTH_POINTS = 720;
	private static final int DEFAULT_SLIDE_HEIGHT_POINTS = 540;

	/**
	 * ページ単位のテキストからWord文書を組み立てる。
	 * <p>
	 * ページの区切りには改ページを入れる。段落を続けて並べるだけだと、元が何ページの文書だったのかが
	 * 完全に失われ、元PDFと突き合わせられなくなる。
	 *
	 * @param pageTexts PDF順のページ単位テキスト
	 * @return Word文書のbyte配列
	 * @throws OfficeProcessingException 組み立てに失敗した場合
	 */
	public byte[] writeWord(List<String> pageTexts) {
		try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			List<String> texts = CollectionUtils.emptyIfNull(pageTexts).stream().toList();
			for (int pageIndex = 0; pageIndex < texts.size(); pageIndex++) {
				appendWordPage(document, texts.get(pageIndex), pageIndex > 0);
			}
			LOGGER.info("PDFからWord文書を生成しました。pageCount={}", texts.size());
			document.write(outputStream);
			return outputStream.toByteArray();
		} catch (IOException | POIXMLException e) {
			throw new OfficeProcessingException("Word文書の生成に失敗しました。", e);
		}
	}

	/**
	 * 1ページ分のテキストをWord文書へ段落として追加する。
	 *
	 * @param document         追加先の文書
	 * @param pageText         ページのテキスト
	 * @param insertPageBreak  先頭に改ページを入れる場合true
	 */
	private void appendWordPage(XWPFDocument document, String pageText, boolean insertPageBreak) {
		if (insertPageBreak) {
			XWPFParagraph breakParagraph = document.createParagraph();
			breakParagraph.createRun().addBreak(BreakType.PAGE);
		}
		for (String line : splitLines(pageText)) {
			XWPFParagraph paragraph = document.createParagraph();
			XWPFRun run = paragraph.createRun();
			run.setText(line);
		}
	}

	/**
	 * ページ単位のテキストからExcelブックを組み立てる。
	 * <p>
	 * 1ページを1シートにし、テキストの1行を1行目の列へ入れる。表として復元はしない。
	 * PDFの描画命令には「どこからどこまでが1つの表か」という情報が無く、推定で列へ割ると
	 * 正しく分かれた行と壊れた行が混ざって、かえって直しにくい表になる。
	 *
	 * @param pageTexts PDF順のページ単位テキスト
	 * @return Excelブックのbyte配列
	 * @throws OfficeProcessingException 組み立てに失敗した場合
	 */
	public byte[] writeExcel(List<String> pageTexts) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			List<String> texts = CollectionUtils.emptyIfNull(pageTexts).stream().toList();
			for (int pageIndex = 0; pageIndex < texts.size(); pageIndex++) {
				appendExcelSheet(workbook, texts.get(pageIndex), pageIndex + 1);
			}
			if (workbook.getNumberOfSheets() == 0) {
				// シートが1枚も無いブックはExcelが開けない。空のPDFでも開けるファイルを返す。
				workbook.createSheet(SHEET_NAME_FORMAT.formatted(1));
			}
			LOGGER.info("PDFからExcelブックを生成しました。sheetCount={}", workbook.getNumberOfSheets());
			workbook.write(outputStream);
			return outputStream.toByteArray();
		} catch (IOException | POIXMLException e) {
			throw new OfficeProcessingException("Excelブックの生成に失敗しました。", e);
		}
	}

	/**
	 * 1ページ分のテキストをExcelブックへシートとして追加する。
	 *
	 * @param workbook   追加先のブック
	 * @param pageText   ページのテキスト
	 * @param pageNumber 1始まりのページ番号
	 */
	private void appendExcelSheet(XSSFWorkbook workbook, String pageText, int pageNumber) {
		Sheet sheet = workbook.createSheet(SHEET_NAME_FORMAT.formatted(pageNumber));
		List<String> lines = splitLines(pageText);
		for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
			Row row = sheet.createRow(lineIndex);
			row.createCell(0).setCellValue(lines.get(lineIndex));
		}
	}

	/**
	 * ページ画像からPowerPointプレゼンテーションを組み立てる。
	 * <p>
	 * スライドサイズは1ページ目の寸法に合わせる。PowerPointはプレゼンテーション全体で1つのスライドサイズしか
	 * 持てないため、ページごとにサイズが違うPDFでは2ページ目以降が余白付きで収まる。
	 *
	 * @param pageImages    PDF順のページ画像（PNG）
	 * @param widthPoints   1ページ目の幅（ポイント）
	 * @param heightPoints  1ページ目の高さ（ポイント）
	 * @return プレゼンテーションのbyte配列
	 * @throws OfficeProcessingException 組み立てに失敗した場合
	 */
	public byte[] writePowerPoint(List<byte[]> pageImages, float widthPoints, float heightPoints) {
		try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			Dimension slideSize = resolveSlideSize(widthPoints, heightPoints);
			slideShow.setPageSize(slideSize);
			for (byte[] pngBytes : CollectionUtils.emptyIfNull(pageImages)) {
				appendSlide(slideShow, pngBytes, slideSize);
			}
			LOGGER.info("PDFからPowerPointを生成しました。slideCount={}", slideShow.getSlides().size());
			slideShow.write(outputStream);
			return outputStream.toByteArray();
		} catch (IOException | POIXMLException e) {
			throw new OfficeProcessingException("PowerPointの生成に失敗しました。", e);
		}
	}

	/**
	 * スライドサイズを決める。
	 *
	 * @param widthPoints  1ページ目の幅（ポイント）
	 * @param heightPoints 1ページ目の高さ（ポイント）
	 * @return スライドサイズ
	 */
	private Dimension resolveSlideSize(float widthPoints, float heightPoints) {
		// ページが1枚も無い場合は寸法が0で来る。0のスライドはPowerPointが開けないため既定値へ倒す。
		if (widthPoints <= 0 || heightPoints <= 0) {
			return new Dimension(DEFAULT_SLIDE_WIDTH_POINTS, DEFAULT_SLIDE_HEIGHT_POINTS);
		}
		return new Dimension(Math.round(widthPoints), Math.round(heightPoints));
	}

	/**
	 * ページ画像を1枚のスライドとして追加する。
	 *
	 * @param slideShow 追加先のプレゼンテーション
	 * @param pngBytes  ページ画像のPNGバイト列
	 * @param slideSize スライドサイズ
	 */
	private void appendSlide(XMLSlideShow slideShow, byte[] pngBytes, Dimension slideSize) {
		XSLFPictureData pictureData = slideShow.addPicture(pngBytes, PictureData.PictureType.PNG);
		XSLFSlide slide = slideShow.createSlide();
		XSLFPictureShape pictureShape = slide.createPicture(pictureData);
		pictureShape.setAnchor(new Rectangle(0, 0, slideSize.width, slideSize.height));
	}

	/**
	 * テキストを行へ分割する。
	 * <p>
	 * 改行コードはCRLF / CR / LF のいずれも受け付ける。PDFから抽出したテキストの改行コードは
	 * 生成元のツールによって変わるため、1種類だけを前提にすると行が連結されたまま出力される。
	 *
	 * @param text 分割対象テキスト
	 * @return 行のリスト
	 */
	private List<String> splitLines(String text) {
		return List.of(StringUtils.defaultString(text).split(LINE_SEPARATOR_PATTERN));
	}
}
