package com.clip.ghost.pdfcontent.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * CLIから実際のPDF生成までを通し、並び順・画質・元画像と保存先の保護を検証する。
 */
class ImageDirectoryPdfCommandTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	@DisplayName("日本語と空白を含むフォルダの画像を辞書順・元の画素寸法で収録し、元画像は変更しない")
	void createsOrderedLosslessPdfWithoutChangingImages() throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("入力 画像"));
		Path second = createImage(directory, "002.PNG", 32, 16, Color.BLUE);
		Path first = createImage(directory, "001.png", 24, 12, Color.RED);
		byte[] firstBytes = Files.readAllBytes(first);
		byte[] secondBytes = Files.readAllBytes(second);
		Files.writeString(directory.resolve("メモ.txt"), "PDFには含めない");
		Path nested = Files.createDirectory(directory.resolve("サブフォルダ"));
		createImage(nested, "003.png", 10, 10, Color.GREEN);
		Path output = temporaryDirectory.resolve("出力 PDF/まとめ.pdf");

		assertEquals(0, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString()));

		try (PDDocument document = Loader.loadPDF(output.toFile())) {
			assertEquals(2, document.getNumberOfPages());
			assertPageImage(document.getPage(0), 24, 12, Color.RED);
			assertPageImage(document.getPage(1), 32, 16, Color.BLUE);
			assertEquals(24f, document.getPage(0).getMediaBox().getWidth());
			assertEquals(12f, document.getPage(0).getMediaBox().getHeight());
			assertEquals(0, document.getPage(0).getRotation());
		}
		assertArrayEquals(firstBytes, Files.readAllBytes(first));
		assertArrayEquals(secondBytes, Files.readAllBytes(second));
		try (Stream<Path> files = Files.list(output.getParent())) {
			assertEquals(1, files.count());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 90, 180, 270 })
	@DisplayName("回転角を指定すると画像を再加工せずPDFの表示方向だけを変える")
	void rotatesPagesWithoutChangingEmbeddedImage(int rotation) throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("images"));
		createImage(directory, "001.png", 32, 16, Color.RED);
		Path output = temporaryDirectory.resolve("rotated.pdf");

		assertEquals(0, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString(), "FIT",
				Integer.toString(rotation)));

		try (PDDocument document = Loader.loadPDF(output.toFile())) {
			assertEquals(rotation, document.getPage(0).getRotation());
			assertPageImage(document.getPage(0), 32, 16, Color.RED);
		}
	}

	@Test
	@DisplayName("A4指定では画像の向きに合わせたA4ページへ収める")
	void createsA4PdfWhenRequested() throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("images"));
		createImage(directory, "001.png", 32, 16, Color.BLUE);
		Path output = temporaryDirectory.resolve("a4.pdf");

		assertEquals(0, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString(), "A4"));

		try (PDDocument document = Loader.loadPDF(output.toFile())) {
			PDRectangle page = document.getPage(0).getMediaBox();
			assertEquals(PDRectangle.A4.getHeight(), page.getWidth());
			assertEquals(PDRectangle.A4.getWidth(), page.getHeight());
		}
	}

	@Test
	@DisplayName("既存の出力ファイルがある場合は失敗し、その内容を保つ")
	void refusesToOverwriteExistingOutput() throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("images"));
		createImage(directory, "001.png", 32, 16, Color.BLUE);
		Path output = temporaryDirectory.resolve("existing.pdf");
		byte[] original = "既存の成果物".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Files.write(output, original);

		assertEquals(1, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString()));

		assertArrayEquals(original, Files.readAllBytes(output));
	}

	@Test
	@DisplayName("途中の画像が壊れている場合は不完全なPDFと一時ファイルを残さず、元画像も保つ")
	void removesTemporaryOutputWhenAnImageIsCorrupt() throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("images"));
		Path first = createImage(directory, "001.png", 32, 16, Color.RED);
		Path corrupt = directory.resolve("002.png");
		Files.writeString(corrupt, "画像ではない");
		byte[] original = Files.readAllBytes(first);
		Path outputDirectory = Files.createDirectory(temporaryDirectory.resolve("output"));
		Path output = outputDirectory.resolve("failed.pdf");

		assertEquals(1, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString()));

		assertTrue(Files.notExists(output));
		try (Stream<Path> files = Files.list(outputDirectory)) {
			assertEquals(0, files.count());
		}
		assertArrayEquals(original, Files.readAllBytes(first));
		assertEquals("画像ではない", Files.readString(corrupt));
	}

	@ParameterizedTest
	@ValueSource(strings = { "45", "0", "abc" })
	@DisplayName("不正な回転角は失敗し、PDFを作成しない")
	void rejectsInvalidRotation(String rotation) throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("images"));
		createImage(directory, "001.png", 32, 16, Color.RED);
		Path output = temporaryDirectory.resolve("invalid.pdf");

		assertEquals(1, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString(), "FIT", rotation));

		assertTrue(Files.notExists(output));
	}

	@Test
	@DisplayName("画像がないフォルダと存在しないフォルダはPDFを作らず失敗する")
	void rejectsEmptyAndMissingDirectories() throws IOException {
		Path empty = Files.createDirectory(temporaryDirectory.resolve("empty"));
		Path output = temporaryDirectory.resolve("empty.pdf");

		assertEquals(1, ImageDirectoryPdfCommand.execute(empty.toString(), output.toString()));
		assertEquals(1, ImageDirectoryPdfCommand.execute(empty.resolve("missing").toString(), output.toString()));

		assertTrue(Files.notExists(output));
	}

	@Test
	@DisplayName("PDF以外の出力拡張子と不正なページサイズはファイルを作らず失敗する")
	void rejectsInvalidOutputExtensionAndPageSize() throws IOException {
		Path directory = Files.createDirectory(temporaryDirectory.resolve("images"));
		createImage(directory, "001.png", 32, 16, Color.RED);
		Path output = temporaryDirectory.resolve("invalid.png");

		assertEquals(1, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString()));
		assertEquals(1, ImageDirectoryPdfCommand.execute(directory.toString(), output.toString(), "LETTER"));

		assertTrue(Files.notExists(output));
	}

	@Test
	@DisplayName("ヘルプは正常終了し、引数の不足や過剰は異常終了する")
	void handlesHelpAndArgumentCount() {
		assertEquals(0, ImageDirectoryPdfCommand.execute("--help"));
		assertEquals(1, ImageDirectoryPdfCommand.execute());
		assertEquals(1, ImageDirectoryPdfCommand.execute("images"));
		assertEquals(1, ImageDirectoryPdfCommand.execute("images", "output.pdf", "FIT", "90", "extra"));
	}

	private Path createImage(Path directory, String name, int width, int height, Color color) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, color.getRGB());
			}
		}
		Path path = directory.resolve(name);
		assertTrue(ImageIO.write(image, "png", path.toFile()));
		return path;
	}

	private void assertPageImage(PDPage page, int width, int height, Color color) throws IOException {
		PDImageXObject image = (PDImageXObject) page.getResources()
				.getXObject(page.getResources().getXObjectNames().iterator().next());
		assertEquals(width, image.getWidth());
		assertEquals(height, image.getHeight());
		assertEquals(color.getRGB(), image.getImage().getRGB(0, 0));
	}
}
