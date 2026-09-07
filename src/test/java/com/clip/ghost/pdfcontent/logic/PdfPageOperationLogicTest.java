package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import com.clip.ghost.pdfcontent.exception.PdfSplitRangeException;

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

		pageOperationLogic.splitPdf(inputPath, outputPath, List.of());

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

	@Test
	void splitPdfCreatesOneEntryPerRangeInRequestOrder() throws IOException {
		Path inputPath = createPdf("split-range.pdf", "first page", "second page", "third page", "fourth page");
		Path outputPath = tempDirectory.resolve("split-range-output.zip");

		pageOperationLogic.splitPdf(inputPath, outputPath, List.of("1-2", "4"));

		assertTrue(Files.exists(inputPath));
		Map<String, List<String>> entries = readZipEntries(outputPath);
		// 指定順にZIPへ入れる。並び替えると利用者が指定した順序が失われる。
		assertEquals(List.of("pages_1-2.pdf", "pages_4.pdf"), List.copyOf(entries.keySet()));
		assertEquals(List.of("first page", "second page"), entries.get("pages_1-2.pdf"));
		assertEquals(List.of("fourth page"), entries.get("pages_4.pdf"));
	}

	@Test
	void splitPdfCreatesSinglePagePdfWhenRangeHasOnePage() throws IOException {
		Path inputPath = createPdf("split-single.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("split-single-output.zip");

		pageOperationLogic.splitPdf(inputPath, outputPath, List.of("3"));

		Map<String, List<String>> entries = readZipEntries(outputPath);
		assertEquals(List.of("pages_3.pdf"), List.copyOf(entries.keySet()));
		assertEquals(List.of("third page"), entries.get("pages_3.pdf"));
	}

	@Test
	void splitPdfKeepsSinglePageNamingWhenRangesAreNotSpecified() throws IOException {
		Path inputPath = createPdf("split-default.pdf", "first page", "second page");
		Path outputPath = tempDirectory.resolve("split-default-output.zip");

		pageOperationLogic.splitPdf(inputPath, outputPath, null);

		// 範囲未指定は従来動作。ZIP内の命名も変えない。
		assertEquals(List.of("split-001.pdf", "split-002.pdf"), List.copyOf(readZipEntries(outputPath).keySet()));
	}

	@Test
	void splitPdfRejectsRangeBeyondTotalPagesWithoutWritingZip() throws IOException {
		Path inputPath = createPdf("split-outside.pdf", "first page", "second page");
		Path outputPath = tempDirectory.resolve("split-outside-output.zip");

		// PdfProcessingExceptionへ化けると400ではなく500になるため、例外型そのものを固定する。
		PdfSplitRangeException exception = assertThrows(PdfSplitRangeException.class,
				() -> pageOperationLogic.splitPdf(inputPath, outputPath, List.of("1-2", "3-4")));

		assertEquals("3-4", exception.getOutsideRangeText());
		assertEquals(2, exception.getTotalPages());
		// ZIPを書き始める前に弾く。書き始めてから弾くと、中身の無いZIPが一時ファイルとして残る。
		assertFalse(Files.exists(outputPath));
		assertTrue(Files.exists(inputPath));
	}

	/**
	 * ZIP内のエントリ名と、各エントリのページ単位テキストを読み出す。
	 *
	 * @param zipPath 読み込むZIPのパス
	 * @return エントリ名順のエントリ名とページ単位テキスト
	 * @throws IOException ZIPまたはPDFの読み込みに失敗した場合
	 */
	private Map<String, List<String>> readZipEntries(Path zipPath) throws IOException {
		Map<String, List<String>> entries = new LinkedHashMap<>();
		try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				try (PDDocument document = Loader.loadPDF(zipInputStream.readAllBytes())) {
					List<String> pageTexts = new ArrayList<>();
					PDFTextStripper textStripper = new PDFTextStripper();
					for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
						textStripper.setStartPage(pageNumber);
						textStripper.setEndPage(pageNumber);
						pageTexts.add(textStripper.getText(document).trim());
					}
					entries.put(zipEntry.getName(), pageTexts);
				}
			}
		}
		return entries;
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
