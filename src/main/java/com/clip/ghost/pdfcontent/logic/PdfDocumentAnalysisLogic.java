package com.clip.ghost.pdfcontent.logic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.dto.PdfPageThumbnail;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * PDFドキュメントのメタデータ取得とテキスト抽出を扱う内部ロジック。
 * <p>
 * 入力ファイルのライフサイクルは呼び出し元が管理し、このクラスはPDFBoxドキュメントのcloseだけを担当する。
 */
@NoArgsConstructor
final class PdfDocumentAnalysisLogic {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfDocumentAnalysisLogic.class);

	/**
	 * PDFのファイル名、サイズ、ページ数、暗号化状態を取得する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param fileName  アップロード時の元ファイル名
	 * @param fileSize  アップロードファイルサイズ
	 * @return PDFの基本情報
	 * @throws PdfProcessingException PDFの読み込みまたはメタデータ取得に失敗した場合
	 */
	PdfMetadataResponse getPdfMetadata(Path inputPath, String fileName, long fileSize) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			PdfMetadataResponse response = new PdfMetadataResponse();
			response.setFileName(fileName);
			response.setFileSize(fileSize);
			response.setPageCount(document.getNumberOfPages());
			response.setEncrypted(document.isEncrypted());
			return response;
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFメタデータの取得に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFの全ページからテキストを抽出し、基本情報とともに返す。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param fileName  アップロード時の元ファイル名
	 * @param fileSize  アップロードファイルサイズ
	 * @return PDFから抽出したテキストと基本情報
	 * @throws PdfProcessingException PDFの読み込みまたはテキスト抽出に失敗した場合
	 */
	PdfTextResponse extractPdfText(Path inputPath, String fileName, long fileSize) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			PDFTextStripper textStripper = new PDFTextStripper();
			PdfTextResponse response = new PdfTextResponse();
			response.setFileName(fileName);
			response.setFileSize(fileSize);
			response.setPageCount(document.getNumberOfPages());
			response.setText(textStripper.getText(document));
			return response;
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFテキスト抽出に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFからページ単位でテキストを抽出する。
	 * <p>
	 * 空ページも省略せず、PDFの総ページ数と同じ要素数を返す。入力ファイルは削除しない。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @return PDF順のページ単位テキスト
	 * @throws PdfProcessingException PDFの読み込みまたはテキスト抽出に失敗した場合
	 */
	List<String> extractPdfPageTexts(Path inputPath) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			return extractPageTexts(document);
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFページ単位テキスト抽出に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFからページ単位でテキストを抽出し、文字を取得できないページはPNGへ画像化して変換器へ渡す。
	 * <p>
	 * 1つの {@code PDDocument} で2段の走査を行う。1段目はテキスト抽出だけで、画像化も変換もしないため安価。
	 * そこで確定した変換対象ページ数が上限を超える場合は、1ページも画像化せず、変換器を1度も呼ばずに例外で止める。
	 * 外部AIの課金は変換器の呼び出しで発生するため、この順序がコストガードの前提になる。
	 * <p>
	 * 2段目は対象ページだけを画像化し、その場で変換してPNGの参照を捨てる。同時にメモリへ載る画像は1ページ分に収まる。
	 * 2段目は {@code PDDocument} を開いたまま変換器をページ数分だけ呼ぶため、変換に時間がかかる間はPDFがメモリに載り続けるが、
	 * 呼び出し回数が {@code maxPages} で有界になるため許容する。
	 * <p>
	 * ページ順を維持し、空ページも省略しない。入力ファイルは削除しない。
	 * <p>
	 * 変換器が失敗したページは、そのページだけを失敗として記録し、残りのページの変換を続ける。
	 * 1ページの失敗で全体を捨てると、外部AIへ課金して得た成功分のページまで利用者へ届かなくなるため。
	 * ただし変換対象があって1ページも成功しなかった場合は部分的成功ではないため、最初の失敗をそのまま伝播する。
	 *
	 * @param inputPath          読み込むPDFのパス
	 * @param renderDpi          画像化する解像度（DPI）
	 * @param maxPages           画像変換にかけるページ数の上限
	 * @param pageImageConverter 画像化した1ページ分をテキストへ変換する処理
	 * @return PDF順のページ内容（テキストと、変換したページの変換結果・変換失敗）
	 * @throws PdfPageLimitExceededException 変換対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込み、テキスト抽出、または画像化に失敗した場合
	 */
	List<PdfPageContent> extractPdfPageContents(Path inputPath, int renderDpi, int maxPages,
			PdfPageImageConverter pageImageConverter) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			List<String> pageTexts = extractPageTexts(document);
			List<Integer> renderTargetPages = collectBlankPageNumbers(pageTexts);
			if (renderTargetPages.size() > maxPages) {
				LOGGER.warn("変換対象ページ数が上限を超えたため画像変換を行いません。targetPageCount={}, maxPages={}", renderTargetPages.size(),
						maxPages);
				throw new PdfPageLimitExceededException(renderTargetPages.size(), maxPages);
			}
			LOGGER.info("画像PDFのページ変換を開始します。targetPageCount={}, pageCount={}, renderDpi={}", renderTargetPages.size(),
					pageTexts.size(), renderDpi);

			PDFRenderer renderer = new PDFRenderer(document);
			Map<Integer, String> convertedTexts = new HashMap<>();
			List<Integer> failedPageNumbers = new ArrayList<>();
			RuntimeException firstFailure = null;
			for (Integer pageNumber : renderTargetPages) {
				// PDFBoxのレンダリングは0始まりのため、1始まりのページ番号から開始ページ番号を引く。
				byte[] pngBytes = renderPageToPng(renderer, pageNumber - PdfConstants.START_PAGE, renderDpi);
				try {
					convertedTexts.put(pageNumber, pageImageConverter.convert(pngBytes));
				} catch (RuntimeException e) {
					// 画像化（renderPageToPng）の失敗はPDF自体を読めていない疑いがあるため従来どおり全体を止め、
					// 変換器の失敗だけをページ単位の部分失敗として扱う。
					failedPageNumbers.add(pageNumber);
					if (firstFailure == null) {
						firstFailure = e;
					}
					LOGGER.warn("ページの画像変換に失敗したため、このページを空本文として続行します。pageNumber={}", pageNumber, e);
				}
			}
			if (CollectionUtils.isNotEmpty(renderTargetPages) && failedPageNumbers.size() == renderTargetPages.size()) {
				// 全滅は「一部が失敗した成功」ではないため、1ページ目の失敗で止まっていた従来どおり例外にする。
				// 独自例外へ包み直すとHTTP statusが変わるため、最初の失敗をそのまま投げる。
				LOGGER.warn("画像変換が全ページ失敗したため処理を中断します。targetPageCount={}", renderTargetPages.size());
				throw firstFailure;
			}
			return buildPageContents(pageTexts, convertedTexts, failedPageNumbers);
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFページ内容の抽出に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFの全ページを低解像度で画像化し、ページ選択UI用のサムネイルを返す。
	 * <p>
	 * ページ数が上限を超える場合は1ページも画像化せずに例外で止める。全ページ分を1レスポンスで返すため、
	 * ページ数がそのままレスポンスサイズに比例するのを防ぐ。
	 * <p>
	 * 1ページずつ画像化してdata URIへ変換し、{@code BufferedImage} とPNGバイト列の参照は都度捨てる。
	 * 同時にメモリへ載る画像を1ページ分に抑えるため。
	 * <p>
	 * 画像は {@code ImageType.RGB} で作る。グレースケールにすればサイズは減るが、色で区別している図や
	 * 見出しがページ選択時に判別しづらくなる。サムネイルは「どのページか」を見分けるためのものなので色を残す。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param renderDpi 画像化する解像度（DPI）
	 * @param maxPages  サムネイルを返すページ数の上限
	 * @return PDF順のページ単位サムネイル
	 * @throws PdfPageLimitExceededException 総ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込みまたは画像化に失敗した場合
	 */
	List<PdfPageThumbnail> extractPdfThumbnails(Path inputPath, int renderDpi, int maxPages) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			int totalPages = document.getNumberOfPages();
			if (totalPages > maxPages) {
				LOGGER.warn("総ページ数が上限を超えたためサムネイルを生成しません。pageCount={}, maxPages={}", totalPages, maxPages);
				throw new PdfPageLimitExceededException(totalPages, maxPages);
			}
			LOGGER.info("サムネイルの生成を開始します。pageCount={}, renderDpi={}", totalPages, renderDpi);

			PDFRenderer renderer = new PDFRenderer(document);
			List<PdfPageThumbnail> thumbnails = new ArrayList<>(totalPages);
			for (int pageNumber = PdfConstants.START_PAGE; pageNumber <= totalPages; pageNumber++) {
				thumbnails.add(renderThumbnail(renderer, pageNumber, renderDpi));
			}
			return thumbnails;
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFサムネイルの生成に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * 指定ページのサムネイルを生成する。
	 *
	 * @param renderer   PDFレンダラー
	 * @param pageNumber 画面・API仕様の1始まりページ番号
	 * @param renderDpi  解像度（DPI）
	 * @return ページ単位サムネイル
	 * @throws IOException レンダリングまたはPNGエンコードに失敗した場合
	 */
	private PdfPageThumbnail renderThumbnail(PDFRenderer renderer, int pageNumber, int renderDpi) throws IOException {
		// PDFBoxのレンダリングは0始まりのため、1始まりのページ番号から開始ページ番号を引く。
		BufferedImage image = renderPageImage(renderer, pageNumber - PdfConstants.START_PAGE, renderDpi);
		String dataUri = PdfConstants.BASE64_PNG + Base64.getEncoder().encodeToString(toPngBytes(image));
		return new PdfPageThumbnail(pageNumber, dataUri, image.getWidth(), image.getHeight());
	}

	/**
	 * 読み込み済みのPDFドキュメントからページ単位のテキストを抽出する。
	 * <p>
	 * PDFBoxのページ指定は1始まりのため、開始・終了ページを同じ値に設定してPDF順に抽出する。
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
	 * 文字レイヤーが空白のページ番号を抽出する。
	 * <p>
	 * 画像化するかどうかの判定はこのメソッドだけが持つ。上限チェックで数えるページ集合と実際に変換するページ集合が
	 * ズレると、コストガードが意味を失うため。
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
	 * ページ単位テキストと変換結果からページ内容を組み立てる。
	 *
	 * @param pageTexts         PDF順のページ単位テキスト
	 * @param convertedTexts    変換したページ番号と変換結果
	 * @param failedPageNumbers 変換に失敗したページ番号（1始まり）
	 * @return PDF順のページ内容
	 */
	private List<PdfPageContent> buildPageContents(List<String> pageTexts, Map<Integer, String> convertedTexts,
			List<Integer> failedPageNumbers) {
		return IntStream.rangeClosed(PdfConstants.START_PAGE, pageTexts.size())
				.mapToObj(pageNumber -> new PdfPageContent(pageNumber,
						pageTexts.get(pageNumber - PdfConstants.START_PAGE), convertedTexts.get(pageNumber),
						failedPageNumbers.contains(pageNumber)))
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
		return toPngBytes(renderPageImage(renderer, pageIndex, renderDpi));
	}

	/**
	 * 指定ページを画像へレンダリングする。
	 * <p>
	 * 画像の幅・高さを使う呼び出し元があるため、PNGへのエンコードとは分けている。
	 *
	 * @param renderer  PDFレンダラー
	 * @param pageIndex 0始まりのページインデックス
	 * @param renderDpi 解像度（DPI）
	 * @return レンダリングした画像
	 * @throws IOException レンダリングに失敗した場合
	 */
	private BufferedImage renderPageImage(PDFRenderer renderer, int pageIndex, int renderDpi) throws IOException {
		return renderer.renderImageWithDPI(pageIndex, renderDpi, ImageType.RGB);
	}

	/**
	 * 画像をPNGバイト列へエンコードする。
	 *
	 * @param image エンコードする画像
	 * @return PNGバイト列
	 * @throws IOException PNGエンコードに失敗した場合
	 */
	private byte[] toPngBytes(BufferedImage image) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(image, "png", outputStream);
		return outputStream.toByteArray();
	}
}
