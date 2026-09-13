package com.clip.ghost.officecontent.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import lombok.NoArgsConstructor;

/**
 * PowerPointプレゼンテーション（.pptx）をMarkdownへ起こす内部クラス。
 * <p>
 * スライドごとに見出しを付け、スライド内のテキストと表を配置順に出す。
 * <p>
 * 図形の位置関係やデザインは再現しない。スライドの見た目が要るなら、Markdownではなく
 * スライドを画像化してPDFにする経路（{@code POST /pdfFromOffice}）を使う。
 */
@NoArgsConstructor
final class PowerPointMarkdownReader {
	private static final String SLIDE_HEADING_FORMAT = "## スライド %d";
	private static final String EMPTY_SLIDE_NOTE = "（テキストの無いスライド）";

	/**
	 * PowerPointプレゼンテーションをスライド単位のMarkdownへ変換する。
	 *
	 * @param inputStream プレゼンテーションの入力ストリーム
	 * @return Markdown本文
	 * @throws IOException プレゼンテーションを読み込めない場合
	 */
	String read(InputStream inputStream) throws IOException {
		try (XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
			List<String> blocks = new ArrayList<>();
			List<XSLFSlide> slides = slideShow.getSlides();
			for (int slideIndex = 0; slideIndex < slides.size(); slideIndex++) {
				appendSlide(blocks, slides.get(slideIndex), slideIndex + 1);
			}
			return MarkdownBlockJoiner.join(blocks);
		}
	}

	/**
	 * 1スライド分の見出しと本文をMarkdownブロックへ追加する。
	 *
	 * @param blocks      追加先のブロックリスト
	 * @param slide       スライド
	 * @param slideNumber 1始まりのスライド番号
	 */
	private void appendSlide(List<String> blocks, XSLFSlide slide, int slideNumber) {
		blocks.add(SLIDE_HEADING_FORMAT.formatted(slideNumber));
		List<String> slideBlocks = new ArrayList<>();
		for (XSLFShape shape : slide.getShapes()) {
			appendShape(slideBlocks, shape);
		}
		if (CollectionUtils.isEmpty(slideBlocks)) {
			// 画像だけのスライドも見出しを残す。抜けたのか元から無いのかを利用者が区別できるようにする。
			blocks.add(EMPTY_SLIDE_NOTE);
			return;
		}
		blocks.addAll(slideBlocks);
	}

	/**
	 * 図形1つ分をMarkdownブロックとして追加する。
	 *
	 * @param blocks 追加先のブロックリスト
	 * @param shape  図形
	 */
	private void appendShape(List<String> blocks, XSLFShape shape) {
		if (shape instanceof XSLFTable table) {
			appendTable(blocks, table);
			return;
		}
		// 表はXSLFTextShapeも兼ねるため、表の判定を先に行う。逆にするとセルが連結された1つの段落になる。
		if (shape instanceof XSLFTextShape textShape) {
			String text = StringUtils.trim(textShape.getText());
			if (StringUtils.isNotEmpty(text)) {
				blocks.add(text);
			}
		}
	}

	/**
	 * 表をGFMの表としてMarkdownブロックへ追加する。
	 *
	 * @param blocks 追加先のブロックリスト
	 * @param table  表
	 */
	private void appendTable(List<String> blocks, XSLFTable table) {
		List<List<String>> rows = new ArrayList<>();
		for (XSLFTableRow tableRow : table.getRows()) {
			List<String> cells = new ArrayList<>();
			for (XSLFTableCell cell : tableRow.getCells()) {
				cells.add(StringUtils.trim(cell.getText()));
			}
			rows.add(cells);
		}
		String markdownTable = MarkdownTableBuilder.build(rows);
		if (StringUtils.isNotEmpty(markdownTable)) {
			blocks.add(markdownTable);
		}
	}
}
