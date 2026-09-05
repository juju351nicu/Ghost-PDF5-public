package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PdfPageCopySupport} の単体テスト。
 */
class PdfPageCopySupportTest {

	@TempDir
	Path tempDirectory;

	@Test
	void appendDocumentCopiesPagesLayoutAndResourcesWithoutDeletingSource() throws IOException {
		Path sourcePath = createSourcePdf();

		try (PDDocument outputDocument = new PDDocument()) {
			new PdfPageCopySupport(outputDocument).appendDocument(sourcePath);

			assertTrue(Files.exists(sourcePath));
			assertEquals(2, outputDocument.getNumberOfPages());
			assertPageLayout(outputDocument.getPage(0), 320, 480, 90);
			assertPageLayout(outputDocument.getPage(1), 420, 540, 180);
			String text = new PDFTextStripper().getText(outputDocument);
			assertTrue(text.contains("first page"));
			assertTrue(text.contains("second page"));
		}
	}

	private Path createSourcePdf() throws IOException {
		Path sourcePath = tempDirectory.resolve("source.pdf");
		try (PDDocument document = new PDDocument()) {
			addPage(document, "first page", 320, 480, 90);
			addPage(document, "second page", 420, 540, 180);
			document.save(sourcePath.toFile());
		}
		return sourcePath;
	}

	private void addPage(PDDocument document, String text, float width, float height, int rotation) throws IOException {
		PDPage page = new PDPage(new PDRectangle(width, height));
		page.setCropBox(new PDRectangle(10, 20, width - 20, height - 40));
		page.setRotation(rotation);
		document.addPage(page);
		PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
			contentStream.beginText();
			contentStream.setFont(font, 12);
			contentStream.newLineAtOffset(30, 30);
			contentStream.showText(text);
			contentStream.endText();
		}
	}

	private void assertPageLayout(PDPage page, float width, float height, int rotation) {
		assertEquals(width, page.getMediaBox().getWidth());
		assertEquals(height, page.getMediaBox().getHeight());
		assertEquals(width - 20, page.getCropBox().getWidth());
		assertEquals(height - 40, page.getCropBox().getHeight());
		assertEquals(rotation, page.getRotation());
	}
}
