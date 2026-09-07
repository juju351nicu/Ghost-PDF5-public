package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.response.ApiResultType;
import com.clip.ghost.pdfcontent.dto.ExtractPdfRequest;
import com.clip.ghost.pdfcontent.dto.GhostPdfDto;
import com.clip.ghost.pdfcontent.dto.InsertPdfRequest;
import com.clip.ghost.pdfcontent.dto.MergePdfRequest;
import com.clip.ghost.pdfcontent.dto.OriginalPdfRequest;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.dto.SplitPdfRequest;

/**
 * {@link GhostPdfService} のリクエスト変換とPDF処理ロジック呼び出しを検証するテスト。
 * <p>
 * Controllerから受け取ったフォームをLogic用DTOへ変換する仕様と、PDFレスポンス生成の呼び出し順を固定する。
 */
@ExtendWith(MockitoExtension.class)
class GhostPdfServiceTest {
	private static final String TEST_PDF_RESOURCE_PATH = "/pdf/sample.pdf";
	private static final String ORIGINAL_FILE_PART_NAME = "originalFile";
	private static final String INSERT_FILE_PART_NAME_0 = "insertPdfForm[0].insertFile";

	@InjectMocks
	private GhostPdfService pdfService;

	@Mock
	private GhostPdfLogic pdfLogic;

