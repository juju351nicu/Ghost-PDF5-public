package com.clip.ghost.pdfcontent.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.clip.ghost.imagecontent.dto.OcrWordBox;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * OCRで認識した単語ボックスを、PDFページへ透明テキスト層として書き込む内部クラス（"OCRサンドイッチPDF"）。
 * <p>
 * ページの見た目（既存の描画内容）は変更せず、{@link RenderingMode#NEITHER} を設定した透明テキストを
 * 追記するだけにする。フォントは新規調達せず、Markdown to PDF（{@code MarkdownPdfRenderer}）で
 * 埋め込み・ライセンス確認済みの同梱Noto Sans JPをそのまま使う。透明テキストでもPDFBoxはグリフ幅計算の
 * ためにフォントを要求するため、フォント自体は必須。
 */
@NoArgsConstructor
final class SearchableTextLayerWriter {
	private static final Logger LOGGER = LoggerFactory.getLogger(SearchableTextLayerWriter.class);
	private static final String BUNDLED_FONT_RESOURCE = "fonts/NotoSansJP-Regular.ttf";
	private static final float POINTS_PER_INCH = 72f;
	private static final float GLYPH_SPACE_UNITS = 1000f;
	private static final float MIN_FONT_SIZE = 1f;
	private static final float MIN_HORIZONTAL_SCALING = 1f;
	private static final float MAX_HORIZONTAL_SCALING = 1000f;
	private static final float DEFAULT_HORIZONTAL_SCALING = 100f;

	/**
	 * 透明テキスト用に、同梱Noto Sans JPをドキュメントへ埋め込んで読み込む。
	 * <p>
	 * 1ドキュメントにつき1回だけ読み込み、対象ページ全部でこのフォントを使い回す。ページごとに読み込み直すと、
	 * 同じフォントプログラムが重複して埋め込まれ、ファイルサイズが不必要に増える。
	 *
	 * @param document 埋め込み先のPDFドキュメント
	 * @return 埋め込み済みフォント
	 * @throws PdfProcessingException フォントの読み込みに失敗した場合
	 */
	static PDFont loadInvisibleTextFont(PDDocument document) {
		try (InputStream fontStream = new ClassPathResource(BUNDLED_FONT_RESOURCE).getInputStream()) {
			// embedSubset=falseでフォント全体を埋め込む。trueにすると、このNoto Sans JPではPDFBoxの
			// サブセット化でグリフIDが2つずれ、書き込んだ文字が別の文字として抽出される不具合が実測で見つかった
			// （例: "HELLO"が"FCJJM"として抽出される）。ファイルサイズは増えるが、検索可能PDFの目的は
			// 検索・コピペの正しさであり、正しさを優先する。
			return PDType0Font.load(document, fontStream, false);
		} catch (IOException e) {
			throw new PdfProcessingException("検索可能PDF用フォントの読み込みに失敗しました。", e);
		}
	}

	/**
	 * 1ページ分の単語ボックスを、透明テキスト層としてページへ書き込む。
	 * <p>
	 * 既存のページ内容（スキャン画像等）は変更せず、{@link PDPageContentStream.AppendMode#APPEND} で
	 * 透明テキスト層だけを追記する。単語ごとに、OCRのボックス幅とフォントの自然な文字幅がずれる場合の
	 * コピー時の余分な空白・文字詰まりを防ぐため、水平スケーリングでボックス幅に合わせる。
	 *
	 * @param document  対象のPDFドキュメント
	 * @param page      書き込み対象のページ
	 * @param font      透明テキストに使うフォント（{@link #loadInvisibleTextFont(PDDocument)} で読み込んだもの）
	 * @param wordBoxes OCRで認識した単語ボックス（画像左上原点・ピクセル座標）
	 * @param renderDpi OCRに使った画像の解像度（DPI）。PDF座標への変換に使う
	 * @throws PdfProcessingException 透明テキスト層の書き込みに失敗した場合
	 */
	static void writeInvisibleTextLayer(PDDocument document, PDPage page, PDFont font, List<OcrWordBox> wordBoxes,
			int renderDpi) {
		if (CollectionUtils.isEmpty(wordBoxes)) {
			return;
		}
		float pageHeightPt = page.getMediaBox().getHeight();
		try (PDPageContentStream contentStream = new PDPageContentStream(document, page,
				PDPageContentStream.AppendMode.APPEND, true)) {
			contentStream.beginText();
			contentStream.setRenderingMode(RenderingMode.NEITHER);
			for (OcrWordBox wordBox : wordBoxes) {
				writeWord(contentStream, font, wordBox, renderDpi, pageHeightPt);
			}
			contentStream.endText();
		} catch (IOException e) {
			throw new PdfProcessingException("検索可能PDFの透明テキスト層の書き込みに失敗しました。", e);
		}
	}

	/**
	 * 1単語分の透明テキストを書き込む。
	 * <p>
	 * このフォントで表現できない文字（OCRの誤認識による記号等）を含む単語は、そのページの他の単語まで
	 * 巻き込んで失敗させないよう、警告ログに記録してスキップする。
	 *
	 * @param contentStream ページのコンテンツストリーム（{@code beginText}済み）
	 * @param font          透明テキストに使うフォント
	 * @param wordBox       書き込む単語ボックス
	 * @param renderDpi     OCRに使った画像の解像度（DPI）
	 * @param pageHeightPt  ページの高さ（pt）
	 * @throws IOException コンテンツストリームへの書き込みに失敗した場合
	 */
	private static void writeWord(PDPageContentStream contentStream, PDFont font, OcrWordBox wordBox, int renderDpi,
			float pageHeightPt) throws IOException {
		if (StringUtils.isBlank(wordBox.text())) {
			return;
		}
		try {
			float pdfX = toPoints(wordBox.left(), renderDpi);
			float boxHeightPt = toPoints(wordBox.height(), renderDpi);
			// ボックス下端をベースライン位置の近似とする。画像は左上原点、PDFは左下原点のためYを反転する。
			float pdfY = pageHeightPt - toPoints(wordBox.top() + wordBox.height(), renderDpi);
			float fontSize = Math.max(boxHeightPt, MIN_FONT_SIZE);
			contentStream.setFont(font, fontSize);
			contentStream.setHorizontalScaling(computeHorizontalScaling(font, wordBox, renderDpi, fontSize));
			contentStream.setTextMatrix(Matrix.getTranslateInstance(pdfX, pdfY));
			contentStream.showText(wordBox.text());
		} catch (IllegalArgumentException e) {
			// フォントが表現できない文字（OCRの誤認識による記号等）。このページの他の単語は続行する。
			LOGGER.warn("透明テキストの書き込みに使えない文字を含む単語をスキップしました。");
		}
	}

	/**
	 * OCRのボックス幅にフォントの自然な文字幅を合わせるための水平スケーリング（{@code Tz}）を計算する。
	 * <p>
	 * ズレを放置すると、コピー＆ペースト時に余分な空白が入ったり文字が詰まったりする（Tesseractの
	 * 既知の不具合として報告例がある）。自然幅が計算できない場合は等倍（100%）にする。
	 *
	 * @param font      使用するフォント
	 * @param wordBox   対象の単語ボックス
	 * @param renderDpi OCRに使った画像の解像度（DPI）
	 * @param fontSize  適用するフォントサイズ
	 * @return 水平スケーリング（パーセント）
	 * @throws IOException フォントの文字幅計算に失敗した場合
	 */
	private static float computeHorizontalScaling(PDFont font, OcrWordBox wordBox, int renderDpi, float fontSize)
			throws IOException {
		float targetWidthPt = toPoints(wordBox.width(), renderDpi);
		float naturalWidthPt = font.getStringWidth(wordBox.text()) / GLYPH_SPACE_UNITS * fontSize;
		if (naturalWidthPt <= 0) {
			return DEFAULT_HORIZONTAL_SCALING;
		}
		float scaling = targetWidthPt / naturalWidthPt * DEFAULT_HORIZONTAL_SCALING;
		return Math.clamp(scaling, MIN_HORIZONTAL_SCALING, MAX_HORIZONTAL_SCALING);
	}

	/**
	 * OCR入力画像のピクセル値を、指定DPIでPDFのpt単位へ変換する。
	 *
	 * @param pixels    ピクセル値
	 * @param renderDpi 画像化した解像度（DPI）
	 * @return pt単位の値
	 */
	private static float toPoints(double pixels, int renderDpi) {
		return (float) (pixels * POINTS_PER_INCH / renderDpi);
	}
}
