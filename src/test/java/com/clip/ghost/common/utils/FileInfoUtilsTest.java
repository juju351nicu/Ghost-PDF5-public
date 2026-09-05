package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link FileInfoUtils} の単体テスト。
 */
class FileInfoUtilsTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("対象文字列を含むファイル名を大文字小文字を無視して取得できる")
	void getTargetFileNames_returnsMatchedNamesIgnoringCase() throws IOException {
		Files.createFile(tempDir.resolve("sample.PDF"));
		Files.createFile(tempDir.resolve("memo.txt"));

		assertEquals(List.of("sample.PDF"), FileInfoUtils.getTargetFileNames(tempDir, "pdf"));
	}

	@Test
	@DisplayName("nullフォルダ指定は空リストまたは0件として扱う")
	void nullFolderPath_returnsEmptyOrZero() {
		assertEquals(List.of(), FileInfoUtils.getFilePaths(null, true));
		assertEquals(List.of(), FileInfoUtils.getFilePaths(null, false));
		assertEquals(0, FileInfoUtils.countFiles(null));
		assertEquals(0, FileInfoUtils.countFiles(null, ".*\\.pdf"));
		assertEquals(List.of(), FileInfoUtils.getTargetFileNames(null, "pdf"));
	}

	@Test
	@DisplayName("検索条件なしのファイル数取得は直下の通常ファイルだけ数える")
	void countFilesWithEmptyPattern_countsDirectRegularFilesOnly() throws IOException {
		Files.createFile(tempDir.resolve("root.txt"));
		Path nestedDir = Files.createDirectories(tempDir.resolve("nested"));
		Files.createFile(nestedDir.resolve("child.txt"));

		assertEquals(1, FileInfoUtils.countFiles(tempDir, null));
		assertEquals(1, FileInfoUtils.countFiles(tempDir, ""));
	}

	@Test
	@DisplayName("ファイル行数を取得できる")
	void countLines_returnsLineCount() throws IOException {
		Path filePath = tempDir.resolve("sample.txt");
		Files.writeString(filePath, "one\ntwo\nthree", StandardCharsets.UTF_8);

		assertEquals(3, FileInfoUtils.countLines(filePath));
	}

	@Test
	@DisplayName("行数取得で対象ファイルが存在しない場合は0行として扱う")
	void countLines_returnsZeroWhenFileDoesNotExist() {
		assertEquals(0, FileInfoUtils.countLines(null));
		assertEquals(0, FileInfoUtils.countLines(tempDir.resolve("missing.txt")));
	}

	@Test
	@DisplayName("ファイル内容に指定文字列が含まれるか判定できる")
	void containsText_returnsTrueWhenFileContainsSearchText() throws IOException {
		Path filePath = tempDir.resolve("sample.txt");
		Files.writeString(filePath, "hello ghost pdf", StandardCharsets.UTF_8);

		assertTrue(FileInfoUtils.containsText(filePath, "ghost"));
		assertFalse(FileInfoUtils.containsText(filePath, "Ghost"));
	}

	@Test
	@DisplayName("検索文字列または対象ファイル未指定の場合は含まれない扱いにする")
	void containsText_returnsFalseWhenSearchTextOrFilePathIsEmpty() throws IOException {
		Path filePath = tempDir.resolve("sample.txt");
		Files.writeString(filePath, "hello ghost pdf", StandardCharsets.UTF_8);

		assertFalse(FileInfoUtils.containsText(filePath, null));
		assertFalse(FileInfoUtils.containsText(filePath, ""));
		assertFalse(FileInfoUtils.containsText(null, "ghost"));
		assertFalse(FileInfoUtils.containsText(tempDir.resolve("missing.txt"), "ghost"));
	}

	@Test
	@DisplayName("最終更新時刻を文字列で取得できる")
	void getLastModifiedTimeText_returnsText() throws IOException {
		Path filePath = Files.createFile(tempDir.resolve("sample.txt"));

		assertTrue(FileInfoUtils.getLastModifiedTimeText(filePath).contains("T"));
	}
}