	@Test
	@DisplayName("PDFプレビューでは編集元PDFを読み込み、PDFレスポンスを返す")
	void showPdfReturnsPdfResponse() throws Exception {

		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubPdfResponse(path, contents);

		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		ResponseEntity<Resource> result = pdfService.showPdf(form);

		verify(pdfLogic, times(1)).loadPdf(any(MultipartFile.class));
		verify(pdfLogic, times(1)).openTemporaryFileForResponse(any(Path.class));
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDFメタデータ取得では編集元PDFを読み込み、基本情報レスポンスを返す")
	void getPdfMetadataReturnsMetadataResponse() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createNamedOriginalPdfFile("sample.pdf", contents);
		PdfMetadataResponse metadata = new PdfMetadataResponse();
		metadata.setFileName("sample.pdf");
		metadata.setFileSize(originalFile.getSize());
		metadata.setPageCount(1);
		metadata.setEncrypted(false);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(originalFile);

		doReturn(path).when(pdfLogic).loadPdf(any(MultipartFile.class));
		doReturn(metadata).when(pdfLogic).getPdfMetadata(path, "sample.pdf", originalFile.getSize());

		ResponseEntity<ApiResult<PdfMetadataResponse>> result = pdfService.getPdfMetadata(form);

		verify(pdfLogic, times(1)).loadPdf(originalFile);
		verify(pdfLogic, times(1)).getPdfMetadata(path, "sample.pdf", originalFile.getSize());
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());
		assertEquals(metadata, result.getBody().getData());
		assertEquals(ApiResultType.INFO, result.getBody().getResultType());
		assertTrue(result.getBody().getMessageList().isEmpty());
	}

	@Test
	@DisplayName("PDFテキスト抽出では編集元PDFを読み込み、テキストレスポンスを返す")
	void extractPdfTextReturnsTextResponse() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createNamedOriginalPdfFile("sample.pdf", contents);
		PdfTextResponse textResponse = new PdfTextResponse();
		textResponse.setFileName("sample.pdf");
		textResponse.setFileSize(originalFile.getSize());
		textResponse.setPageCount(1);
		textResponse.setText("sample text");
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(originalFile);

		doReturn(path).when(pdfLogic).loadPdf(originalFile);
		doReturn(textResponse).when(pdfLogic).extractPdfText(path, "sample.pdf", originalFile.getSize());

		ResponseEntity<ApiResult<PdfTextResponse>> result = pdfService.extractPdfText(form);

		verify(pdfLogic, times(1)).loadPdf(originalFile);
		verify(pdfLogic, times(1)).extractPdfText(path, "sample.pdf", originalFile.getSize());
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertNotNull(result.getBody());
		assertEquals(textResponse, result.getBody().getData());
	}

	@Test
	@DisplayName("PDFページ抽出では抽出後PDFレスポンスを返す")
	void extractPdfByPagesReturnsPdfResponse() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createOriginalPdfFile(contents);
		ExtractPdfRequest form = new ExtractPdfRequest();
		form.setOriginalFile(originalFile);
		form.setExtractPages(List.of(1));

		doReturn(path).when(pdfLogic).loadPdf(any(MultipartFile.class));
		doReturn(path).when(pdfLogic).extractPdf(eq(List.of(1)), eq(path));
		doReturn(new ByteArrayResource(contents)).when(pdfLogic).openTemporaryFileForResponse(any(Path.class));

		ResponseEntity<Resource> result = pdfService.extractPdfByPages(form);

		verify(pdfLogic, times(1)).loadPdf(originalFile);
		verify(pdfLogic, times(1)).extractPdf(eq(List.of(1)), eq(path));
		verify(pdfLogic, times(1)).openTemporaryFileForResponse(path);
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDF結合では複数PDFを読み込み、結合後PDFレスポンスを返す")
	void mergePdfsReturnsPdfResponse() throws Exception {
		Path firstPath = testPdfPath();
		Path secondPath = Path.of(firstPath + ".second");
		Path mergePath = Path.of(firstPath + ".merged");
		byte[] contents = readTestPdf();
		MockMultipartFile firstFile = createNamedOriginalPdfFile("first.pdf", contents);
		MockMultipartFile secondFile = createNamedOriginalPdfFile("second.pdf", contents);
		MergePdfRequest form = new MergePdfRequest();
		form.setMergeFiles(List.of(firstFile, secondFile));

		doReturn(firstPath).when(pdfLogic).loadPdf(firstFile);
		doReturn(secondPath).when(pdfLogic).loadPdf(secondFile);
		doReturn(mergePath).when(pdfLogic).mergePdf(List.of(firstPath, secondPath));
		doReturn(new ByteArrayResource(contents)).when(pdfLogic).openTemporaryFileForResponse(mergePath);

		ResponseEntity<Resource> result = pdfService.mergePdfs(form);

		verify(pdfLogic, times(1)).loadPdf(firstFile);
		verify(pdfLogic, times(1)).loadPdf(secondFile);
		verify(pdfLogic, times(1)).mergePdf(List.of(firstPath, secondPath));
		verify(pdfLogic, times(1)).openTemporaryFileForResponse(mergePath);
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDF分割では分割後ZIPレスポンスを返す")
	void splitPdfReturnsZipResponse() throws Exception {
		Path inputPath = testPdfPath();
		Path splitZipPath = Path.of(inputPath + ".zip");
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createOriginalPdfFile(contents);
		SplitPdfRequest form = new SplitPdfRequest();
		form.setOriginalFile(originalFile);

		doReturn(inputPath).when(pdfLogic).loadPdf(originalFile);
		doReturn(splitZipPath).when(pdfLogic).splitPdf(inputPath, null);
		doReturn(new ByteArrayResource(contents)).when(pdfLogic).openTemporaryFileForResponse(splitZipPath);

		ResponseEntity<Resource> result = pdfService.splitPdf(form);

		verify(pdfLogic, times(1)).loadPdf(originalFile);
		verify(pdfLogic, times(1)).splitPdf(inputPath, null);
		verify(pdfLogic, times(1)).openTemporaryFileForResponse(splitZipPath);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(MediaType.valueOf("application/zip"), result.getHeaders().getContentType());
		// byte配列を経由しないストリーム化後もContent-Lengthを維持する。
		assertEquals(contents.length, result.getHeaders().getContentLength());
	}

	@Test
	@DisplayName("PDF挿入では差し込みフォームがnullの場合に空DTOリストを渡す")
	@SuppressWarnings("unchecked")
	void insertPdfsPassesEmptyDtoListWhenInsertPdfFormIsNull() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		form.setInsertPdfForm(null);

		stubInsertPdfProcessing(path, contents);

		ResponseEntity<Resource> result = pdfService.insertPdfs(form);

		ArgumentCaptor<List<GhostPdfDto>> captor = ArgumentCaptor.forClass(List.class);
		verify(pdfLogic, times(1)).insertPdf(any(Path.class), captor.capture());
		assertTrue(captor.getValue().isEmpty());
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDF挿入では削除ページ指定が空の場合に削除処理を呼ばない")
	void insertPdfsSkipsDeletePdfWhenDeletePagesAreEmpty() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		form.setOriginalDeletePages(List.of());
		form.setInsertPdfForm(List.of());

		stubInsertPdfProcessing(path, contents);

		ResponseEntity<Resource> result = pdfService.insertPdfs(form);

		verify(pdfLogic, times(1)).loadPdf(any(MultipartFile.class));
		verify(pdfLogic, never()).deletePdf(org.mockito.ArgumentMatchers.<Integer>anyList(), any(Path.class));
		verify(pdfLogic, times(1)).insertPdf(eq(path), any());
		verify(pdfLogic, times(1)).openTemporaryFileForResponse(any(Path.class));
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDF挿入では削除ページ指定がある場合に削除後PDFへ差し込み処理を行う")
	void insertPdfsUsesDeletedPdfPathWhenDeletePagesAreSpecified() throws Exception {
		Path originalPath = testPdfPath();
		Path deletedPath = Path.of(originalPath + ".deleted");
		Path mergedPath = Path.of(originalPath + ".merged");
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(originalFile);
		form.setOriginalDeletePages(List.of(1));
		form.setInsertPdfForm(List.of());

		doReturn(originalPath).when(pdfLogic).loadPdf(any(MultipartFile.class));
		doReturn(deletedPath).when(pdfLogic).deletePdf(eq(List.of(1)), eq(originalPath));
		doReturn(mergedPath).when(pdfLogic).insertPdf(eq(deletedPath), any());
		doReturn(new ByteArrayResource(contents)).when(pdfLogic).openTemporaryFileForResponse(eq(mergedPath));

		ResponseEntity<Resource> result = pdfService.insertPdfs(form);

		InOrder orderedCalls = inOrder(pdfLogic);
		orderedCalls.verify(pdfLogic).loadPdf(any(MultipartFile.class));
		orderedCalls.verify(pdfLogic).deletePdf(eq(List.of(1)), eq(originalPath));
		orderedCalls.verify(pdfLogic).insertPdf(eq(deletedPath), any());
		orderedCalls.verify(pdfLogic).openTemporaryFileForResponse(eq(mergedPath));
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDF挿入ではnullの差し込みフォーム行をDTOへ変換しない")
	@SuppressWarnings("unchecked")
	void insertPdfsSkipsNullInsertPdfFormRow() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createOriginalPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(originalFile);
		form.setInsertPdfForm(Collections.singletonList(null));

		stubInsertPdfProcessing(path, contents);

		ResponseEntity<Resource> result = pdfService.insertPdfs(form);

		ArgumentCaptor<List<GhostPdfDto>> captor = ArgumentCaptor.forClass(List.class);
		verify(pdfLogic, times(1)).loadPdf(any(MultipartFile.class));
		verify(pdfLogic, times(1)).insertPdf(any(Path.class), captor.capture());
		assertTrue(captor.getValue().isEmpty());
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDF挿入では空の差し込みファイル行をDTOへ変換しない")
	@SuppressWarnings("unchecked")
	void insertPdfsSkipsEmptyInsertFile() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createOriginalPdfFile(contents);
		MockMultipartFile emptyInsertFile = createEmptyInsertPdfFile();
		InsertPdfRequest insertForm = new InsertPdfRequest();
		insertForm.setInsertFile(emptyInsertFile);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(originalFile);
		form.setInsertPdfForm(List.of(insertForm));

		stubInsertPdfProcessing(path, contents);

		ResponseEntity<Resource> result = pdfService.insertPdfs(form);

		ArgumentCaptor<List<GhostPdfDto>> captor = ArgumentCaptor.forClass(List.class);
		verify(pdfLogic, times(1)).loadPdf(any(MultipartFile.class));
		verify(pdfLogic, times(1)).insertPdf(any(Path.class), captor.capture());
		assertTrue(captor.getValue().isEmpty());
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDF挿入ではページ番号と差し込み種別をDTOへ反映する")
	@SuppressWarnings("unchecked")
	void insertPdfsMapsSpecifiedInsertPageAndOptionToDto() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile originalFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);
		InsertPdfRequest insertForm = new InsertPdfRequest();
		insertForm.setInsertFile(insertPdfFile);
		insertForm.setInsertPage(5);
		insertForm.setInsertOption(PdfConstants.OPTION_REPLACE);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(originalFile);
		form.setInsertPdfForm(List.of(insertForm));

		stubInsertPdfProcessing(path, contents);

		ResponseEntity<Resource> result = pdfService.insertPdfs(form);

		ArgumentCaptor<List<GhostPdfDto>> captor = ArgumentCaptor.forClass(List.class);
		verify(pdfLogic, times(2)).loadPdf(any(MultipartFile.class));
		verify(pdfLogic, times(1)).insertPdf(any(Path.class), captor.capture());
		assertEquals(1, captor.getValue().size());
		assertEquals(5, captor.getValue().get(0).getInsertPage());
		assertEquals(PdfConstants.OPTION_REPLACE, captor.getValue().get(0).getInsertOption());
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@DisplayName("PDFページ削除では削除後PDFレスポンスを返す")
	void deletePdfByPagesReturnsPdfResponse() throws Exception {

		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);

		stubDeletePdfProcessing(path, contents);

		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		form.setOriginalDeletePages(List.of(1, 2));
		ResponseEntity<Resource> result = pdfService.deletePdfByPages(form);

		verify(pdfLogic, times(1)).loadPdf(any(MultipartFile.class));
		verify(pdfLogic, times(1)).deletePdf(org.mockito.ArgumentMatchers.<Integer>anyList(), any(Path.class));
		verify(pdfLogic, times(1)).openTemporaryFileForResponse(any(Path.class));
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("PDF挿入では未指定のページ番号と差し込み種別に既存default値を設定する")
	void insertPdfsUsesDefaultInsertPageAndOption() throws Exception {
		Path path = testPdfPath();
		byte[] contents = readTestPdf();
		MockMultipartFile mockPdfFile = createOriginalPdfFile(contents);
		MockMultipartFile insertPdfFile = createInsertPdfFile(contents);
		OriginalPdfRequest form = new OriginalPdfRequest();
		form.setOriginalFile(mockPdfFile);
		InsertPdfRequest insertForm = new InsertPdfRequest();
		insertForm.setInsertFile(insertPdfFile);
		form.setInsertPdfForm(List.of(insertForm));

		stubInsertPdfProcessing(path, contents);

		ResponseEntity<Resource> result = pdfService.insertPdfs(form);
		ArgumentCaptor<List<GhostPdfDto>> captor = ArgumentCaptor.forClass(List.class);
		verify(pdfLogic, times(2)).loadPdf(any(MultipartFile.class));
		verify(pdfLogic, times(1)).insertPdf(any(Path.class), captor.capture());
		verify(pdfLogic, times(1)).openTemporaryFileForResponse(any(Path.class));
		assertEquals(1, captor.getValue().size());
		assertEquals(-1, captor.getValue().get(0).getInsertPage());
		assertEquals(PdfConstants.OPTION_LAST_INSERT, captor.getValue().get(0).getInsertOption());
		assertEquals(HttpStatus.OK, result.getStatusCode());
	}

	/**
	 * プレビュー用のPDF読み込みとレスポンス変換をmockする。
	 *
	 * @param path     PDF一時ファイルパス
	 * @param contents PDFレスポンス本文
	 */
	private void stubPdfResponse(Path path, byte[] contents) {
		doReturn(path).when(pdfLogic).loadPdf(any(MultipartFile.class));
		doReturn(new ByteArrayResource(contents)).when(pdfLogic).openTemporaryFileForResponse(any(Path.class));
	}

	/**
	 * 差し込みPDF処理のLogic呼び出しをmockする。
	 *
	 * @param path     PDF一時ファイルパス
	 * @param contents PDFレスポンス本文
	 */
	private void stubInsertPdfProcessing(Path path, byte[] contents) {
		doReturn(path).when(pdfLogic).loadPdf(any(MultipartFile.class));
		doReturn(path).when(pdfLogic).insertPdf(any(Path.class), any());
		doReturn(new ByteArrayResource(contents)).when(pdfLogic).openTemporaryFileForResponse(any(Path.class));
	}

	/**
	 * ページ削除PDF処理のLogic呼び出しをmockする。
	 *
	 * @param path     PDF一時ファイルパス
	 * @param contents PDFレスポンス本文
	 */
	private void stubDeletePdfProcessing(Path path, byte[] contents) {
		doReturn(path).when(pdfLogic).loadPdf(any(MultipartFile.class));
		doReturn(path).when(pdfLogic).deletePdf(org.mockito.ArgumentMatchers.<Integer>anyList(), any(Path.class));
		doReturn(new ByteArrayResource(contents)).when(pdfLogic).openTemporaryFileForResponse(any(Path.class));
	}

	/**
	 * 編集元PDFとして扱うテスト用MultipartFileを作成する。
	 *
	 * @param contents テストPDFの内容
	 * @return 編集元PDFのMultipartFile
	 */
	private MockMultipartFile createOriginalPdfFile(byte[] contents) {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, "", MediaType.APPLICATION_PDF_VALUE, contents);
	}

	private MockMultipartFile createNamedOriginalPdfFile(String fileName, byte[] contents) {
		return new MockMultipartFile(ORIGINAL_FILE_PART_NAME, fileName, MediaType.APPLICATION_PDF_VALUE, contents);
	}

	/**
	 * 差し込みPDFとして扱うテスト用MultipartFileを作成する。
	 *
	 * @param contents テストPDFの内容
	 * @return 差し込みPDFのMultipartFile
	 */
	private MockMultipartFile createInsertPdfFile(byte[] contents) {
		return new MockMultipartFile(INSERT_FILE_PART_NAME_0, "", MediaType.APPLICATION_PDF_VALUE, contents);
	}

	/**
	 * ファイル未選択行を表す空の差し込みPDFを作成する。
	 *
	 * @return 空の差し込みPDF MultipartFile
	 */
	private MockMultipartFile createEmptyInsertPdfFile() {
		return new MockMultipartFile(INSERT_FILE_PART_NAME_0, "", MediaType.APPLICATION_PDF_VALUE, new byte[0]);
	}

	private Path testPdfPath() throws Exception {
		URL resource = getClass().getResource(TEST_PDF_RESOURCE_PATH);
		assertNotNull(resource, "テストPDFリソースが見つかりません。");
		return Path.of(resource.toURI());
	}

	private byte[] readTestPdf() throws IOException {
		try (InputStream inputStream = getClass().getResourceAsStream(TEST_PDF_RESOURCE_PATH)) {
			assertNotNull(inputStream, "テストPDFリソースが見つかりません。");
			return inputStream.readAllBytes();
		}
	}
}
