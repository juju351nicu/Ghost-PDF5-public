package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * {@link ResponseUtils} の単体テスト。
 */
class ResponseUtilsTest {

	@Test
	@DisplayName("PDF表示レスポンスはinlineのPDFレスポンスとして作成される")
	void getResponseBytesBuildsInlinePdfResponse() {
		byte[] contents = "pdf".getBytes();

		ResponseEntity<byte[]> response = ResponseUtils.getResponseBytes(contents);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
		assertEquals("inline", response.getHeaders().getContentDisposition().getType());
		assertArrayEquals(contents, response.getBody());
	}

	@Test
	@DisplayName("PDFダウンロードレスポンスはattachmentのoctet-streamとして作成される")
	void downloadPdfBuildsAttachmentResponse() {
		byte[] contents = "download".getBytes();

		ResponseEntity<byte[]> response = ResponseUtils.downloadPdf("sample.pdf", contents);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
		assertEquals(contents.length, response.getHeaders().getContentLength());
		assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
		assertEquals("sample.pdf", response.getHeaders().getContentDisposition().getFilename());
		assertArrayEquals(contents, response.getBody());
	}

	@Test
	@DisplayName("ZIPダウンロードレスポンスはattachmentのapplication/zipとして作成される")
	void downloadZipBuildsAttachmentResponse() {
		byte[] contents = "zip".getBytes();

		ResponseEntity<byte[]> response = ResponseUtils.downloadZip("split.zip", contents);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.valueOf("application/zip"), response.getHeaders().getContentType());
		assertEquals(contents.length, response.getHeaders().getContentLength());
		assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
		assertEquals("split.zip", response.getHeaders().getContentDisposition().getFilename());
		assertArrayEquals(contents, response.getBody());
	}

	@Test
	@DisplayName("PDF表示レスポンスはcontents未指定の場合に明確な例外を送出する")
	void getResponseBytesThrowsWhenContentsIsNull() {
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.getResponseBytes(null));
	}

	@Test
	@DisplayName("PDFダウンロードレスポンスは入力不足の場合に明確な例外を送出する")
	void downloadPdfThrowsWhenRequiredArgumentsAreMissing() {
		byte[] contents = "download".getBytes();

		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadPdf(null, contents));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadPdf("", contents));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadPdf("   ", contents));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadPdf("sample.pdf", null));
	}

	@Test
	@DisplayName("ZIPダウンロードレスポンスは入力不足の場合に明確な例外を送出する")
	void downloadZipThrowsWhenRequiredArgumentsAreMissing() {
		byte[] contents = "zip".getBytes();

		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip(null, contents));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip("", contents));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip("   ", contents));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip("split.zip", null));
	}
}
