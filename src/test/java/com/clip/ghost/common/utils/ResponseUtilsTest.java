package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * {@link ResponseUtils} の単体テスト。
 */
class ResponseUtilsTest {

	@TempDir
	Path tempDirectory;

	@Test
	@DisplayName("PDF表示レスポンスはinlineのPDFレスポンスとして作成される")
	void inlinePdfBuildsInlinePdfResponse() throws IOException {
		byte[] contents = "pdf".getBytes(StandardCharsets.UTF_8);
		Resource resource = new ByteArrayResource(contents);

		ResponseEntity<Resource> response = ResponseUtils.inlinePdf(resource);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
		assertEquals("inline", response.getHeaders().getContentDisposition().getType());
		// byte配列を返していた頃と同じヘッダーを維持する。Content-Lengthが消えると進捗表示が出なくなる。
		assertEquals(contents.length, response.getHeaders().getContentLength());
		assertArrayEquals(contents, readBody(response));
	}

	@Test
	@DisplayName("ZIPダウンロードレスポンスはattachmentのapplication/zipとして作成される")
	void downloadZipBuildsAttachmentResponse() throws IOException {
		byte[] contents = "zip".getBytes(StandardCharsets.UTF_8);
		Resource resource = new ByteArrayResource(contents);

		ResponseEntity<Resource> response = ResponseUtils.downloadZip("split.zip", resource);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.valueOf("application/zip"), response.getHeaders().getContentType());
		assertEquals(contents.length, response.getHeaders().getContentLength());
		assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
		assertEquals("split.zip", response.getHeaders().getContentDisposition().getFilename());
		assertArrayEquals(contents, readBody(response));
	}

	@Test
	@DisplayName("ファイルリソースでもContent-Lengthがファイルサイズになる")
	void contentLengthComesFromFileSize() throws IOException {
		Path filePath = tempDirectory.resolve("sample.pdf");
		byte[] contents = "pdf file contents".getBytes(StandardCharsets.UTF_8);
		Files.write(filePath, contents);

		ResponseEntity<Resource> response = ResponseUtils.inlinePdf(new FileSystemResource(filePath));

		assertEquals(contents.length, response.getHeaders().getContentLength());
		assertArrayEquals(contents, readBody(response));
	}

	@Test
	@DisplayName("PDF表示レスポンスはcontents未指定の場合に明確な例外を送出する")
	void inlinePdfThrowsWhenContentsIsNull() {
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.inlinePdf(null));
	}

	@Test
	@DisplayName("PDF表示レスポンスはサイズを取得できない場合に明確な例外を送出する")
	void inlinePdfThrowsWhenContentLengthIsNotResolvable() {
		// 存在しないファイルはサイズを取得できない。Content-Lengthを付けられないまま返さない。
		Resource missingResource = new FileSystemResource(tempDirectory.resolve("missing.pdf"));

		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.inlinePdf(missingResource));
	}

	@Test
	@DisplayName("ZIPダウンロードレスポンスは入力不足の場合に明確な例外を送出する")
	void downloadZipThrowsWhenRequiredArgumentsAreMissing() {
		Resource resource = new ByteArrayResource("zip".getBytes(StandardCharsets.UTF_8));

		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip(null, resource));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip("", resource));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip("   ", resource));
		assertThrows(IllegalArgumentException.class, () -> ResponseUtils.downloadZip("split.zip", null));
	}

	/**
	 * レスポンス本文のリソースをバイト列として読み取る。
	 *
	 * @param response 検証対象レスポンス
	 * @return レスポンス本文のバイト列
	 * @throws IOException リソースを読み取れない場合
	 */
	private byte[] readBody(ResponseEntity<Resource> response) throws IOException {
		Resource body = response.getBody();
		if (body == null) {
			return new byte[0];
		}
		try (InputStream inputStream = body.getInputStream()) {
			return inputStream.readAllBytes();
		}
	}
}
