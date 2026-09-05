package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link FileOperationUtils} の単体テスト。
 */
class FileOperationUtilsTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("ファイル作成は親ディレクトリを作成し、null指定は明確な例外にする")
	void ensureFile_createsParentDirectoryAndRejectsNull() {
		Path filePath = tempDir.resolve("parent").resolve("sample.txt");

		assertEquals(filePath, FileOperationUtils.ensureFile(filePath));
		assertTrue(Files.isRegularFile(filePath));
		assertThrows(IllegalArgumentException.class, () -> FileOperationUtils.ensureFile(null));
	}

	@Test
	@DisplayName("ファイルコピーはコピー元未指定または存在しない場合は何もしない")
	void copyFile_doesNothingWhenSourceIsMissing() {
		Path targetPath = tempDir.resolve("target.txt");

		assertDoesNotThrow(() -> FileOperationUtils.copyFile(null, targetPath));
		assertDoesNotThrow(() -> FileOperationUtils.copyFile(tempDir.resolve("missing.txt"), targetPath));
		assertFalse(Files.exists(targetPath));
	}

	@Test
	@DisplayName("ファイルコピーはコピー先親ディレクトリを作成する")
	void copyFile_createsParentDirectory() throws IOException {
		Path sourcePath = tempDir.resolve("source.txt");
		Path targetPath = tempDir.resolve("target").resolve("copied.txt");
		Files.writeString(sourcePath, "copy", StandardCharsets.UTF_8);

		FileOperationUtils.copyFile(sourcePath, targetPath);

		assertEquals("copy", Files.readString(targetPath, StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("文字列書き込みは親ディレクトリを作成し、UTF-8で保存する")
	void writeString_writesUtf8Text() throws IOException {
		Path filePath = tempDir.resolve("nested").resolve("sample.md");

		FileOperationUtils.writeString(filePath, "# 見出し");

		assertEquals("# 見出し", Files.readString(filePath, StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("ディレクトリ作成と削除はnullまたは存在しない対象を安全に扱う")
	void createDirectoriesAndDelete_doesNothingWhenTargetIsMissing() {
		Path missingPath = tempDir.resolve("missing");

		assertDoesNotThrow(() -> FileOperationUtils.createDirectories(null));
		assertDoesNotThrow(() -> FileOperationUtils.deleteFile(null));
		assertDoesNotThrow(() -> FileOperationUtils.deleteFile(missingPath));
		assertDoesNotThrow(() -> FileOperationUtils.deleteFolder(null, true));
		assertDoesNotThrow(() -> FileOperationUtils.deleteFolder(missingPath, true));
	}

	@Test
	@DisplayName("ディレクトリを再帰的にコピーできる")
	void copyDirectory_copiesDirectoryRecursively() throws IOException {
		Path sourceDir = Files.createDirectories(tempDir.resolve("source").resolve("nested"));
		Path sourceFile = sourceDir.resolve("sample.txt");
		Files.writeString(sourceFile, "copy", StandardCharsets.UTF_8);
		Path targetDir = tempDir.resolve("target");

		FileOperationUtils.copyDirectory(tempDir.resolve("source"), targetDir);

		assertEquals("copy",
				Files.readString(targetDir.resolve("nested").resolve("sample.txt"), StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("ディレクトリを移動できる")
	void moveDirectory_movesDirectoryRecursively() throws IOException {
		Path sourceDir = Files.createDirectories(tempDir.resolve("source"));
		Files.writeString(sourceDir.resolve("sample.txt"), "move", StandardCharsets.UTF_8);
		Path targetDir = tempDir.resolve("target");

		FileOperationUtils.moveDirectory(sourceDir, targetDir);

		assertFalse(Files.exists(sourceDir));
		assertTrue(Files.exists(targetDir.resolve("sample.txt")));
	}

	@Test
	@DisplayName("非再帰ディレクトリ削除は直下ファイルだけ削除する")
	void deleteFolderWithoutRecursive_deletesOnlyDirectFiles() throws IOException {
		Path rootFile = Files.writeString(tempDir.resolve("root.txt"), "root", StandardCharsets.UTF_8);
		Path nestedDir = Files.createDirectories(tempDir.resolve("nested"));
		Path nestedFile = Files.writeString(nestedDir.resolve("child.txt"), "child", StandardCharsets.UTF_8);

		FileOperationUtils.deleteFolder(tempDir, false);

		assertFalse(Files.exists(rootFile));
		assertTrue(Files.exists(nestedDir));
		assertTrue(Files.exists(nestedFile));
	}
}
