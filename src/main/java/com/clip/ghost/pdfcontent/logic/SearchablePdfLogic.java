package com.clip.ghost.pdfcontent.logic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clip.ghost.imagecontent.dto.OcrWordBox;
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.enums.SearchablePdfMode;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * 検索可能PDF（OCRサンドイッチPDF）の生成を担当する内部ロジック。
 * <p>
 * {@code SearchablePdfMode} が決めた対象ページだけを画像化してOCRへ渡し、認識結果を{@link SearchableTextLayerWriter}で
 * 透明テキスト層として書き戻す。対象ページ数の上限チェックは画像化・OCR呼び出しより前に行い、
 * 超過時は1ページも処理せずに例外で止める（{@code PdfDocumentAnalysisLogic}の`mode=AUTO`/`VISION`と同じ考え方）。
 */
@NoArgsConstructor
final class SearchablePdfLogic {
	private static final Logger LOGGER = LoggerFactory.getLogger(SearchablePdfLogic.class);

	/**
	 * 検索可能PDFを生成する。
	 * <p>
	 * 入力ファイルは削除しない。呼び出し後の削除はFacade側へ委ねる。
	 *
	 * @param inputPath  読み込むPDFのパス
	 * @param outputPath 生成した検索可能PDFの出力先パス
	 * @param mode       OCR対象ページを決める変換モード
	 * @param renderDpi  OCR用に画像化する解像度（DPI）
	 * @param maxPages   OCR対象ページ数の上限
	 * @param pageOcr    画像化した1ページ分を単語ボックスへ変換する処理
	 * @throws PdfPageLimitExceededException OCR対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込み、画像化、透明テキスト書き込み、または保存に失敗した場合
	 */
	void createSearchablePdf(Path inputPath, Path outputPath, SearchablePdfMode mode, int renderDpi, int maxPages,
			SearchablePdfPageOcr pageOcr) {
		try (PDDocument document = PdfDocumentLoader.load(inputPath)) {
			List<String> pageTexts = extractPageTexts(document);
			List<Integer> targetPageNumbers = collectOcrTargetPageNumbers(pageTexts, mode);
			if (targetPageNumbers.size() > maxPages) {
				LOGGER.warn("OCR対象ページ数が上限を超えたため検索可能PDFを生成しません。mode={}, targetPageCount={}, maxPages={}",
						mode.getKey(), targetPageNumbers.size(), maxPages);
				throw new PdfPageLimitExceededException(targetPageNumbers.size(), maxPages,
						mode.describeConversionTarget());
			}
			LOGGER.info("検索可能PDFの生成を開始します。mode={}, targetPageCount={}, pageCount={}, renderDpi={}", mode.getKey(),
					targetPageNumbers.size(), pageTexts.size(), renderDpi);
			writeSearchableTextLayers(document, targetPageNumbers, renderDpi, pageOcr);
			document.save(outputPath.toFile());
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("検索可能PDFの生成に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * 対象ページを画像化してOCRへ渡し、認識結果を透明テキスト層としてページへ書き込む。
	 * <p>
	 * フォントは1ドキュメントにつき1回だけ読み込み、対象ページ全部で使い回す。
	 *
	 * @param document         編集対象のPDFドキュメント
	 * @param targetPageNumbers OCR対象の1始まりページ番号
	 * @param renderDpi        OCR用に画像化する解像度（DPI）
	 * @param pageOcr          画像化した1ページ分を単語ボックスへ変換する処理
	 * @throws IOException 画像化または透明テキスト書き込みに失敗した場合
	 */
	private void writeSearchableTextLayers(PDDocument document, List<Integer> targetPageNumbers, int renderDpi,
			SearchablePdfPageOcr pageOcr) throws IOException {
		if (CollectionUtils.isEmpty(targetPageNumbers)) {
			return;
		}
		PDFRenderer renderer = new PDFRenderer(document);
		PDFont font = SearchableTextLayerWriter.loadInvisibleTextFont(document);
		for (Integer pageNumber : targetPageNumbers) {
			// PDFBoxのページ指定は0始まりのため、1始まりのページ番号から開始ページ番号を引く。
			int pageIndex = pageNumber - PdfConstants.START_PAGE;
			byte[] pngBytes = renderPageToPng(renderer, pageIndex, renderDpi);
			List<OcrWordBox> wordBoxes = pageOcr.recognize(pngBytes);
			SearchableTextLayerWriter.writeInvisibleTextLayer(document, document.getPage(pageIndex), font, wordBoxes,
					renderDpi);
		}
	}

	/**
	 * 読み込み済みのPDFドキュメントからページ単位のテキストを抽出する。
	 *
	 * @param document 読み込み済みのPDFドキュメント
	 * @return PDF順のページ単位テキスト
	 * @throws IOException テキスト抽出に失敗した場合
	 */
	private List<String> extractPageTexts(PDDocument document) throws IOException {
		PDFTextStripper textStripper = new PDFTextStripper();
		int totalPages = document.getNumberOfPages();
		List<String> pageTexts = new ArrayList<>(totalPages);
		for (int pageNumber = PdfConstants.START_PAGE; pageNumber <= totalPages; pageNumber++) {
			textStripper.setStartPage(pageNumber);
			textStripper.setEndPage(pageNumber);
			pageTexts.add(textStripper.getText(document));
		}
		return pageTexts;
	}

	/**
	 * OCR対象のページ番号をモードに応じて抽出する。
	 * <p>
	 * 対象ページ数はそのままOCR呼び出し回数（コストガードの対象）になるため、上限チェックで数える集合と
	 * 実際に処理する集合をこの1メソッドで確定させる。
	 *
	 * @param pageTexts PDF順のページ単位テキスト
	 * @param mode      OCR対象ページを決める変換モード
	 * @return OCR対象のページ番号（1始まり）
	 */
	private List<Integer> collectOcrTargetPageNumbers(List<String> pageTexts, SearchablePdfMode mode) {
		if (mode.convertsEveryPage()) {
			return IntStream.rangeClosed(PdfConstants.START_PAGE, pageTexts.size()).boxed().toList();
		}
		return collectBlankPageNumbers(pageTexts);
	}

	/**
	 * 文字レイヤーが空白のページ番号を抽出する。
	 * <p>
	 * {@code PdfDocumentAnalysisLogic}の`mode=AUTO`と同じ「文字レイヤーが空か」という判定基準だが、
	 * 対象がMarkdown下書きではなくPDF自体の書き換えという別の目的のため、判定はこのクラス専用に持つ
	 * （意図的な重複。1行の単純な判定であり、共有するための抽象化を新たに作るほどではない）。
	 *
	 * @param pageTexts PDF順のページ単位テキスト
	 * @return 文字レイヤーが空白のページ番号（1始まり）
	 */
	private List<Integer> collectBlankPageNumbers(List<String> pageTexts) {
		return IntStream.rangeClosed(PdfConstants.START_PAGE, pageTexts.size())
				.filter(pageNumber -> StringUtils.isBlank(pageTexts.get(pageNumber - PdfConstants.START_PAGE))).boxed()
				.toList();
	}

	/**
	 * 指定ページをPNGバイト列へ画像化する。
	 *
	 * @param renderer  PDFレンダラー
	 * @param pageIndex 0始まりのページインデックス
	 * @param renderDpi 解像度（DPI）
	 * @return PNGバイト列
	 * @throws IOException レンダリングまたはPNGエンコードに失敗した場合
	 */
	private byte[] renderPageToPng(PDFRenderer renderer, int pageIndex, int renderDpi) throws IOException {
		BufferedImage image = renderer.renderImageWithDPI(pageIndex, renderDpi, ImageType.RGB);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(image, "png", outputStream);
		return outputStream.toByteArray();
	}
}
