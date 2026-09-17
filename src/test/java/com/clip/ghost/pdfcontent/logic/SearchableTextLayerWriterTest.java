package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.imagecontent.dto.OcrWordBox;

/**
 * {@link SearchableTextLayerWriter} の透明テキスト書き込みを検証するテスト。
 * <p>
 * 実OCR・実Tesseractは使わず、固定の単語ボックスをPDFBoxだけで書き込み、抽出可能性と
 * 描画モード（{@code RenderingMode.NEITHER}）を確認する。
 */
class SearchableTextLayerWriterTest {

	@Test
	@DisplayName("書き込んだ単語ボックスの文字列がPDFTextStripperで抽出できる")
	void writtenTextIsExtractableByTextStripper() throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);
			PDFont font = SearchableTextLayerWriter.loadInvisibleTextFont(document);
			List<OcrWordBox> wordBoxes = List.of(new OcrWordBox("HELLO", 33, 60, 172, 40),
					new OcrWordBox("12345", 226, 61, 144, 38));

			SearchableTextLayerWriter.writeInvisibleTextLayer(document, page, font, wordBoxes, 96);

			String extracted = new PDFTextStripper().getText(document);
			assertTrue(extracted.contains("HELLO"));
			assertTrue(extracted.contains("12345"));
		}
	}

	@Test
	@DisplayName("透明テキスト（RenderingMode.NEITHER）として書き込む")
	void writesTextWithInvisibleRenderingMode() throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);
			PDFont font = SearchableTextLayerWriter.loadInvisibleTextFont(document);
			List<OcrWordBox> wordBoxes = List.of(new OcrWordBox("HELLO", 33, 60, 172, 40));

			SearchableTextLayerWriter.writeInvisibleTextLayer(document, page, font, wordBoxes, 96);

			assertTrue(hasInvisibleRenderingModeOperator(page));
		}
	}

	@Test
	@DisplayName("単語ボックスが空の場合はページへ何も書き込まない")
	void doesNothingWhenWordBoxesAreEmpty() throws IOException {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage();
			document.addPage(page);
			PDFont font = SearchableTextLayerWriter.loadInvisibleTextFont(document);

			SearchableTextLayerWriter.writeInvisibleTextLayer(document, page, font, List.of(), 96);

			String extracted = new PDFTextStripper().getText(document);
			assertEquals("", extracted.strip());
		}
	}

	/**
	 * ページのコンテンツストリームに、描画しない（{@code Tr 3}）レンダリングモード指定が含まれるか判定する。
	 *
	 * @param page 判定対象のページ
	 * @return 含まれる場合はtrue
	 * @throws IOException コンテンツストリームの解析に失敗した場合
	 */
	private boolean hasInvisibleRenderingModeOperator(PDPage page) throws IOException {
		// PDFStreamParserはAutoCloseableを実装しないため、try-with-resourcesではなくtry/finallyで閉じる。
		PDFStreamParser streamParser = new PDFStreamParser(page);
		List<Object> tokens;
		try {
			tokens = streamParser.parse();
		} finally {
			streamParser.close();
		}
		for (int index = 1; index < tokens.size(); index++) {
			Object token = tokens.get(index);
			if (token instanceof Operator operator && "Tr".equals(operator.getName())
					&& tokens.get(index - 1) instanceof COSBase operand && operand instanceof COSNumber number
					&& number.intValue() == 3) {
				return true;
			}
		}
		return false;
	}
}
