package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;
import com.clip.ghost.pdfcontent.dto.GhostPdfDto;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.enums.PdfInsertOption;
import com.clip.ghost.pdfcontent.enums.PdfMarkdownDraftMode;

/**
 * PDF操作ロジックのテストクラス。
 */
class GhostPdfLogicTest {

	@TempDir
	Path tempDirectory;

	private GhostPdfLogic pdfLogic;

	@BeforeEach
	void setUp() {
		pdfLogic = new GhostPdfLogic();
		ReflectionTestUtils.setField(pdfLogic, "tmpDirectory", tempDirectory.toString());
	}

	@Test
	@DisplayName("レスポンス送信後にストリームがcloseされた時点で一時ファイルを削除する")
	void openTemporaryFileForResponseDeletesTemporaryFileAfterStreamIsClosed() throws IOException {
		Path inputPath = tempDirectory.resolve("convert.pdf");
		byte[] expectedBytes = "temporary pdf bytes".getBytes(StandardCharsets.UTF_8);
		Files.write(inputPath, expectedBytes);

		Resource resource = pdfLogic.openTemporaryFileForResponse(inputPath);

		// 他のpublicメソッドと違い、このメソッドから戻った時点では削除しない。送信途中で消えるとレスポンスが壊れる。
		assertTrue(inputPath.toFile().exists());
		assertEquals(expectedBytes.length, resource.contentLength());
		try (InputStream inputStream = resource.getInputStream()) {
			assertArrayEquals(expectedBytes, inputStream.readAllBytes());
		}

		// レスポンス本文を書き終えた後にSpringがストリームを閉じる。そこで削除される。
		assertFalse(inputPath.toFile().exists());
	}

	@Test
	@DisplayName("一時ファイルが存在しない場合は例外にする")
	void openTemporaryFileForResponseThrowsExceptionWhenTemporaryFileDoesNotExist() {
		Path inputPath = tempDirectory.resolve("missing.pdf");

		assertThrows(PdfProcessingException.class, () -> pdfLogic.openTemporaryFileForResponse(inputPath));
		assertFalse(inputPath.toFile().exists());
	}

	@Test
	@DisplayName("メタデータを返し、一時ファイルを削除する")
	void getPdfMetadataReturnsBasicInfoAndDeletesTemporaryFile() throws IOException {
		Path inputPath = createPdf("metadata.pdf", page(100, 200, 0), page(200, 300, 90));

		PdfMetadataResponse response = pdfLogic.getPdfMetadata(inputPath, "metadata.pdf", 123L);

		assertFalse(inputPath.toFile().exists());
		assertEquals("metadata.pdf", response.getFileName());
		assertEquals(123L, response.getFileSize());
		assertEquals(2, response.getPageCount());
		assertFalse(response.getEncrypted());
	}

	@Test
	@DisplayName("テキストを返し、一時ファイルを削除する")
	void extractPdfTextReturnsTextAndDeletesTemporaryFile() throws IOException {
		Path inputPath = createPdf("text.pdf", page(100, 200, 0), page(200, 300, 90));

		PdfTextResponse response = pdfLogic.extractPdfText(inputPath, "text.pdf", 456L);

		assertFalse(inputPath.toFile().exists());
		assertEquals("text.pdf", response.getFileName());
		assertEquals(456L, response.getFileSize());
		assertEquals(2, response.getPageCount());
		assertTrue(response.getText().contains("100"));
		assertTrue(response.getText().contains("200"));
	}

	@Test
	@DisplayName("ページ順のテキストを返し、一時ファイルを削除する")
	void extractPdfPageTextsReturnsPagesInOrderAndDeletesTemporaryFile() throws IOException {
		Path inputPath = createPdf("page-text.pdf", page(100, 200, 0), page(200, 300, 90));

		List<String> pageTexts = pdfLogic.extractPdfPageTexts(inputPath);

		assertFalse(inputPath.toFile().exists());
		assertEquals(2, pageTexts.size());
		assertTrue(pageTexts.get(0).contains("100"));
		assertTrue(pageTexts.get(1).contains("200"));
	}

