package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

/**
 * {@link PdfImageExportLogic} の単体テスト。
 */
class PdfImageExportLogicTest {
	private static final int TEST_RENDER_DPI = 36;
	private static final int TEST_MAX_PAGES = 10;

	@TempDir
	Path tempDirectory;

	private final PdfImageExportLogic imageExportLogic = new PdfImageExportLogic();

	@ParameterizedTest
	@EnumSource(PdfImageFormat.class)
	@DisplayName("全ての画像形式で、ページごとに読み込める画像ファイルをZIPへ出力する")
	void exportPdfImagesWritesReadableImageForEveryFormat(PdfImageFormat format) throws IOException {
		Path inputPath = createPdf("images-input-" + format.getKey() + ".pdf", "first page", "second page");
		Path outputPath = tempDirectory.resolve("images-output-" + format.getKey() + ".zip");

		imageExportLogic.exportPdfImages(inputPath, outputPath, format, TEST_RENDER_DPI, TEST_MAX_PAGES, List.of());

		Map<String, byte[]> entries = readZipEntries(outputPath);
		assertEquals(List.of("page-001." + format.getFileExtension(), "page-002." + format.getFileExtension()),
				List.copyOf(entries.keySet()));
		for (byte[] imageBytes : entries.values()) {
			assertTrue(imageBytes.length > 0);
			assertNotNull(readImage(imageBytes), format.getKey() + " の画像を読み戻せません。");
		}
	}

	@Test
	@DisplayName("画像化ページを指定した場合は指定ページだけをZIPへ出力する")
	void exportPdfImagesWritesOnlySpecifiedPages() throws IOException {
		Path inputPath = createPdf("images-selected-input.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("images-selected-output.zip");

		imageExportLogic.exportPdfImages(inputPath, outputPath, PdfImageFormat.PNG, TEST_RENDER_DPI, TEST_MAX_PAGES,
				List.of(3, 1, 3));

		// 重複は除かれ、ページ番号順になる。ZIP内のファイル名は元PDFのページ番号を保つ。
		assertEquals(List.of("page-001.png", "page-003.png"), List.copyOf(readZipEntries(outputPath).keySet()));
	}

	@Test
	@DisplayName("解像度を上げると出力画像の画素数が増える")
	void exportPdfImagesUsesRequestedDpi() throws IOException {
		Path lowDpiInputPath = createPdf("images-dpi-low-input.pdf", "first page");
		Path lowDpiOutputPath = tempDirectory.resolve("images-dpi-low-output.zip");
		Path highDpiInputPath = createPdf("images-dpi-high-input.pdf", "first page");
		Path highDpiOutputPath = tempDirectory.resolve("images-dpi-high-output.zip");

		imageExportLogic.exportPdfImages(lowDpiInputPath, lowDpiOutputPath, PdfImageFormat.PNG, 36, TEST_MAX_PAGES,
				List.of());
		imageExportLogic.exportPdfImages(highDpiInputPath, highDpiOutputPath, PdfImageFormat.PNG, 72, TEST_MAX_PAGES,
				List.of());

		BufferedImage lowDpiImage = readImage(readZipEntries(lowDpiOutputPath).get("page-001.png"));
		BufferedImage highDpiImage = readImage(readZipEntries(highDpiOutputPath).get("page-001.png"));
		assertTrue(highDpiImage.getWidth() > lowDpiImage.getWidth());
	}

	@Test
	@DisplayName("対象ページ数が上限を超えた場合は例外になり、ZIPを作らない")
	void exportPdfImagesThrowsWhenPageCountExceedsLimit() throws IOException {
		Path inputPath = createPdf("images-limit-input.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("images-limit-output.zip");

		assertThrows(PdfPageLimitExceededException.class, () -> imageExportLogic.exportPdfImages(inputPath, outputPath,
				PdfImageFormat.PNG, TEST_RENDER_DPI, 2, List.of()));

		// 上限判定はZIPを開く前に行うため、中身の無いZIPが残らない。
		assertTrue(Files.notExists(outputPath));
	}

	@Test
	@DisplayName("画像化ページ番号が総ページ数を超える場合はPdfProcessingExceptionになる")
	void exportPdfImagesThrowsWhenPageNumberIsOutsideDocument() throws IOException {
		Path inputPath = createPdf("images-invalid-input.pdf", "first page");
		Path outputPath = tempDirectory.resolve("images-invalid-output.zip");

		assertThrows(PdfProcessingException.class, () -> imageExportLogic.exportPdfImages(inputPath, outputPath,
				PdfImageFormat.PNG, TEST_RENDER_DPI, TEST_MAX_PAGES, List.of(2)));
	}

	@Test
	@DisplayName("画像化しても入力ファイルは削除しない")
	void exportPdfImagesDoesNotDeleteSource() throws IOException {
		Path inputPath = createPdf("images-source-input.pdf", "first page");
		Path outputPath = tempDirectory.resolve("images-source-output.zip");

		imageExportLogic.exportPdfImages(inputPath, outputPath, PdfImageFormat.PNG, TEST_RENDER_DPI, TEST_MAX_PAGES,
				List.of());

		assertTrue(Files.exists(inputPath));
	}

	/**
	 * テキストを1行ずつ書いた複数ページのPDFを生成する。
	 *
	 * @param fileName  生成するPDFのファイル名
	 * @param pageTexts ページごとに書き込むテキスト
	 * @return 生成したPDFのパス
	 * @throws IOException PDFの生成に失敗した場合
	 */
	private Path createPdf(String fileName, String... pageTexts) throws IOException {
		Path path = tempDirectory.resolve(fileName);
		try (PDDocument document = new PDDocument()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (String pageText : pageTexts) {
				PDPage page = new PDPage();
				document.addPage(page);
				try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
					contentStream.beginText();
					contentStream.setFont(font, 12);
					contentStream.newLineAtOffset(20, 20);
					contentStream.showText(pageText);
					contentStream.endText();
				}
			}
			document.save(path.toFile());
		}
		return path;
	}

	/**
	 * ZIPのエントリ名と内容を、ZIP内の並び順を保って読み出す。
	 *
	 * @param zipPath 読み込むZIPのパス
	 * @return エントリ名をキーにした内容のMap
	 * @throws IOException ZIPの読み込みに失敗した場合
	 */
	private Map<String, byte[]> readZipEntries(Path zipPath) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry entry = zipInputStream.getNextEntry();
			while (entry != null) {
				entries.put(entry.getName(), zipInputStream.readAllBytes());
				entry = zipInputStream.getNextEntry();
			}
		}
		return entries;
	}

	/**
	 * バイト列を画像として読み戻す。
	 *
	 * @param imageBytes 画像のバイト列
	 * @return 読み込んだ画像。読み込めない場合はnull
	 * @throws IOException 画像の読み込みに失敗した場合
	 */
	private BufferedImage readImage(byte[] imageBytes) throws IOException {
		try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
			return ImageIO.read(inputStream);
		}
	}
}
