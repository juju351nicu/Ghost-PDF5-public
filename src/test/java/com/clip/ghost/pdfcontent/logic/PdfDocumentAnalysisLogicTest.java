package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

/**
 * {@link PdfDocumentAnalysisLogic} の単体テスト。
 */
class PdfDocumentAnalysisLogicTest {

	@TempDir
	Path tempDirectory;

	@Test
	void getPdfMetadataReturnsBasicInfoWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("metadata.pdf", "first page", "second page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		PdfMetadataResponse response = analysisLogic.getPdfMetadata(inputPath, "metadata.pdf", 123L);

		assertTrue(Files.exists(inputPath));
		assertEquals("metadata.pdf", response.getFileName());
		assertEquals(123L, response.getFileSize());
		assertEquals(2, response.getPageCount());
		assertFalse(response.getEncrypted());
	}

	@Test
	void extractPdfTextReturnsTextWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("text.pdf", "first page", "second page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		PdfTextResponse response = analysisLogic.extractPdfText(inputPath, "text.pdf", 456L);

		assertTrue(Files.exists(inputPath));
		assertEquals("text.pdf", response.getFileName());
		assertEquals(456L, response.getFileSize());
		assertEquals(2, response.getPageCount());
		assertTrue(response.getText().contains("first page"));
		assertTrue(response.getText().contains("second page"));
	}

	@Test
	void extractPdfPageTextsReturnsPagesInOrderAndKeepsEmptyPageWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("pages.pdf", "first page", "", "third page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		List<String> pageTexts = analysisLogic.extractPdfPageTexts(inputPath);

		assertTrue(Files.exists(inputPath));
		assertEquals(3, pageTexts.size());
		assertTrue(pageTexts.get(0).contains("first page"));
		assertEquals("", pageTexts.get(1));
		assertTrue(pageTexts.get(2).contains("third page"));
	}

	@Test
	void extractPdfPageTextsKeepsSourceWhenPdfCannotBeRead() throws IOException {
		Path inputPath = tempDirectory.resolve("broken.pdf");
		Files.writeString(inputPath, "not a pdf");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		assertThrows(PdfProcessingException.class, () -> analysisLogic.extractPdfPageTexts(inputPath));

		assertTrue(Files.exists(inputPath));
	}

	@Test
	void extractPdfPageContentsRendersBlankPagesToPngAndKeepsSource() throws IOException {
		Path inputPath = createPdf("contents.pdf", "first page", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		List<PdfPageContent> contents = analysisLogic.extractPdfPageContents(inputPath, 100);

		assertTrue(Files.exists(inputPath));
		assertEquals(2, contents.size());
		assertTrue(contents.get(0).text().contains("first page"));
		assertNull(contents.get(0).imageBytes());
		assertTrue(contents.get(1).text().isBlank());
		byte[] png = contents.get(1).imageBytes();
		assertNotNull(png);
		// PNGシグネチャ（0x89 'P' 'N' 'G'）を確認し、画像化されていることを検証する。
		assertTrue(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
	}

	private Path createPdf(String fileName, String... pageTexts) throws IOException {
		Path path = tempDirectory.resolve(fileName);
		try (PDDocument document = new PDDocument()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (String pageText : pageTexts) {
				PDPage page = new PDPage();
				document.addPage(page);
				if (pageText.isEmpty()) {
					continue;
				}
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
}