	@Test
	@DisplayName("PDFが壊れている場合は例外にし、一時ファイルを削除する")
	void extractPdfPageTextsThrowsExceptionAndDeletesTemporaryFileWhenSourceIsBroken() throws IOException {
		Path inputPath = tempDirectory.resolve("broken-page-text.pdf");
		Files.writeString(inputPath, "not pdf");

		assertThrows(PdfProcessingException.class, () -> pdfLogic.extractPdfPageTexts(inputPath));

		assertFalse(inputPath.toFile().exists());
	}

	@Test
	@DisplayName("文字が無いページを変換し、一時ファイルを削除する")
	void extractPdfPageContentsConvertsBlankPagesAndDeletesTemporaryFile() throws IOException {
		Path inputPath = createBlankPdf("page-contents.pdf", 1);

		List<PdfPageContent> contents = pdfLogic.extractPdfPageContents(inputPath, 72, 20, PdfMarkdownDraftMode.AUTO,
				pngBytes -> "converted markdown");

		assertFalse(inputPath.toFile().exists());
		assertEquals(1, contents.size());
		assertEquals("converted markdown", contents.get(0).convertedText());
	}

	@Test
	@DisplayName("ページ上限超過でも一時ファイルを削除し、変換器を呼ばない")
	void extractPdfPageContentsDeletesTemporaryFileAndSkipsConverterWhenPageLimitExceeded() throws IOException {
		Path inputPath = createBlankPdf("page-limit.pdf", 2);
		AtomicInteger convertedCount = new AtomicInteger();

		assertThrows(PdfPageLimitExceededException.class,
				() -> pdfLogic.extractPdfPageContents(inputPath, 72, 1, PdfMarkdownDraftMode.AUTO,
						pngBytes -> "converted-" + convertedCount.incrementAndGet()));

		// 上限超過で拒否した場合も一時ファイルを残さず、課金の起点となる変換器も呼ばない。
		assertFalse(inputPath.toFile().exists());
		assertEquals(0, convertedCount.get());
	}

	@Test
	@DisplayName("指定ページを削除し、残りページのレイアウトを保つ")
	void deletePdfRemovesSelectedPagesAndKeepsPageLayout() throws IOException {
		Path inputPath = createPdf("delete-input.pdf", page(100, 200, 0), page(200, 300, 90), page(300, 400, 0),
				page(400, 500, 270));

		Path outputPath = pdfLogic.deletePdf(List.of(1, 3), inputPath);

		assertFalse(inputPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(200, 300, 90), page(400, 500, 270));
	}

