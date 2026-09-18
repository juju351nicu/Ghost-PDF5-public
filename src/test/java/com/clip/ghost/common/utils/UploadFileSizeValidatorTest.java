package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link UploadFileSizeValidator} のサイズ上限判定を検証するテスト。
 */
class UploadFileSizeValidatorTest {
	private static final long MAX_SIZE = 10L;

	@Test
	@DisplayName("上限未満のファイルは例外にしない")
	void acceptsFileUnderLimit() {
		MultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[9]);

		assertDoesNotThrow(() -> UploadFileSizeValidator.validate(file, MAX_SIZE));
	}

	@Test
	@DisplayName("上限ちょうどのファイルは既存APIと同じく拒否する")
	void rejectsFileAtLimit() {
		MultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[10]);

		assertThrows(MultipartException.class, () -> UploadFileSizeValidator.validate(file, MAX_SIZE));
	}

	@Test
	@DisplayName("ファイル未指定は検証しない")
	void ignoresNullFile() {
		assertDoesNotThrow(() -> UploadFileSizeValidator.validate(null, MAX_SIZE));
	}

	@Test
	@DisplayName("複数ファイルは1件でも上限以上なら拒否する")
	void rejectsWhenAnyFileExceedsLimit() {
		List<MultipartFile> files = List.of(new MockMultipartFile("file", new byte[1]),
				new MockMultipartFile("file", new byte[10]));

		assertThrows(MultipartException.class, () -> UploadFileSizeValidator.validateAll(files, MAX_SIZE));
	}

	@Test
	@DisplayName("複数ファイルがすべて上限未満なら例外にしない")
	void acceptsWhenAllFilesUnderLimit() {
		List<MultipartFile> files = List.of(new MockMultipartFile("file", new byte[1]),
				new MockMultipartFile("file", new byte[9]));

		assertDoesNotThrow(() -> UploadFileSizeValidator.validateAll(files, MAX_SIZE));
	}

	@Test
	@DisplayName("リスト未指定と要素nullは検証しない")
	void ignoresNullCollectionAndNullElement() {
		assertDoesNotThrow(() -> UploadFileSizeValidator.validateAll(null, MAX_SIZE));
		assertDoesNotThrow(() -> UploadFileSizeValidator.validateAll(Arrays.asList((MultipartFile) null), MAX_SIZE));
	}
}
