package com.clip.ghost.pdfcontent.logic;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clip.ghost.pdfcontent.dto.PdfImageSource;
import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.clip.ghost.pdfcontent.exception.PdfImageInputException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * 画像ファイルから1つのPDFを組み立てる内部クラス。
 * <p>
 * 画像は渡された順にページへ並べる。読み込みには {@code PDImageXObject.createFromFile} ではなく
 * ImageIOを使う。前者が扱えるのはJPEGとPNGだけで、TIFFとBMPを渡すと例外になるため。
 * ImageIOで {@code BufferedImage} へ読んでから {@code LosslessFactory} に渡せば、4形式を同じ経路で扱える。
 * <p>
 * 複数ページTIFFは全ページを取り出して並べる。1枚目だけを読むと、利用者から見て画像が消えたことになる。
 */
@NoArgsConstructor
final class ImagesToPdfLogic {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImagesToPdfLogic.class);

	/**
	 * 画像ファイルを順にページへ並べたPDFを生成する。
	 *
	 * @param imageSources 画像の一時保存先と元ファイル名（並べる順）
	 * @param outputPath   生成したPDFの出力先パス
	 * @param pageSize     ページサイズの決め方
	 * @throws PdfImageInputException 画像として読めないファイルが含まれる場合
	 * @throws PdfProcessingException PDFの組み立てまたは保存に失敗した場合
	 */
	void createPdfFromImages(List<PdfImageSource> imageSources, Path outputPath, PdfImagePageSize pageSize) {
		if (CollectionUtils.isEmpty(imageSources)) {
			throw new IllegalArgumentException("PDF化する画像が指定されていません。");
		}
		try (PDDocument document = new PDDocument()) {
			for (PdfImageSource imageSource : imageSources) {
				addImageFileToDocument(document, imageSource, pageSize);
			}
			LOGGER.info("画像からPDFを生成しました。pageCount={}", document.getNumberOfPages());
			document.save(outputPath.toFile());
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("画像からのPDF生成に失敗しました。", e);
		}
	}

	/**
	 * 1つの画像ファイルに含まれる全ページをPDFへ追加する。
	 * <p>
	 * 1枚読んではページへ描き、次を読む前に {@code BufferedImage} の参照を捨てる。
	 * 先に全ページを読み込むと、複数ページTIFFでヒープを使い切る。
	 *
	 * @param document    追加先のPDFドキュメント
	 * @param imageSource 画像の一時保存先と元ファイル名
	 * @param pageSize    ページサイズの決め方
	 * @throws PdfImageInputException 画像として読めない場合
	 * @throws IOException            画像の読み込みまたはページ描画に失敗した場合
	 */
	private void addImageFileToDocument(PDDocument document, PdfImageSource imageSource, PdfImagePageSize pageSize)
			throws IOException {
		try (InputStream inputStream = Files.newInputStream(imageSource.path());
				ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
			ImageReader reader = findImageReader(imageInputStream, imageSource);
			try {
				reader.setInput(imageInputStream);
				// allowSearch=trueで複数ページTIFFの総ページ数を確定させる。falseだと-1が返り得る。
				int imageCount = reader.getNumImages(true);
				for (int imageIndex = 0; imageIndex < imageCount; imageIndex++) {
					addImageToDocument(document, reader.read(imageIndex), pageSize);
				}
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * 画像に対応するImageIOのリーダーを取得する。
	 *
	 * @param imageInputStream 画像の入力ストリーム
	 * @param imageSource      画像の一時保存先と元ファイル名
	 * @return 画像を読めるリーダー
	 * @throws PdfImageInputException 対応するリーダーが無い場合
	 */
	private ImageReader findImageReader(ImageInputStream imageInputStream, PdfImageSource imageSource) {
		Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
		if (!readers.hasNext()) {
			// ログには一時パス、利用者向けメッセージには元のファイル名を出す。
			// 一時ファイル名はUUIDのため、利用者はどのファイルを指しているのか判断できない。
			LOGGER.warn("画像として読み込めないファイルです。path={}", imageSource.path());
			throw new PdfImageInputException(imageSource.fileName());
		}
		return readers.next();
	}

	/**
	 * 1枚の画像を新しいページとしてPDFへ追加する。
	 *
	 * @param document 追加先のPDFドキュメント
	 * @param image    追加する画像
	 * @param pageSize ページサイズの決め方
	 * @throws IOException 画像の埋め込みまたはページ描画に失敗した場合
	 */
	private void addImageToDocument(PDDocument document, BufferedImage image, PdfImagePageSize pageSize)
			throws IOException {
		PDRectangle pageRectangle = buildPageRectangle(image, pageSize);
		PDPage page = new PDPage(pageRectangle);
		document.addPage(page);

		PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
		float scale = Math.min(pageRectangle.getWidth() / image.getWidth(),
				pageRectangle.getHeight() / image.getHeight());
		float drawWidth = image.getWidth() * scale;
		float drawHeight = image.getHeight() * scale;
		// 余白が出る場合は中央へ置く。左上寄せにすると、A4に収めたときだけ見た目の重心がずれる。
		float offsetX = (pageRectangle.getWidth() - drawWidth) / 2;
		float offsetY = (pageRectangle.getHeight() - drawHeight) / 2;
		try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
			contentStream.drawImage(pdImage, offsetX, offsetY, drawWidth, drawHeight);
		}
	}

	/**
	 * ページの矩形を決める。
	 * <p>
	 * {@code FIT} は画像1ピクセルを1ポイント（72dpi相当）として扱う。実寸を再現するには画像のDPI情報が要るが、
	 * スクリーンショットやスキャン画像はDPIを持たないことが多く、持っていても値が当てにならない。
	 * 推測で拡大縮小するより、画素と座標を1対1に対応させるほうが結果を予測しやすい。
	 *
	 * @param image    ページへ置く画像
	 * @param pageSize ページサイズの決め方
	 * @return ページの矩形
	 */
	private PDRectangle buildPageRectangle(BufferedImage image, PdfImagePageSize pageSize) {
		if (pageSize.fitsPageToImage()) {
			return new PDRectangle(image.getWidth(), image.getHeight());
		}
		// 横長の画像はA4横向きに置く。縦向き固定にすると横長画像が極端に小さくなる。
		boolean landscape = image.getWidth() > image.getHeight();
		return landscape ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) : PDRectangle.A4;
	}
}
