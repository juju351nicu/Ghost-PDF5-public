package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PdfPageOperationLogic} の単体テスト。
 */
class PdfPageOperationLogicTest {

	@TempDir
	Path tempDirectory;

	private final PdfPageOperationLogic pageOperationLogic = new PdfPageOperationLogic();

	@Test
	void deletePdfRemovesPagesWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("delete-input.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("delete-output.pdf");

		pageOperationLogic.deletePdf(List.of(2), inputPath, outputPath);

		assertTrue(Files.exists(inputPath));
		assertPdfContent(outputPath, 2, "first page", "third page");
		assertFalse(readPdfText(outputPath).contains("second page"));
	}

	@Test
	void extractPdfSortsAndDeduplicatesPagesWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("extract-input.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("extract-output.pdf");

		pageOperationLogic.extractPdf(List.of(3, 1, 3), inputPath, outputPath);

		assertTrue(Files.exists(inputPath));
		assertPdfContent(outputPath, 2, "first page", "third page");
	}

	@Test
	void mergePdfKeepsDocumentOrderWithoutDeletingSources() throws IOException {
		Path firstPath = createPdf("merge-first.pdf", "first page", "second page");
		Path secondPath = createPdf("merge-second.pdf", "third page");
		Path outputPath = tempDirectory.resolve("merge-output.pdf");

		pageOperationLogic.mergePdf(List.of(firstPath, secondPath), outputPath);

		assertTrue(Files.exists(firstPath));
		assertTrue(Files.exists(secondPath));
		assertPdfContent(outputPath, 3, "first page", "second page", "third page");
	}

	@Test
	void splitPdfCreatesSinglePageEntriesWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("split-input.pdf", "first page", "second page");
		Path outputPath = tempDirectory.resolve("split-output.zip");

		pageOperationLogic.splitPdf(inputPath, outputPath);

		assertTrue(Files.exists(inputPath));
		List<String> entryNames = new ArrayList<>();
		List<String> entryTexts = new ArrayList<>();
		try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(outputPath))) {
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				entryNames.add(zipEntry.getName());
				try (PDDocument document = Loader.loadPDF(zipInputStream.readAllBytes())) {
					assertEquals(1, document.getNumberOfPages());
					entryTexts.add(new PDFTextStripper().getText(document).trim());
				}
			}
		}
		assertEquals(List.of("split-001.pdf", "split-002.pdf"), entryNames);
		assertEquals(List.of("first page", "second page"), entryTexts);
	}

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

	private void assertPdfContent(Path path, int expectedPageCount, String... expectedTexts) throws IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			assertEquals(expectedPageCount, document.getNumberOfPages());
			String text = new PDFTextStripper().getText(document);
			int previousTextIndex = -1;
			for (String expectedText : expectedTexts) {
				int textIndex = text.indexOf(expectedText);
				assertTrue(textIndex > previousTextIndex);
				previousTextIndex = textIndex;
			}
		}
	}

	private String readPdfText(Path path) throws IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			return new PDFTextStripper().getText(document);
		}
	}
}
