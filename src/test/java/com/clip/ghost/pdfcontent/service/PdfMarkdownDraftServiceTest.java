package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

/**
 * {@link PdfMarkdownDraftService} のページ番号付与、テキスト正規化、Markdown生成を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfMarkdownDraftServiceTest {

	@InjectMocks
	private PdfMarkdownDraftService service;

	@Mock
	private GhostPdfLogic pdfLogic;

	@Test
	@DisplayName("PDF情報とページ順を維持したMarkdown下書きレスポンスを返す")
	void generateMarkdownDraftReturnsMetadataAndPagesInPdfOrder() {
		Path inputPath = Path.of("temporary", "sample.pdf");
		MockMultipartFile originalFile = createPdfFile("sample.pdf", new byte[] { 1, 2, 3 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of("first page", "second page")).when(pdfLogic).extractPdfPageTexts(inputPath);

		ResponseEntity<PdfMarkdownDraftResponse> result = service.generateMarkdownDraft(form);

		PdfMarkdownDraftResponse response = result.getBody();
		assertNotNull(response);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("sample.pdf", response.getFileName());
		assertEquals(originalFile.getSize(), response.getFileSize());
		assertEquals(2, response.getPageCount());
		assertPage(response.getPages().get(0), 1, "first page");
		assertPage(response.getPages().get(1), 2, "second page");
		assertEquals("## Page 1\n\nfirst page\n\n## Page 2\n\nsecond page", response.getMarkdown());

		InOrder orderedCalls = inOrder(pdfLogic);
		orderedCalls.verify(pdfLogic).loadPdf(originalFile);
		orderedCalls.verify(pdfLogic).extractPdfPageTexts(inputPath);
	}

	@Test
	@DisplayName("改行と末尾空白を正規化し、空ページの見出しを残す")
	void generateMarkdownDraftNormalizesTextAndKeepsEmptyPageHeading() {
		Path inputPath = Path.of("temporary", "line-endings.pdf");
		MockMultipartFile originalFile = createPdfFile("line-endings.pdf", new byte[] { 1 });
		PdfMarkdownDraftRequest form = createRequest(originalFile);
		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(List.of("first\r\nline  \r\n", " \t\r\n", "third\rline\t  ")).when(pdfLogic)
				.extractPdfPageTexts(inputPath);

		PdfMarkdownDraftResponse response = service.generateMarkdownDraft(form).getBody();

		assertNotNull(response);
		assertPage(response.getPages().get(0), 1, "first\nline");
		assertPage(response.getPages().get(1), 2, "");
		assertPage(response.getPages().get(2), 3, "third\nline");
		assertEquals("## Page 1\n\nfirst\nline\n\n## Page 2\n\n## Page 3\n\nthird\nline",
				response.getMarkdown());
		assertFalse(response.getMarkdown().endsWith("\n"));
	}

	/**
	 * テスト用のPDFアップロードファイルを生成する。
	 *
	 * @param fileName アップロード時のファイル名
	 * @param contents ファイル内容
	 * @return PDF multipartファイル
	 */
	private MockMultipartFile createPdfFile(String fileName, byte[] contents) {
		return new MockMultipartFile("originalFile", fileName, MediaType.APPLICATION_PDF_VALUE, contents);
	}

	/**
	 * テスト用のMarkdown下書きリクエストを生成する。
	 *
	 * @param originalFile 生成元PDF
	 * @return Markdown下書きリクエスト
	 */
	private PdfMarkdownDraftRequest createRequest(MockMultipartFile originalFile) {
		PdfMarkdownDraftRequest request = new PdfMarkdownDraftRequest();
		request.setOriginalFile(originalFile);
		return request;
	}

	/**
	 * 1ページ分の番号と本文を検証する。
	 *
	 * @param page               検証対象ページ
	 * @param expectedPageNumber 期待するページ番号
	 * @param expectedText       期待するページ本文
	 */
	private void assertPage(PdfMarkdownDraftPageResponse page, int expectedPageNumber, String expectedText) {
		assertEquals(expectedPageNumber, page.getPageNumber());
		assertEquals(expectedText, page.getText());
	}
}
