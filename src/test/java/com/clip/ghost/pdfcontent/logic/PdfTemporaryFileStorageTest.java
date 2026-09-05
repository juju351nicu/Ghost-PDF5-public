package com.clip.ghost.pdfcontent.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
	void readBytesKeepsFileUntilExplicitlyDeleted() throws IOException {
		PdfTemporaryFileStorage storage = new PdfTemporaryFileStorage(tempDirectory.toString());
		Path inputPath = tempDirectory.resolve("input.pdf");
		byte[] content = "temporary pdf bytes".getBytes(StandardCharsets.UTF_8);
		Files.write(inputPath, content);

		assertArrayEquals(content, storage.readBytes(inputPath));
		assertTrue(Files.exists(inputPath));

		storage.delete(inputPath);

		assertFalse(Files.exists(inputPath));
	}
}
