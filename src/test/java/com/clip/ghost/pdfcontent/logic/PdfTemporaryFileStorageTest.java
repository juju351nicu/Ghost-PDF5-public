package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clip.ghost.pdfcontent.exception.PdfProcessingException;
import org.springframework.mock.web.MockMultipartFile;

/**
 * {@link PdfTemporaryFileStorage} の単体テスト。
 */
class PdfTemporaryFileStorageTest {

	@TempDir
	Path tempDirectory;

	@Test
	void saveUploadedPdfWritesFileToConfiguredDirectory() throws IOException {
		PdfTemporaryFileStorage storage = new PdfTemporaryFileStorage(tempDirectory.toString());
		byte[] content = "temporary pdf bytes".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile pdfFile = new MockMultipartFile("originalFile", "sample.PDF", "application/pdf", content);

		Path savedPath = storage.saveUploadedPdf(pdfFile);

		assertEquals(tempDirectory, savedPath.getParent());
		assertTrue(savedPath.getFileName().toString().endsWith(".PDF"));
		assertArrayEquals(content, Files.readAllBytes(savedPath));
	}

	@Test
	void openForResponseStreamsFileAndDeletesItOnClose() throws IOException {
		PdfTemporaryFileStorage storage = new PdfTemporaryFileStorage(tempDirectory.toString());
		Path inputPath = tempDirectory.resolve("input.pdf");
		byte[] content = "temporary pdf bytes".getBytes(StandardCharsets.UTF_8);
		Files.write(inputPath, content);

		Resource resource = storage.openForResponse(inputPath);

		// サイズ取得で内容を読み込まないこと（ストリームを消費するとレスポンスが壊れる）。
		assertEquals(content.length, resource.contentLength());
		assertTrue(Files.exists(inputPath));
		try (InputStream inputStream = resource.getInputStream()) {
			assertArrayEquals(content, inputStream.readAllBytes());
			// 送信中に相当する時点では削除されていない。
			assertTrue(Files.exists(inputPath));
		}

		assertFalse(Files.exists(inputPath));
	}

	@Test
	void openForResponseThrowsWhenTemporaryFileDoesNotExist() {
		PdfTemporaryFileStorage storage = new PdfTemporaryFileStorage(tempDirectory.toString());
		Path missingPath = tempDirectory.resolve("missing.pdf");

		assertThrows(PdfProcessingException.class, () -> storage.openForResponse(missingPath));
	}
}
