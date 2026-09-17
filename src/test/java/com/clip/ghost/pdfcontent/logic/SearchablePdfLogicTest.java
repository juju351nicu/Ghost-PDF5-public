package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clip.ghost.imagecontent.dto.OcrWordBox;
import com.clip.ghost.pdfcontent.enums.SearchablePdfMode;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;

/**
 * {@link SearchablePdfLogic} の対象ページ選定、コストガード、透明テキスト書き込みを検証するテスト。
 * <p>
 * 実Tesseractは使わず、固定の単語ボックスを返す{@link SearchablePdfPageOcr}で検証する。
 */
class SearchablePdfLogicTest {

	@TempDir
	Path tempDirectory;

	private final SearchablePdfLogic searchablePdfLogic = new SearchablePdfLogic();

	@Test
	@DisplayName("AUTOでは文字レイヤーが無いページだけをOCR対象にする")
	void createSearchablePdfProcessesOnlyBlankPagesInAutoMode() throws IOException {
		Path inputPath = createPdf("auto-input.pdf", "", "existing text", "");
		Path outputPath = tempDirectory.resolve("auto-output.pdf");
		List<Integer> recognizedPageCalls = new ArrayList<>();
		SearchablePdfPageOcr pageOcr = pngBytes -> {
			recognizedPageCalls.add(recognizedPageCalls.size());
			return List.of(new OcrWordBox("OCR結果", 10, 10, 100, 20));
		};

		searchablePdfLogic.createSearchablePdf(inputPath, outputPath, SearchablePdfMode.AUTO, 96, 20, pageOcr);

		// 2ページ（1、3ページ目）が空白のため、OCR呼び出しは2回。
		assertEquals(2, recognizedPageCalls.size());
		assertTrue(Files.exists(inputPath));
		String extracted = readPdfText(outputPath);
		assertTrue(extracted.contains("existing text"));
		assertTrue(extracted.contains("OCR結果"));
	}

	@Test
	@DisplayName("FORCE_OCRでは文字レイヤーの有無に関係なく全ページをOCR対象にする")
	void createSearchablePdfProcessesEveryPageInForceOcrMode() throws IOException {
		Path inputPath = createPdf("force-input.pdf", "existing text", "");
		Path outputPath = tempDirectory.resolve("force-output.pdf");
		AtomicInteger callCount = new AtomicInteger();
		SearchablePdfPageOcr pageOcr = pngBytes -> {
			callCount.incrementAndGet();
			return List.of(new OcrWordBox("OCR結果", 10, 10, 100, 20));
		};

		searchablePdfLogic.createSearchablePdf(inputPath, outputPath, SearchablePdfMode.FORCE_OCR, 96, 20, pageOcr);

		assertEquals(2, callCount.get());
	}

	@Test
	@DisplayName("OCR対象ページ数が上限を超えた場合は例外を投げ、OCRを1度も呼ばない")
	void createSearchablePdfThrowsWhenTargetPageCountExceedsLimitWithoutCallingOcr() throws IOException {
		Path inputPath = createPdf("limit-input.pdf", "", "", "");
		Path outputPath = tempDirectory.resolve("limit-output.pdf");
		AtomicInteger callCount = new AtomicInteger();
		SearchablePdfPageOcr pageOcr = pngBytes -> {
			callCount.incrementAndGet();
			return List.of();
		};

		PdfPageLimitExceededException exception = assertThrows(PdfPageLimitExceededException.class,
				() -> searchablePdfLogic.createSearchablePdf(inputPath, outputPath, SearchablePdfMode.AUTO, 96, 2,
						pageOcr));

		assertEquals(3, exception.getTargetPageCount());
		assertEquals(2, exception.getMaxPages());
		assertEquals(0, callCount.get());
	}

	@Test
	@DisplayName("既存の文字レイヤーを持つページはAUTOで変更しない")
	void createSearchablePdfDoesNotChangePagesWithExistingTextInAutoMode() throws IOException {
		Path inputPath = createPdf("keep-input.pdf", "existing text");
		Path outputPath = tempDirectory.resolve("keep-output.pdf");
		SearchablePdfPageOcr pageOcr = pngBytes -> {
			throw new AssertionError("文字レイヤーがあるページでOCRが呼ばれてはいけない。");
		};

		searchablePdfLogic.createSearchablePdf(inputPath, outputPath, SearchablePdfMode.AUTO, 96, 20, pageOcr);

		assertEquals("existing text", readPdfText(outputPath).strip());
	}

	/**
	 * テスト用のPDFを作成する。空文字を指定したページには文字を描画せず、スキャン画像のような空白ページとして扱う。
	 *
	 * @param fileName  作成するPDFファイル名
	 * @param pageTexts ページごとの本文。空文字は空白ページを表す
	 * @return 作成したPDFのパス
	 * @throws IOException PDFの作成に失敗した場合
	 */
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

	/**
	 * PDFの全ページのテキストを読み出す。
	 *
	 * @param path 読み込むPDFのパス
	 * @return 抽出したテキスト
	 * @throws IOException PDFの読み込みまたはテキスト抽出に失敗した場合
	 */
	private String readPdfText(Path path) throws IOException {
		try (PDDocument document = PdfDocumentLoader.load(path)) {
			return new PDFTextStripper().getText(document);
		}
	}
}