	@Test
	@DisplayName("削除ページが空リストなら全ページを残し、入力を削除する")
	void deletePdfWithEmptyDeleteListKeepsAllPagesAndDeletesInput() throws IOException {
		Path inputPath = createPdf("empty-delete-input.pdf", page(100, 200, 0), page(200, 300, 90));

		Path outputPath = pdfLogic.deletePdf(List.of(), inputPath);

		assertFalse(inputPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90));
	}

	@Test
	@DisplayName("削除ページがnullなら全ページを残し、入力を削除する")
	void deletePdfWithNullDeleteListKeepsAllPagesAndDeletesInput() throws IOException {
		Path inputPath = createPdf("null-delete-input.pdf", page(100, 200, 0), page(200, 300, 90));

		Path outputPath = pdfLogic.deletePdf(null, inputPath);

		assertFalse(inputPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90));
	}

	@Test
	@DisplayName("重複・null・範囲外を含む指定でも範囲内のページだけ削除する")
	void deletePdfWithDuplicateNullAndOutOfRangePagesRemovesOnlyInRangeTargets() throws IOException {
		Path inputPath = createPdf("boundary-delete-input.pdf", page(100, 200, 0), page(200, 300, 90),
				page(300, 400, 180));

		Path outputPath = pdfLogic.deletePdf(Arrays.asList(null, 0, 2, 2, 99), inputPath);

		assertFalse(inputPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(300, 400, 180));
	}

	@Test
	@DisplayName("抽出ページをページ番号順に並べ、入力を削除する")
	void extractPdfKeepsRequestedPagesInPageNumberOrderAndDeletesInput() throws IOException {
		Path inputPath = createPdf("extract-input.pdf", page(100, 200, 0), page(200, 300, 90), page(300, 400, 180));

		Path outputPath = pdfLogic.extractPdf(List.of(3, 1, 3), inputPath);

		assertFalse(inputPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(300, 400, 180));
	}

	@Test
	@DisplayName("抽出ページが範囲外なら例外にし、入力を削除する")
	void extractPdfThrowsExceptionAndDeletesInputWhenPageIsOutOfRange() throws IOException {
		Path inputPath = createPdf("extract-out-of-range-input.pdf", page(100, 200, 0), page(200, 300, 90));

		assertThrows(PdfProcessingException.class, () -> pdfLogic.extractPdf(List.of(3), inputPath));
		assertFalse(inputPath.toFile().exists());
	}

	@Test
	@DisplayName("結合順を入力順に保ち、入力ファイルを削除する")
	void mergePdfKeepsInputOrderAndDeletesInputFiles() throws IOException {
		Path firstPath = createPdf("merge-first.pdf", page(100, 200, 0), page(200, 300, 90));
		Path secondPath = createPdf("merge-second.pdf", page(300, 400, 180));

		Path outputPath = pdfLogic.mergePdf(List.of(firstPath, secondPath));

		assertFalse(firstPath.toFile().exists());
		assertFalse(secondPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90), page(300, 400, 180));
	}

	@Test
	@DisplayName("結合対象が空の場合は例外にする")
	void mergePdfThrowsExceptionWhenInputListIsEmpty() {
		assertThrows(PdfProcessingException.class, () -> pdfLogic.mergePdf(List.of()));
	}

	@Test
	@DisplayName("1ページずつのPDFを含むZIPを作り、入力を削除する")
	void splitPdfCreatesZipWithSinglePagePdfsAndDeletesInput() throws IOException {
		Path inputPath = createPdf("split-input.pdf", page(100, 200, 0), page(200, 300, 90), page(300, 400, 180));

		Path outputPath = pdfLogic.splitPdf(inputPath, List.of());

		assertFalse(inputPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertSplitZip(outputPath, List.of("split-001.pdf", "split-002.pdf", "split-003.pdf"));
	}

	@Test
	@DisplayName("範囲ごとのPDFを含むZIPを作り、入力を削除する")
	void splitPdfCreatesZipWithRangePdfsAndDeletesInput() throws IOException {
		Path inputPath = createPdf("split-range-input.pdf", page(100, 200, 0), page(200, 300, 90),
				page(300, 400, 180));

		Path outputPath = pdfLogic.splitPdf(inputPath, List.of("1-2", "3"));

		assertFalse(inputPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertSplitZip(outputPath, List.of("pages_1-2.pdf", "pages_3.pdf"), List.of(2, 1));
	}

	@Test
	@DisplayName("差し込み・差し替え・末尾挿入をリクエスト順に適用する")
	void insertPdfSupportsInsertReplaceAndLastInsertInRequestOrder() throws IOException {
		Path originalPath = createPdf("original.pdf", page(100, 200, 0), page(200, 300, 90), page(300, 400, 180));
		Path firstInsertPath = createPdf("first-insert.pdf", page(410, 510, 270));
		Path secondInsertPath = createPdf("second-insert.pdf", page(420, 520, 0));
		Path replacePath = createPdf("replace.pdf", page(510, 610, 90), page(511, 611, 180));
		Path lastInsertPath = createPdf("last-insert.pdf", page(610, 710, 0));

		Path outputPath = pdfLogic.insertPdf(originalPath,
				List.of(insert(firstInsertPath, 1, PdfInsertOption.INSERT),
						insert(secondInsertPath, 1, PdfInsertOption.INSERT),
						insert(replacePath, 2, PdfInsertOption.REPLACE),
						insert(lastInsertPath, -1, PdfInsertOption.LAST_INSERT)));

		assertFalse(originalPath.toFile().exists());
		assertFalse(firstInsertPath.toFile().exists());
		assertFalse(secondInsertPath.toFile().exists());
		assertFalse(replacePath.toFile().exists());
		assertFalse(lastInsertPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(410, 510, 270), page(420, 520, 0), page(510, 610, 90),
				page(511, 611, 180), page(300, 400, 180), page(610, 710, 0));
	}

	@Test
	@DisplayName("最終ページの後ろへも差し込みできる")
	void insertPdfCanInsertAfterLastPageWithInsertOption() throws IOException {
		Path originalPath = createPdf("last-page-insert-original.pdf", page(100, 200, 0), page(200, 300, 90));
		Path insertPath = createPdf("last-page-insert.pdf", page(400, 500, 180));

		Path outputPath = pdfLogic.insertPdf(originalPath, List.of(insert(insertPath, 2, PdfInsertOption.INSERT)));

		assertFalse(originalPath.toFile().exists());
		assertFalse(insertPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90), page(400, 500, 180));
	}

	@Test
	@DisplayName("ページ複製時にCropBoxを保つ")
	void insertPdfPreservesCropBoxWhenCloningPages() throws IOException {
		PDRectangle cropBox = new PDRectangle(10, 20, 110, 120);
		Path originalPath = createPdfWithCropBox("crop-original.pdf", page(200, 300, 90), cropBox);

		Path outputPath = pdfLogic.insertPdf(originalPath, List.of());

		assertFalse(originalPath.toFile().exists());
		assertCropBox(outputPath, cropBox);
	}

	@Test
	@DisplayName("最終ページより後ろのページ指定は無視する")
	void insertPdfIgnoresPageNumberAfterLastPage() throws IOException {
		Path originalPath = createPdf("out-of-range-original.pdf", page(100, 200, 0), page(200, 300, 0));
		Path insertPath = createPdf("out-of-range-insert.pdf", page(400, 500, 0));

		Path outputPath = pdfLogic.insertPdf(originalPath, List.of(insert(insertPath, 3, PdfInsertOption.INSERT)));

		assertFalse(insertPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 0));
	}

	@Test
	@DisplayName("差し込みリストがnullなら元ページを保ち、入力を削除する")
	void insertPdfWithNullInsertListKeepsOriginalPagesAndDeletesOriginal() throws IOException {
		Path originalPath = createPdf("null-insert-list-original.pdf", page(100, 200, 0), page(200, 300, 90));

		Path outputPath = pdfLogic.insertPdf(originalPath, null);

		assertFalse(originalPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90));
	}

	@Test
	@DisplayName("差し込み要素がnullなら元ページを保ち、入力を削除する")
	void insertPdfWithNullInsertDtoKeepsOriginalPagesAndDeletesOriginal() throws IOException {
		Path originalPath = createPdf("null-insert-dto-original.pdf", page(100, 200, 0), page(200, 300, 90));

		Path outputPath = pdfLogic.insertPdf(originalPath, Collections.singletonList(null));

		assertFalse(originalPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90));
	}

	@Test
	@DisplayName("差し込みパスがnullなら元ページを保ち、入力を削除する")
	void insertPdfWithNullInsertPathKeepsOriginalPagesAndDeletesOriginal() throws IOException {
		Path originalPath = createPdf("null-insert-path-original.pdf", page(100, 200, 0), page(200, 300, 90));
		GhostPdfDto insertDto = insert(null, 1, PdfInsertOption.INSERT);

		Path outputPath = pdfLogic.insertPdf(originalPath, List.of(insertDto));

		assertFalse(originalPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90));
	}

	@Test
	@DisplayName("差し込みリストが空なら元ページを保ち、入力を削除する")
	void insertPdfWithEmptyInsertListKeepsOriginalPagesAndDeletesOriginal() throws IOException {
		Path originalPath = createPdf("empty-insert-original.pdf", page(100, 200, 0), page(200, 300, 90));

		Path outputPath = pdfLogic.insertPdf(originalPath, List.of());

		assertFalse(originalPath.toFile().exists());
		assertTrue(outputPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0), page(200, 300, 90));
	}

	@Test
	@DisplayName("差し込み方法が未指定の行は無視し、入力ファイルを削除する")
	void insertPdfIgnoresRequestAndDeletesInputFilesWhenInsertOptionIsMissing() throws IOException {
		Path originalPath = createPdf("missing-option-original.pdf", page(100, 200, 0));
		Path insertPath = createPdf("missing-option-insert.pdf", page(400, 500, 0));

		// 差し込み方法はenumで受けるため不正なコード値は届かない。未指定の行は他の未指定項目と同じく無視する。
		Path outputPath = pdfLogic.insertPdf(originalPath, List.of(insert(insertPath, 1, null)));

		assertFalse(originalPath.toFile().exists());
		assertFalse(insertPath.toFile().exists());
		assertPageLayout(outputPath, page(100, 200, 0));
	}

	@Test
	@DisplayName("差し込みPDFが壊れている場合は例外にし、入力ファイルを削除する")
	void insertPdfThrowsExceptionAndDeletesInputFilesWhenInsertSourceIsBroken() throws IOException {
		Path originalPath = createPdf("broken-insert-original.pdf", page(100, 200, 0));
		Path insertPath = tempDirectory.resolve("broken-insert.pdf");
		Files.writeString(insertPath, "not pdf");

		assertThrows(PdfProcessingException.class,
				() -> pdfLogic.insertPdf(originalPath, List.of(insert(insertPath, 1, PdfInsertOption.INSERT))));
		assertFalse(originalPath.toFile().exists());
		assertFalse(insertPath.toFile().exists());
	}

	@Test
	@DisplayName("アップロードPDFを設定した一時ディレクトリへ保存する")
	void loadPdfSavesUploadedPdfToConfiguredTemporaryDirectory() throws IOException {
		Path sourcePath = createPdf("upload-source.pdf", page(100, 200, 0));
		byte[] sourceBytes = Files.readAllBytes(sourcePath);
		MockMultipartFile pdfFile = new MockMultipartFile("originalFile", "upload.PDF", "application/pdf", sourceBytes);

		Path outputPath = pdfLogic.loadPdf(pdfFile);

		assertEquals(tempDirectory, outputPath.getParent());
		assertTrue(outputPath.toFile().exists());
		assertTrue(outputPath.getFileName().toString().endsWith(".PDF"));
		assertArrayEquals(sourceBytes, Files.readAllBytes(outputPath));
	}

	@Test
	@DisplayName("PDF以外のアップロードは例外にする")
	void loadPdfThrowsExceptionWhenUploadedFileIsNotPdf() {
		MockMultipartFile textFile = new MockMultipartFile("originalFile", "sample.txt", "text/plain",
				"not pdf".getBytes());

		assertThrows(PdfProcessingException.class, () -> pdfLogic.loadPdf(textFile));
	}

	@Test
	@DisplayName("アップロードファイルがnullの場合は例外にする")
	void loadPdfThrowsExceptionWhenUploadedFileIsNull() {
		assertThrows(PdfProcessingException.class, () -> pdfLogic.loadPdf(null));
	}

	@Test
	@DisplayName("アップロードPDFが空の場合は例外にする")
	void loadPdfThrowsExceptionWhenUploadedPdfIsEmpty() {
		MockMultipartFile emptyPdfFile = new MockMultipartFile("originalFile", "empty.pdf", "application/pdf",
				new byte[0]);

		assertThrows(PdfProcessingException.class, () -> pdfLogic.loadPdf(emptyPdfFile));
	}

	@Test
	@DisplayName("PDFが壊れている場合は例外にし、入力を削除する")
	void deletePdfThrowsExceptionAndDeletesInputWhenSourceIsBroken() throws IOException {
		Path inputPath = tempDirectory.resolve("broken.pdf");
		Files.writeString(inputPath, "not pdf");

		assertThrows(PdfProcessingException.class, () -> pdfLogic.deletePdf(List.of(1), inputPath));
		assertFalse(inputPath.toFile().exists());
	}

	private GhostPdfDto insert(Path path, int pageNumber, PdfInsertOption option) {
		GhostPdfDto dto = new GhostPdfDto();
		dto.setInsertPath(path);
		dto.setInsertPage(pageNumber);
		dto.setInsertOption(option);
		return dto;
	}

	private Path createPdf(String fileName, PageLayout... pageLayouts) throws IOException {
		Path path = tempDirectory.resolve(fileName);
		try (PDDocument document = new PDDocument()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (PageLayout pageLayout : pageLayouts) {
				PDPage page = new PDPage(new PDRectangle(pageLayout.width(), pageLayout.height()));
				page.setRotation(pageLayout.rotation());
				document.addPage(page);
				try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
					contentStream.beginText();
					contentStream.setFont(font, 12);
					contentStream.newLineAtOffset(20, 20);
					contentStream.showText(Integer.toString(Math.round(pageLayout.width())));
					contentStream.endText();
				}
			}
			if (document.getNumberOfPages() > 0) {
				PDResources inheritedResources = document.getPage(0).getResources();
				for (PDPage page : document.getPages()) {
					page.setResources(null);
				}
				document.getPages().getCOSObject().setItem(COSName.RESOURCES, inheritedResources);
			}
			document.save(path.toFile());
		}
		return path;
	}

	private Path createBlankPdf(String fileName, int pageCount) throws IOException {
		Path path = tempDirectory.resolve(fileName);
		try (PDDocument document = new PDDocument()) {
			for (int index = 0; index < pageCount; index++) {
				document.addPage(new PDPage(new PDRectangle(100, 200)));
			}
			document.save(path.toFile());
		}
		return path;
	}

	private Path createPdfWithCropBox(String fileName, PageLayout pageLayout, PDRectangle cropBox) throws IOException {
		Path path = tempDirectory.resolve(fileName);
		try (PDDocument document = new PDDocument()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			PDPage page = new PDPage(new PDRectangle(pageLayout.width(), pageLayout.height()));
			page.setCropBox(cropBox);
			page.setRotation(pageLayout.rotation());
			document.addPage(page);
			try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
				contentStream.beginText();
				contentStream.setFont(font, 12);
				contentStream.newLineAtOffset(20, 20);
				contentStream.showText(Integer.toString(Math.round(pageLayout.width())));
				contentStream.endText();
			}
			document.save(path.toFile());
		}
		return path;
	}

	private void assertCropBox(Path path, PDRectangle expectedCropBox) throws IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			PDRectangle actualCropBox = document.getPage(0).getCropBox();
			assertEquals(expectedCropBox.getLowerLeftX(), actualCropBox.getLowerLeftX());
			assertEquals(expectedCropBox.getLowerLeftY(), actualCropBox.getLowerLeftY());
			assertEquals(expectedCropBox.getWidth(), actualCropBox.getWidth());
			assertEquals(expectedCropBox.getHeight(), actualCropBox.getHeight());
		}
	}

	private void assertSplitZip(Path zipPath, List<String> expectedEntryNames) throws IOException {
		assertSplitZip(zipPath, expectedEntryNames, Collections.nCopies(expectedEntryNames.size(), 1));
	}

	private void assertSplitZip(Path zipPath, List<String> expectedEntryNames, List<Integer> expectedPageCounts)
			throws IOException {
		List<String> actualEntryNames = new ArrayList<>();
		List<Integer> actualPageCounts = new ArrayList<>();
		try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry zipEntry = zipInputStream.getNextEntry();
			while (zipEntry != null) {
				actualEntryNames.add(zipEntry.getName());
				try (PDDocument document = Loader.loadPDF(readCurrentZipEntry(zipInputStream))) {
					actualPageCounts.add(document.getNumberOfPages());
				}
				zipInputStream.closeEntry();
				zipEntry = zipInputStream.getNextEntry();
			}
		}
		assertEquals(expectedEntryNames, actualEntryNames);
		assertEquals(expectedPageCounts, actualPageCounts);
	}

	private byte[] readCurrentZipEntry(ZipInputStream zipInputStream) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		zipInputStream.transferTo(outputStream);
		return outputStream.toByteArray();
	}

	private void assertPageLayout(Path path, PageLayout... expectedLayouts) throws IOException {
		try (PDDocument document = Loader.loadPDF(path.toFile())) {
			assertEquals(expectedLayouts.length, document.getNumberOfPages());
			PDFTextStripper textStripper = new PDFTextStripper();
			for (int pageIndex = 0; pageIndex < expectedLayouts.length; pageIndex++) {
				PageLayout expected = expectedLayouts[pageIndex];
				PDPage actual = document.getPage(pageIndex);
				assertEquals(expected.width(), actual.getMediaBox().getWidth());
				assertEquals(expected.height(), actual.getMediaBox().getHeight());
				assertEquals(expected.rotation(), actual.getRotation());
				textStripper.setStartPage(pageIndex + 1);
				textStripper.setEndPage(pageIndex + 1);
				assertEquals(Integer.toString(Math.round(expected.width())),
						textStripper.getText(document).replaceAll("\\s+", ""));
			}
		}
	}

	private PageLayout page(float width, float height, int rotation) {
		return new PageLayout(width, height, rotation);
	}

	private record PageLayout(float width, float height, int rotation) {
	}
}
