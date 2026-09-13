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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clip.ghost.pdfcontent.enums.PdfRotation;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;
import com.clip.ghost.pdfcontent.exception.PdfSplitRangeException;

/**
 * {@link PdfPageOperationLogic} の単体テスト。
 */
class PdfPageOperationLogicTest {

	@TempDir
	Path tempDirectory;

	private final PdfPageOperationLogic pageOperationLogic = new PdfPageOperationLogic();

	@Test
	@DisplayName("指定ページを削除し、入力ファイルは削除しない")
	void deletePdfRemovesPagesWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("delete-input.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("delete-output.pdf");

		pageOperationLogic.deletePdf(List.of(2), inputPath, outputPath);

		assertTrue(Files.exists(inputPath));
		assertPdfContent(outputPath, 2, "first page", "third page");
		assertFalse(readPdfText(outputPath).contains("second page"));
	}

	@Test
	@DisplayName("抽出ページを並べ替えて重複を除き、入力ファイルは削除しない")
	void extractPdfSortsAndDeduplicatesPagesWithoutDeletingSource() throws IOException {
		Path inputPath = createPdf("extract-input.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("extract-output.pdf");

		pageOperationLogic.extractPdf(List.of(3, 1, 3), inputPath, outputPath);

		assertTrue(Files.exists(inputPath));
		assertPdfContent(outputPath, 2, "first page", "third page");
	}

	@Test
	@DisplayName("回転ページ未指定の場合は全ページを回転し、入力ファイルは削除しない")
	void rotatePdfRotatesEveryPageWhenPagesNotSpecified() throws IOException {
		Path inputPath = createPdf("rotate-all-input.pdf", "first page", "second page");
		Path outputPath = tempDirectory.resolve("rotate-all-output.pdf");

		pageOperationLogic.rotatePdf(PdfRotation.CLOCKWISE_90, List.of(), inputPath, outputPath);

		assertTrue(Files.exists(inputPath));
		assertPageRotations(outputPath, 90, 90);
	}

	@Test
	@DisplayName("回転ページを指定した場合は指定ページだけを回転し、他のページの回転角は変えない")
	void rotatePdfRotatesOnlySpecifiedPages() throws IOException {
		Path inputPath = createPdf("rotate-selected-input.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("rotate-selected-output.pdf");

		pageOperationLogic.rotatePdf(PdfRotation.UPSIDE_DOWN_180, List.of(2), inputPath, outputPath);

		assertPageRotations(outputPath, 0, 180, 0);
	}

	@Test
	@DisplayName("回転は現在の回転角への相対回転となり、360以上は正規化される")
	void rotatePdfAddsRotationToCurrentAngleAndNormalizes() throws IOException {
		Path inputPath = createPdf("rotate-relative-input.pdf", "first page");
		setPageRotation(inputPath, 270);
		Path outputPath = tempDirectory.resolve("rotate-relative-output.pdf");

		pageOperationLogic.rotatePdf(PdfRotation.UPSIDE_DOWN_180, List.of(), inputPath, outputPath);

		// 270 + 180 = 450 は 90 へ正規化される。
		assertPageRotations(outputPath, 90);
	}

	@Test
	@DisplayName("回転ページ番号が総ページ数を超える場合はPdfProcessingExceptionになる")
	void rotatePdfThrowsWhenPageNumberIsOutsideDocument() throws IOException {
		Path inputPath = createPdf("rotate-invalid-input.pdf", "first page");
		Path outputPath = tempDirectory.resolve("rotate-invalid-output.pdf");

		assertThrows(PdfProcessingException.class,
				() -> pageOperationLogic.rotatePdf(PdfRotation.CLOCKWISE_90, List.of(2), inputPath, outputPath));
	}

	@Test
	@DisplayName("結合順を保ち、入力ファイルは削除しない")
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
	@DisplayName("1ページずつのZIPエントリを作り、入力ファイルは削除しない")
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
	@DisplayName("分割範囲ごとにリクエスト順でZIPエントリを作る")
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
	@DisplayName("1ページだけの範囲は1ページのPDFにする")
	void splitPdfCreatesSinglePagePdfWhenRangeHasOnePage() throws IOException {
		Path inputPath = createPdf("split-single.pdf", "first page", "second page", "third page");
		Path outputPath = tempDirectory.resolve("split-single-output.zip");

		pageOperationLogic.splitPdf(inputPath, outputPath, List.of("3"));

		Map<String, List<String>> entries = readZipEntries(outputPath);
		assertEquals(List.of("pages_3.pdf"), List.copyOf(entries.keySet()));
		assertEquals(List.of("third page"), entries.get("pages_3.pdf"));
	}

	@Test
	@DisplayName("範囲未指定なら従来の1ページずつの命名を保つ")
	void splitPdfKeepsSinglePageNamingWhenRangesAreNotSpecified() throws IOException {
		Path inputPath = createPdf("split-default.pdf", "first page", "second page");
		Path outputPath = tempDirectory.resolve("split-default-output.zip");

		pageOperationLogic.splitPdf(inputPath, outputPath, null);

		// 範囲未指定は従来動作。ZIP内の命名も変えない。
		assertEquals(List.of("split-001.pdf", "split-002.pdf"), List.copyOf(readZipEntries(outputPath).keySet()));
	}

	@Test
	@DisplayName("総ページ数を超える範囲はZIPを書かずに拒否する")
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

	/**
	 * 指定ページ番号のページへ回転角を直接設定する。
	 *
	 * @param path     編集対象PDFのパス
	 * @param rotation 設定する回転角
	 * @throws IOException PDFの読み書きに失敗した場合
	 */
	private void setPageRotation(Path path, int rotation) throws IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			document.getPage(0).setRotation(rotation);
			document.save(path.toFile());
		}
	}

	/**
	 * PDFの各ページの回転角がページ順に期待どおりか検証する。
	 *
	 * @param path              検証対象PDFのパス
	 * @param expectedRotations ページ順の期待回転角
	 * @throws IOException PDFの読み込みに失敗した場合
	 */
	private void assertPageRotations(Path path, int... expectedRotations) throws IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			assertEquals(expectedRotations.length, document.getNumberOfPages());
			for (int pageIndex = 0; pageIndex < expectedRotations.length; pageIndex++) {
				assertEquals(expectedRotations[pageIndex], document.getPage(pageIndex).getRotation());
			}
		}
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
