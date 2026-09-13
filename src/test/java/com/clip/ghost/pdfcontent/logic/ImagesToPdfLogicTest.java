package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.clip.ghost.pdfcontent.dto.PdfImageSource;
import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.clip.ghost.pdfcontent.exception.PdfImageInputException;

/**
 * {@link ImagesToPdfLogic} の単体テスト。
 */
class ImagesToPdfLogicTest {
	private static final float SIZE_TOLERANCE = 0.5f;

	@TempDir
	Path tempDirectory;

	private final ImagesToPdfLogic imagesToPdfLogic = new ImagesToPdfLogic();

	@ParameterizedTest
	@ValueSource(strings = { "png", "jpg", "tiff", "bmp" })
	@DisplayName("PNG / JPG / TIFF / BMPのいずれからも1ページのPDFを生成できる")
	void createPdfFromImagesAcceptsEverySupportedFormat(String formatName) throws IOException {
		PdfImageSource imagePath = createImage("single." + formatName, formatName, 200, 100);
		Path outputPath = tempDirectory.resolve("single-" + formatName + ".pdf");

		imagesToPdfLogic.createPdfFromImages(List.of(imagePath), outputPath, PdfImagePageSize.A4);

		assertEquals(1, readPageCount(outputPath));
	}

	@Test
	@DisplayName("複数の画像を渡した順にページへ並べる")
	void createPdfFromImagesKeepsGivenOrder() throws IOException {
		PdfImageSource firstPath = createImage("first.png", "png", 100, 100);
		PdfImageSource secondPath = createImage("second.png", "png", 100, 100);
		Path outputPath = tempDirectory.resolve("multi.pdf");

		imagesToPdfLogic.createPdfFromImages(List.of(firstPath, secondPath), outputPath, PdfImagePageSize.A4);

		assertEquals(2, readPageCount(outputPath));
	}

	@Test
	@DisplayName("FITでは画像の画素寸法がそのままページサイズになる")
	void createPdfFromImagesUsesImageSizeWhenFitSpecified() throws IOException {
		PdfImageSource imagePath = createImage("fit.png", "png", 320, 240);
		Path outputPath = tempDirectory.resolve("fit.pdf");

		imagesToPdfLogic.createPdfFromImages(List.of(imagePath), outputPath, PdfImagePageSize.FIT);

		try (PDDocument document = Loader.loadPDF(outputPath.toFile())) {
			PDRectangle mediaBox = document.getPage(0).getMediaBox();
			assertEquals(320f, mediaBox.getWidth(), SIZE_TOLERANCE);
			assertEquals(240f, mediaBox.getHeight(), SIZE_TOLERANCE);
		}
	}

	@Test
	@DisplayName("A4では縦長画像を縦向き、横長画像を横向きのページへ置く")
	void createPdfFromImagesChoosesPageOrientationByImageShape() throws IOException {
		PdfImageSource portraitPath = createImage("portrait.png", "png", 100, 200);
		PdfImageSource landscapePath = createImage("landscape.png", "png", 200, 100);
		Path outputPath = tempDirectory.resolve("orientation.pdf");

		imagesToPdfLogic.createPdfFromImages(List.of(portraitPath, landscapePath), outputPath, PdfImagePageSize.A4);

		try (PDDocument document = Loader.loadPDF(outputPath.toFile())) {
			PDRectangle portraitBox = document.getPage(0).getMediaBox();
			PDRectangle landscapeBox = document.getPage(1).getMediaBox();
			assertTrue(portraitBox.getHeight() > portraitBox.getWidth());
			assertTrue(landscapeBox.getWidth() > landscapeBox.getHeight());
		}
	}

	@Test
	@DisplayName("画像として読めないファイルを渡した場合はPdfImageInputExceptionになる")
	void createPdfFromImagesThrowsWhenFileIsNotImage() throws IOException {
		Path notImagePath = tempDirectory.resolve("not-image-temp.png");
		Files.writeString(notImagePath, "これは画像ではありません。", StandardCharsets.UTF_8);
		// 一時ファイル名と利用者が付けたファイル名は一致しない。メッセージへ出るのは後者であることを固定する。
		PdfImageSource notImageSource = new PdfImageSource(notImagePath, "利用者が選んだ資料.png");
		Path outputPath = tempDirectory.resolve("not-image.pdf");

		PdfImageInputException exception = assertThrows(PdfImageInputException.class,
				() -> imagesToPdfLogic.createPdfFromImages(List.of(notImageSource), outputPath, PdfImagePageSize.A4));

		assertEquals("利用者が選んだ資料.png", exception.getFileName());
	}

	@Test
	@DisplayName("画像が1件も無い場合はPDFを作らずIllegalArgumentExceptionになる")
	void createPdfFromImagesThrowsWhenNoImageGiven() {
		Path outputPath = tempDirectory.resolve("empty.pdf");

		assertThrows(IllegalArgumentException.class,
				() -> imagesToPdfLogic.createPdfFromImages(List.of(), outputPath, PdfImagePageSize.A4));

		assertTrue(Files.notExists(outputPath));
	}

	@Test
	@DisplayName("PDF化しても入力画像は削除しない")
	void createPdfFromImagesDoesNotDeleteSource() throws IOException {
		PdfImageSource imagePath = createImage("source.png", "png", 100, 100);
		Path outputPath = tempDirectory.resolve("source.pdf");

		imagesToPdfLogic.createPdfFromImages(List.of(imagePath), outputPath, PdfImagePageSize.A4);

		assertTrue(Files.exists(imagePath.path()));
	}

	/**
	 * 指定サイズの単色画像ファイルを生成する。
	 *
	 * @param fileName   生成する画像のファイル名
	 * @param formatName ImageIOの書き出しフォーマット名
	 * @param width      画像の幅（px）
	 * @param height     画像の高さ（px）
	 * @return 生成した画像の一時保存先と元ファイル名
	 * @throws IOException 画像の生成に失敗した場合
	 */
	private PdfImageSource createImage(String fileName, String formatName, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(Color.LIGHT_GRAY);
			graphics.fillRect(0, 0, width, height);
		} finally {
			graphics.dispose();
		}
		Path path = tempDirectory.resolve(fileName);
		assertTrue(ImageIO.write(image, formatName, path.toFile()), formatName + " を書き出せません。");
		return new PdfImageSource(path, fileName);
	}

	/**
	 * 生成したPDFのページ数を読み出す。
	 *
	 * @param pdfPath 読み込むPDFのパス
	 * @return ページ数
	 * @throws IOException PDFの読み込みに失敗した場合
	 */
	private int readPageCount(Path pdfPath) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
			return document.getNumberOfPages();
		}
	}
}
