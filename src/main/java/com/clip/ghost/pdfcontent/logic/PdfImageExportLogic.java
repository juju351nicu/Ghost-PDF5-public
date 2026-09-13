package com.clip.ghost.pdfcontent.logic;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfPageImage;
import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * PDFのページを画像ファイルへ書き出す内部クラス。
 * <p>
 * 出力はページごとに1ファイルのZIPへまとめる。ページ数が可変のため、単一ファイルで返すと
 * 「1ページのときだけ画像、複数ページならZIP」とレスポンス形式が入力に依存してしまう。
 * <p>
 * 1ページ描画するたびにZIPへ書き出し、{@code BufferedImage} の参照を捨てる。全ページを保持すると
 * 高解像度・多ページで確実にヒープを使い切る。
 */
@NoArgsConstructor
final class PdfImageExportLogic {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfImageExportLogic.class);
	private static final String PAGE_IMAGE_FILE_NAME_FORMAT = "page-%03d.%s";
	/** 1インチあたりのポイント数。PDFの座標系は72分の1インチを1ポイントとする。 */
	private static final float POINTS_PER_INCH = 72f;

	/**
	 * PDFのページを画像化し、ZIPへ格納する。
	 *
	 * @param inputPath  読み込むPDFのパス
	 * @param outputPath 画像ZIPの出力先パス
	 * @param format     出力する画像形式
	 * @param renderDpi  画像化する解像度（DPI）
	 * @param maxPages   画像化するページ数の上限
	 * @param imagePages 画像化する1始まりのページ番号。空の場合は全ページを画像化する
	 * @throws PdfPageLimitExceededException 対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込み、画像化、ZIP保存に失敗した場合
	 */
	void exportPdfImages(Path inputPath, Path outputPath, PdfImageFormat format, int renderDpi, int maxPages,
			List<Integer> imagePages) {
		try (PDDocument document = PdfDocumentLoader.load(inputPath)) {
			List<Integer> targetPageNumbers = selectImagePageNumbers(document, imagePages);
			// ZIPを開く前に上限を判定する。開いてから弾くと、中身の無いZIPが一時ファイルとして残る。
			validatePageCount(targetPageNumbers.size(), maxPages);
			LOGGER.info("PDFのページ画像化を開始します。pageCount={}, format={}, renderDpi={}", targetPageNumbers.size(),
					format.getKey(), renderDpi);

			PDFRenderer renderer = new PDFRenderer(document);
			try (OutputStream outputStream = Files.newOutputStream(outputPath);
					ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
				for (Integer pageNumber : targetPageNumbers) {
					addPageImageToZip(zipOutputStream, renderer, pageNumber, format, renderDpi);
				}
			}
		} catch (IllegalArgumentException | IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFのページ画像化に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFのページを画像化して読み出す。
	 * <p>
	 * ZIPへ書き出す {@link #exportPdfImages} と違い、結果をメモリ上のリストで返す。
	 * PowerPointのスライドのように「全ページ分を1つのファイルへ組み立てる」用途では、
	 * 途中でファイルへ落とす意味が無いため。同時にメモリへ載る量は {@code maxPages} で頭打ちにする。
	 *
	 * @param inputPath  読み込むPDFのパス
	 * @param format     画像のエンコード形式
	 * @param renderDpi  画像化する解像度（DPI）
	 * @param maxPages   画像化するページ数の上限
	 * @param imagePages 画像化する1始まりのページ番号。空の場合は全ページを画像化する
	 * @return PDF順のページ画像
	 * @throws PdfPageLimitExceededException 対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込みまたは画像化に失敗した場合
	 */
	List<PdfPageImage> readPdfPageImages(Path inputPath, PdfImageFormat format, int renderDpi, int maxPages,
			List<Integer> imagePages) {
		try (PDDocument document = PdfDocumentLoader.load(inputPath)) {
			List<Integer> targetPageNumbers = selectImagePageNumbers(document, imagePages);
			validatePageCount(targetPageNumbers.size(), maxPages);
			LOGGER.info("PDFのページ画像化を開始します。pageCount={}, format={}, renderDpi={}", targetPageNumbers.size(),
					format.getKey(), renderDpi);

			PDFRenderer renderer = new PDFRenderer(document);
			List<PdfPageImage> pageImages = new ArrayList<>(targetPageNumbers.size());
			for (Integer pageNumber : targetPageNumbers) {
				BufferedImage image = renderPage(renderer, pageNumber, renderDpi);
				pageImages.add(new PdfPageImage(pageNumber, encodeImage(image, format),
						toPoints(image.getWidth(), renderDpi), toPoints(image.getHeight(), renderDpi)));
			}
			return pageImages;
		} catch (IllegalArgumentException | IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFのページ画像化に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * 画素数を解像度からポイント値へ換算する。
	 *
	 * @param pixels    画素数
	 * @param renderDpi 描画に使った解像度（DPI）
	 * @return ポイント値
	 */
	private float toPoints(int pixels, int renderDpi) {
		return pixels * POINTS_PER_INCH / renderDpi;
	}

	/**
	 * 対象ページ数が上限を超えていないか検証する。
	 *
	 * @param targetPageCount 対象ページ数
	 * @param maxPages        ページ数の上限
	 * @throws PdfPageLimitExceededException 上限を超えた場合
	 */
	private void validatePageCount(int targetPageCount, int maxPages) {
		if (targetPageCount > maxPages) {
			LOGGER.warn("対象ページ数が上限を超えたため画像化しません。pageCount={}, maxPages={}", targetPageCount, maxPages);
			throw new PdfPageLimitExceededException(targetPageCount, maxPages);
		}
	}

	/**
	 * 指定ページを画像化する。
	 *
	 * @param renderer   PDFレンダラー
	 * @param pageNumber 画面・API仕様の1始まりページ番号
	 * @param renderDpi  解像度（DPI）
	 * @return 画像化したページ
	 * @throws IOException レンダリングに失敗した場合
	 */
	private BufferedImage renderPage(PDFRenderer renderer, int pageNumber, int renderDpi) throws IOException {
		// PDFBoxのレンダリングは0始まりのため、1始まりのページ番号から開始ページ番号を引く。
		return renderer.renderImageWithDPI(pageNumber - PdfConstants.START_PAGE, renderDpi, ImageType.RGB);
	}

	/**
	 * 1ページ分の画像をZIPへ追加する。
	 *
	 * @param zipOutputStream 追加先ZIP
	 * @param renderer        PDFレンダラー
	 * @param pageNumber      画面・API仕様の1始まりページ番号
	 * @param format          出力する画像形式
	 * @param renderDpi       解像度（DPI）
	 * @throws IOException レンダリング、画像エンコード、またはZIP書き込みに失敗した場合
	 */
	private void addPageImageToZip(ZipOutputStream zipOutputStream, PDFRenderer renderer, int pageNumber,
			PdfImageFormat format, int renderDpi) throws IOException {
		BufferedImage image = renderPage(renderer, pageNumber, renderDpi);
		zipOutputStream.putNextEntry(new ZipEntry(buildPageImageFileName(pageNumber, format)));
		zipOutputStream.write(encodeImage(image, format));
		zipOutputStream.closeEntry();
	}

	/**
	 * 画像を指定形式のバイト列へエンコードする。
	 * <p>
	 * JPEGとBMPはアルファチャンネルを持てないため、書き出す前に白背景の不透明画像へ描き直す。
	 * この変換を省くとJPEGは色が化け（赤みがかる）、BMPは {@code ImageIO.write} がfalseを返して0バイトになる。
	 *
	 * @param image  エンコードする画像
	 * @param format 出力する画像形式
	 * @return エンコード済みのバイト列
	 * @throws IOException エンコードに失敗した場合
	 */
	private byte[] encodeImage(BufferedImage image, PdfImageFormat format) throws IOException {
		BufferedImage targetImage = format.requiresOpaqueImage() ? toOpaqueImage(image) : image;
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			if (!ImageIO.write(targetImage, format.getImageIoFormatName(), outputStream)) {
				throw new IOException("この実行環境のImageIOは指定形式を書き出せません。format=" + format.getKey());
			}
			return outputStream.toByteArray();
		}
	}

	/**
	 * 透過を含む可能性のある画像を、白背景の不透明画像へ描き直す。
	 *
	 * @param image 変換元の画像
	 * @return アルファチャンネルを持たない画像
	 */
	private BufferedImage toOpaqueImage(BufferedImage image) {
		BufferedImage opaqueImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = opaqueImage.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.drawImage(image, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return opaqueImage;
	}

	/**
	 * 画像化対象ページ番号をページ番号順・重複なしで取得する。
	 * <p>
	 * 未指定は「全ページ」を意味する。画像化はページを選ばない使い方が多く、
	 * 未指定を空集合として扱うと中身の無いZIPが返ってしまう。
	 *
	 * @param document   読み込んだPDFドキュメント
	 * @param imagePages 画像化するページ番号。未指定の場合は全ページ
	 * @return 画像化対象の1始まりページ番号
	 * @throws IllegalArgumentException 指定されたページ番号がPDFのページ範囲外の場合
	 */
	private List<Integer> selectImagePageNumbers(PDDocument document, List<Integer> imagePages) {
		int totalPages = document.getNumberOfPages();
		if (CollectionUtils.isEmpty(imagePages)) {
			return IntStream.rangeClosed(PdfConstants.START_PAGE, totalPages).boxed().toList();
		}
		List<Integer> pageNumbers = imagePages.stream().distinct().sorted().toList();
		if (pageNumbers.stream()
				.anyMatch(pageNumber -> pageNumber == null || pageNumber < PdfConstants.START_PAGE
						|| pageNumber > totalPages)) {
			throw new IllegalArgumentException("画像化ページ番号がPDFのページ範囲外です。");
		}
		return pageNumbers;
	}

	/**
	 * 画像のZIP内ファイル名を作成する。
	 *
	 * @param pageNumber 画面・API仕様の1始まりページ番号
	 * @param format     出力する画像形式
	 * @return 3桁のページ番号と拡張子を含むZIP内ファイル名
	 */
	private String buildPageImageFileName(int pageNumber, PdfImageFormat format) {
		return PAGE_IMAGE_FILE_NAME_FORMAT.formatted(pageNumber, format.getFileExtension());
	}
}
