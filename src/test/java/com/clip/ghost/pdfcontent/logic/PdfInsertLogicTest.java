package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.GhostPdfDto;

/**
 * {@link PdfInsertLogic} の単体テスト。
 */
class PdfInsertLogicTest {

	@TempDir
	Path tempDirectory;

	@Test
	void insertPdfAppliesRequestsInOrderWithoutDeletingSources() throws IOException {
		Path originalPath = createPdf("original.pdf", "original first", "original second", "original third");
		Path insertPath = createPdf("insert.pdf", "inserted page");
		Path replacePath = createPdf("replace.pdf", "replacement first", "replacement second");
		Path lastInsertPath = createPdf("last-insert.pdf", "last page");
		Path outputPath = tempDirectory.resolve("output.pdf");
		List<GhostPdfDto> insertRequests = List.of(new GhostPdfDto(1, insertPath, PdfConstants.OPTION_INSERT),
				new GhostPdfDto(2, replacePath, PdfConstants.OPTION_REPLACE),
				new GhostPdfDto(-1, lastInsertPath, PdfConstants.OPTION_LAST_INSERT));

		new PdfInsertLogic().insertPdf(originalPath, insertRequests, outputPath);

		assertTrue(Files.exists(originalPath));
		assertTrue(Files.exists(insertPath));
		assertTrue(Files.exists(replacePath));
		assertTrue(Files.exists(lastInsertPath));
		assertPdfContent(outputPath, "original first", "inserted page", "replacement first", "replacement second",
				"original third", "last page");
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

	private void assertPdfContent(Path path, String... expectedTexts) throws IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			assertEquals(expectedTexts.length, document.getNumberOfPages());
			String text = new PDFTextStripper().getText(document);
			int previousTextIndex = -1;
			for (String expectedText : expectedTexts) {
				int textIndex = text.indexOf(expectedText);
				assertTrue(textIndex > previousTextIndex);
				previousTextIndex = textIndex;
			}
		}
	}
}
