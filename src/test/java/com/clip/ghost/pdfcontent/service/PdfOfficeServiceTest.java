package com.clip.ghost.pdfcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.clip.ghost.officecontent.logic.OfficeWriterLogic;
import com.clip.ghost.pdfcontent.config.PdfImageProperties;
import com.clip.ghost.pdfcontent.dto.OfficeFromPdfRequest;
import com.clip.ghost.pdfcontent.dto.PdfPageImage;
import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

/**
 * {@link PdfOfficeService} の形式ごとの変換経路とファイル名正規化を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class PdfOfficeServiceTest {

	@Mock
	private GhostPdfLogic pdfLogic;

	private PdfOfficeService officeService;

	@BeforeEach
	void setup() {
		// Office書き出しは実物を使う。ここで見たいのは「形式に応じて正しい中身のファイルが返るか」で、
		// mockにすると経路の取り違えに気付けない。
		officeService = new PdfOfficeService(pdfLogic, new OfficeWriterLogic(), new PdfImageProperties());
	}

	@Test
	@DisplayName("DOCXではページ単位テキストを読み、Wordとして開けるファイルを返す")
	void generateOfficeWritesWordFromPageTexts() throws IOException {
		stubPdfLoad();
		doReturn(List.of("1ページ目", "2ページ目")).when(pdfLogic).extractPdfPageTexts(any());

		ResponseEntity<Resource> result = officeService.generateOffice(createRequest(OfficeDocumentType.DOCX));

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("設計書.docx", result.getHeaders().getContentDisposition().getFilename());
		verify(pdfLogic, never()).readPdfPageImages(any(), any(), anyInt(), anyInt(), any());
		try (XWPFDocument document = new XWPFDocument(
				new ByteArrayInputStream(result.getBody().getContentAsByteArray()))) {
			assertEquals(3, document.getParagraphs().size());
		}
	}

	@Test
	@DisplayName("XLSXではページ単位テキストを読み、1ページ1シートのブックを返す")
	void generateOfficeWritesExcelFromPageTexts() throws IOException {
		stubPdfLoad();
		doReturn(List.of("1ページ目", "2ページ目")).when(pdfLogic).extractPdfPageTexts(any());

		ResponseEntity<Resource> result = officeService.generateOffice(createRequest(OfficeDocumentType.XLSX));

		assertEquals("設計書.xlsx", result.getHeaders().getContentDisposition().getFilename());
		try (XSSFWorkbook workbook = new XSSFWorkbook(
				new ByteArrayInputStream(result.getBody().getContentAsByteArray()))) {
			assertEquals(2, workbook.getNumberOfSheets());
		}
	}

	@Test
	@DisplayName("PPTXではページ画像を読み、1ページ目の寸法をスライドサイズにする")
	void generateOfficeWritesPowerPointFromPageImages() throws IOException {
		stubPdfLoad();
		byte[] pngBytes = readSamplePng();
		doReturn(List.of(new PdfPageImage(1, pngBytes, 612f, 792f), new PdfPageImage(2, pngBytes, 612f, 792f)))
				.when(pdfLogic).readPdfPageImages(any(), eq(PdfImageFormat.PNG), anyInt(), anyInt(), any());

		ResponseEntity<Resource> result = officeService.generateOffice(createRequest(OfficeDocumentType.PPTX));

		assertEquals("設計書.pptx", result.getHeaders().getContentDisposition().getFilename());
		verify(pdfLogic, never()).extractPdfPageTexts(any());
		verify(pdfLogic, times(1)).readPdfPageImages(any(), eq(PdfImageFormat.PNG), anyInt(), anyInt(), any());
		try (XMLSlideShow slideShow = new XMLSlideShow(
				new ByteArrayInputStream(result.getBody().getContentAsByteArray()))) {
			assertEquals(2, slideShow.getSlides().size());
			assertEquals(612, slideShow.getPageSize().width);
		}
	}

	@Test
	@DisplayName("ファイル名からパス区切りを取り除き、出力形式の拡張子へそろえる")
	void generateOfficeRemovesUnsafeFileNameCharacters() {
		stubPdfLoad();
		doReturn(List.of("本文")).when(pdfLogic).extractPdfPageTexts(any());

		OfficeFromPdfRequest form = createRequest(OfficeDocumentType.DOCX);
		form.setOriginalFile(new MockMultipartFile("originalFile", "../../etc/passwd.pdf", "application/pdf",
				"pdf".getBytes(StandardCharsets.UTF_8)));

		assertEquals("passwd.docx",
				officeService.generateOffice(form).getHeaders().getContentDisposition().getFilename());
	}

	/**
	 * PDFの一時保存をstubする。
	 */
	private void stubPdfLoad() {
		doReturn(Path.of("input.pdf")).when(pdfLogic).loadPdf(any(), any());
	}

	/**
	 * 同梱のサンプルPNGを読み出す。
	 *
	 * @return PNGのbyte配列
	 * @throws IOException サンプル画像を読み込めない場合
	 */
	private byte[] readSamplePng() throws IOException {
		return java.nio.file.Files.readAllBytes(Path.of("src/main/resources/static/img/sample.png"));
	}

	/**
	 * PDFからOffice文書出力リクエストを生成する。
	 *
	 * @param format 出力形式
	 * @return 出力リクエスト
	 */
	private OfficeFromPdfRequest createRequest(OfficeDocumentType format) {
		OfficeFromPdfRequest form = new OfficeFromPdfRequest();
		form.setOriginalFile(new MockMultipartFile("originalFile", "設計書.pdf", "application/pdf",
				"pdf".getBytes(StandardCharsets.UTF_8)));
		form.setFormat(format);
		return form;
	}
}
