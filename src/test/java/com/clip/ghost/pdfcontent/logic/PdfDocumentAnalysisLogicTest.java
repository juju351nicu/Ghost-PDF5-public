package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.dto.PdfPageThumbnail;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.enums.PdfMarkdownDraftMode;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfPasswordProtectedException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

/**
 * {@link PdfDocumentAnalysisLogic} の単体テスト。
 */
class PdfDocumentAnalysisLogicTest {

	@TempDir
	Path tempDirectory;

	@Test
	@DisplayName("メタデータを返し、入力ファイルは削除しない")
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
	@DisplayName("テキストを返し、入力ファイルは削除しない")
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
	@DisplayName("ページ順のテキストを返し、空ページも省略しない")
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
	@DisplayName("PDFを読めない場合も入力ファイルを残す")
	void extractPdfPageTextsKeepsSourceWhenPdfCannotBeRead() throws IOException {
		Path inputPath = tempDirectory.resolve("broken.pdf");
		Files.writeString(inputPath, "not a pdf");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		assertThrows(PdfProcessingException.class, () -> analysisLogic.extractPdfPageTexts(inputPath));

		assertTrue(Files.exists(inputPath));
	}

	@Test
	@DisplayName("AUTOは文字が無いページだけを変換し、入力ファイルを残す")
	void extractPdfPageContentsConvertsOnlyBlankPagesAndKeepsSource() throws IOException {
		Path inputPath = createPdf("contents.pdf", "first page", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();
		List<byte[]> convertedImages = new ArrayList<>();

		List<PdfPageContent> contents = analysisLogic.extractPdfPageContents(inputPath, 100, 20,
				PdfMarkdownDraftMode.AUTO, pngBytes -> {
					convertedImages.add(pngBytes);
					return "converted markdown";
				});

		assertTrue(Files.exists(inputPath));
		assertEquals(2, contents.size());
		assertTrue(contents.get(0).text().contains("first page"));
		assertNull(contents.get(0).convertedText());
		assertTrue(contents.get(1).text().isBlank());
		assertEquals("converted markdown", contents.get(1).convertedText());
		assertEquals(1, convertedImages.size());
		byte[] png = convertedImages.get(0);
		assertNotNull(png);
		// PNGシグネチャ（0x89 'P' 'N' 'G'）を確認し、画像化されたページが変換器へ渡っていることを検証する。
		assertTrue(png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
	}

	@Test
	@DisplayName("変換対象をページ順に1ページずつ変換する")
	void extractPdfPageContentsConvertsBlankPagesOneByOneInPageOrder() throws IOException {
		Path inputPath = createPdf("order.pdf", "", "second page", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();
		AtomicInteger convertedCount = new AtomicInteger();

		List<PdfPageContent> contents = analysisLogic.extractPdfPageContents(inputPath, 100, 20, PdfMarkdownDraftMode.AUTO,
				pngBytes -> "converted-" + convertedCount.incrementAndGet());

		assertEquals(2, convertedCount.get());
		assertEquals("converted-1", contents.get(0).convertedText());
		assertNull(contents.get(1).convertedText());
		assertEquals("converted-2", contents.get(2).convertedText());
	}

	@Test
	@DisplayName("1ページの変換失敗でも成功ページの結果を返す")
	void extractPdfPageContentsKeepsSucceededPagesWhenConverterFailsForOnePage() throws IOException {
		Path inputPath = createPdf("partial.pdf", "", "second page", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();
		AtomicInteger convertedCount = new AtomicInteger();

		List<PdfPageContent> contents = analysisLogic.extractPdfPageContents(inputPath, 100, 20,
				PdfMarkdownDraftMode.AUTO, pngBytes -> {
					if (convertedCount.incrementAndGet() == 1) {
						throw new IllegalArgumentException("変換に失敗しました。");
					}
					return "converted markdown";
				});

		// 1ページの失敗で全体を捨てず、成功したページの変換結果を返す。
		assertEquals(2, convertedCount.get());
		assertEquals(3, contents.size());
		assertNull(contents.get(0).convertedText());
		assertTrue(contents.get(0).conversionFailed());
		assertFalse(contents.get(1).conversionFailed());
		assertEquals("converted markdown", contents.get(2).convertedText());
		assertFalse(contents.get(2).conversionFailed());
		assertTrue(Files.exists(inputPath));
	}

	@Test
	@DisplayName("全ページ失敗時は最初の失敗をそのまま伝播する")
	void extractPdfPageContentsPropagatesFirstFailureWhenEveryTargetPageFails() throws IOException {
		Path inputPath = createPdf("all-failed.pdf", "", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();
		AtomicInteger convertedCount = new AtomicInteger();

		// 全滅は部分的成功ではないため、HTTP statusを変えないよう最初の失敗をそのまま伝播する。
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> analysisLogic.extractPdfPageContents(inputPath, 100, 20, PdfMarkdownDraftMode.AUTO, pngBytes -> {
					throw new IllegalArgumentException("変換に失敗しました。" + convertedCount.incrementAndGet());
				}));

		assertEquals("変換に失敗しました。1", exception.getMessage());
		assertEquals(2, convertedCount.get());
		assertTrue(Files.exists(inputPath));
	}

	@Test
	@DisplayName("変換対象が無い場合は文字レイヤーのページをそのまま返す")
	void extractPdfPageContentsKeepsTextPagesWhenThereIsNoConversionTarget() throws IOException {
		Path inputPath = createPdf("no-target.pdf", "first page", "second page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		List<PdfPageContent> contents = analysisLogic.extractPdfPageContents(inputPath, 100, 20,
				PdfMarkdownDraftMode.AUTO, pngBytes -> {
					throw new IllegalStateException("変換対象が無いページで変換器が呼ばれました。");
				});

		// 変換対象0ページを「全滅」と数えると、文字レイヤーだけのPDFが失敗になってしまう。
		assertEquals(2, contents.size());
		assertFalse(contents.get(0).conversionFailed());
		assertFalse(contents.get(1).conversionFailed());
	}

	@Test
	@DisplayName("AUTOで変換対象が上限を超えたら変換器を呼ばずに拒否する")
	void extractPdfPageContentsRejectsWithoutCallingConverterWhenBlankPagesExceedMaxPages() throws IOException {
		Path inputPath = createPdf("limit.pdf", "first page", "", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();
		AtomicInteger convertedCount = new AtomicInteger();

		// PdfProcessingExceptionへ化けると400ではなく500になるため、例外型そのものを固定する。
		PdfPageLimitExceededException exception = assertThrows(PdfPageLimitExceededException.class,
				() -> analysisLogic.extractPdfPageContents(inputPath, 100, 1, PdfMarkdownDraftMode.AUTO,
						pngBytes -> "converted-" + convertedCount.incrementAndGet()));

		// 課金は変換器の呼び出しで発生するため、1度も呼ばれないことがコストガードの中核。
		assertEquals(0, convertedCount.get());
		assertEquals(2, exception.getTargetPageCount());
		assertEquals(1, exception.getMaxPages());
		assertTrue(Files.exists(inputPath));
	}

	@Test
	@DisplayName("VISIONは文字レイヤーのあるページも含め全ページを変換する")
	void extractPdfPageContentsConvertsEveryPageInVisionMode() throws IOException {
		Path inputPath = createPdf("vision.pdf", "first page", "", "third page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();
		AtomicInteger convertedCount = new AtomicInteger();

		List<PdfPageContent> contents = analysisLogic.extractPdfPageContents(inputPath, 100, 20,
				PdfMarkdownDraftMode.VISION, pngBytes -> "converted-" + convertedCount.incrementAndGet());

		// VISIONの中核。文字レイヤーを持つページも変換器へ渡らないと、表がフラットなテキストのままになる。
		assertEquals(3, convertedCount.get());
		assertEquals("converted-1", contents.get(0).convertedText());
		assertEquals("converted-2", contents.get(1).convertedText());
		assertEquals("converted-3", contents.get(2).convertedText());
		// 文字レイヤーのテキストは変換結果と別に保持したままにする。
		assertTrue(contents.get(0).text().contains("first page"));
		assertTrue(Files.exists(inputPath));
	}

	@Test
	@DisplayName("VISIONで総ページ数が上限を超えたら変換器を呼ばずに拒否する")
	void extractPdfPageContentsRejectsWithoutCallingConverterWhenTotalPagesExceedMaxPagesInVisionMode()
			throws IOException {
		Path inputPath = createPdf("vision-limit.pdf", "first page", "second page", "third page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();
		AtomicInteger convertedCount = new AtomicInteger();

		// VISIONでは総ページ数がそのまま対象ページ数になるため、AUTOなら通るPDFでも上限に掛かる。
		PdfPageLimitExceededException exception = assertThrows(PdfPageLimitExceededException.class,
				() -> analysisLogic.extractPdfPageContents(inputPath, 100, 2, PdfMarkdownDraftMode.VISION,
						pngBytes -> "converted-" + convertedCount.incrementAndGet()));

		assertEquals(0, convertedCount.get());
		assertEquals(3, exception.getTargetPageCount());
		assertEquals(2, exception.getMaxPages());
		// 「AUTOなら通るのにVISIONだと通らない」理由が伝わるよう、数えた対象の説明を持たせる。
		assertEquals(PdfMarkdownDraftMode.VISION.describeConversionTarget(), exception.getTargetDescription());
		assertTrue(Files.exists(inputPath));
	}

	@Test
	@DisplayName("指定したDPIでページを画像化する")
	void extractPdfPageContentsRendersWithGivenDpi() throws IOException {
		Path inputPath = createPdf("dpi.pdf", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		int lowDpiWidth = readConvertedImageWidth(analysisLogic, inputPath, 72);
		int highDpiWidth = readConvertedImageWidth(analysisLogic, inputPath, 144);

		assertTrue(highDpiWidth > lowDpiWidth);
	}

	@Test
	@DisplayName("PDF順にページ単位のdata URIサムネイルを返す")
	void extractPdfThumbnailsReturnsDataUriPerPageInPdfOrder() throws IOException {
		Path inputPath = createPdf("thumbnails.pdf", "first page", "second page", "third page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		List<PdfPageThumbnail> thumbnails = analysisLogic.extractPdfThumbnails(inputPath, 40, 100);

		assertTrue(Files.exists(inputPath));
		assertEquals(3, thumbnails.size());
		assertEquals(List.of(1, 2, 3), thumbnails.stream().map(PdfPageThumbnail::pageNumber).toList());
		for (PdfPageThumbnail thumbnail : thumbnails) {
			// 画面のimgタグへそのまま渡せる形にする。
			assertTrue(thumbnail.dataUri().startsWith("data:image/png;base64,"));
			BufferedImage image = readDataUriImage(thumbnail.dataUri());
			assertNotNull(image);
			assertEquals(image.getWidth(), thumbnail.width());
			assertEquals(image.getHeight(), thumbnail.height());
		}
	}

	@Test
	@DisplayName("指定したDPIがサムネイルの画像サイズへ反映される")
	void extractPdfThumbnailsUsesGivenDpiForImageSize() throws IOException {
		Path inputPath = createPdf("thumbnail-dpi.pdf", "first page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		PdfPageThumbnail lowDpiThumbnail = analysisLogic.extractPdfThumbnails(inputPath, 40, 100).get(0);
		PdfPageThumbnail highDpiThumbnail = analysisLogic.extractPdfThumbnails(inputPath, 80, 100).get(0);

		// DPIを2倍にすれば辺の長さも約2倍になる。ページサイズは入力PDF依存のため、絶対値ではなく比で見る。
		assertTrue(highDpiThumbnail.width() > lowDpiThumbnail.width());
		assertTrue(highDpiThumbnail.height() > lowDpiThumbnail.height());
		assertTrue(Math.abs(highDpiThumbnail.width() - lowDpiThumbnail.width() * 2) <= 2);
		assertTrue(Math.abs(highDpiThumbnail.height() - lowDpiThumbnail.height() * 2) <= 2);
	}

	@Test
	@DisplayName("総ページ数が上限を超えたら画像化せずに拒否する")
	void extractPdfThumbnailsRejectsWhenPageCountExceedsMaxPages() throws IOException {
		Path inputPath = createPdf("thumbnail-limit.pdf", "first page", "second page", "third page");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		// 上限判定はレンダリング前に行うため、1ページも画像化せずに例外で止まる。
		PdfPageLimitExceededException exception = assertThrows(PdfPageLimitExceededException.class,
				() -> analysisLogic.extractPdfThumbnails(inputPath, 40, 2));

		assertEquals(3, exception.getTargetPageCount());
		assertEquals(2, exception.getMaxPages());
		assertTrue(Files.exists(inputPath));
	}

	/**
	 * data URIのサムネイルをデコードして画像として読み込む。
	 *
	 * @param dataUri data URI形式のサムネイル
	 * @return 読み込んだ画像
	 * @throws IOException PNGとして読み込めない場合
	 */
	private BufferedImage readDataUriImage(String dataUri) throws IOException {
		byte[] pngBytes = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
		return ImageIO.read(new ByteArrayInputStream(pngBytes));
	}

	@Test
	@DisplayName("ユーザーパスワード付きPDFのメタデータ取得は、PDF処理例外ではなくパスワード保護例外になる")
	void getPdfMetadataThrowsPasswordProtectedExceptionForUserPasswordPdf() throws IOException {
		Path inputPath = createProtectedPdf("user-protected.pdf", "owner-secret", "user-secret");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		assertThrows(PdfPasswordProtectedException.class,
				() -> analysisLogic.getPdfMetadata(inputPath, "user-protected.pdf", 123L));
	}

	@Test
	@DisplayName("ユーザーパスワード付きPDFのテキスト抽出は、PDF処理例外ではなくパスワード保護例外になる")
	void extractPdfTextThrowsPasswordProtectedExceptionForUserPasswordPdf() throws IOException {
		Path inputPath = createProtectedPdf("user-protected-text.pdf", "owner-secret", "user-secret");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		assertThrows(PdfPasswordProtectedException.class,
				() -> analysisLogic.extractPdfText(inputPath, "user-protected-text.pdf", 456L));
	}

	@Test
	@DisplayName("所有者パスワードだけのPDFは、暗号化済みでも従来どおり読み込める")
	void getPdfMetadataReadsOwnerPasswordOnlyPdf() throws IOException {
		Path inputPath = createProtectedPdf("owner-protected.pdf", "owner-secret", "");
		PdfDocumentAnalysisLogic analysisLogic = new PdfDocumentAnalysisLogic();

		PdfMetadataResponse response = analysisLogic.getPdfMetadata(inputPath, "owner-protected.pdf", 789L);

		assertEquals(1, response.getPageCount());
		assertTrue(response.getEncrypted());
	}

	/**
	 * 指定DPIで画像化されたページの画像幅を取得する。
	 *
	 * @param analysisLogic 検証対象のロジック
	 * @param inputPath     読み込むPDFのパス
	 * @param renderDpi     画像化する解像度（DPI）
	 * @return 画像化されたPNGの幅（px）
	 * @throws IOException PNGの読み込みに失敗した場合
	 */
	private int readConvertedImageWidth(PdfDocumentAnalysisLogic analysisLogic, Path inputPath, int renderDpi)
			throws IOException {
		List<byte[]> convertedImages = new ArrayList<>();
		analysisLogic.extractPdfPageContents(inputPath, renderDpi, 20, PdfMarkdownDraftMode.AUTO, pngBytes -> {
			convertedImages.add(pngBytes);
			return "converted markdown";
		});
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(convertedImages.get(0)));
		return image.getWidth();
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

	/**
	 * パスワードで保護したテスト用PDFを1ページ作成する。
	 * <p>
	 * {@code userPassword} を空文字にすると所有者パスワードだけの保護になり、PDFBoxは空パスワードで開ける。
	 * ユーザーパスワードを指定した場合だけ読み込み時に失敗する。この違いを固定するためのヘルパー。
	 *
	 * @param fileName       作成するファイル名
	 * @param ownerPassword  所有者パスワード
	 * @param userPassword   PDFを開くために必要なユーザーパスワード。空文字の場合は設定しない
	 * @return 作成したPDFのパス
	 * @throws IOException PDFの作成に失敗した場合
	 */
	private Path createProtectedPdf(String fileName, String ownerPassword, String userPassword) throws IOException {
		Path path = tempDirectory.resolve(fileName);
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage());
			StandardProtectionPolicy protectionPolicy = new StandardProtectionPolicy(ownerPassword, userPassword,
					new AccessPermission());
			protectionPolicy.setEncryptionKeyLength(128);
			document.protect(protectionPolicy);
			document.save(path.toFile());
		}
		return path;
	}
}
