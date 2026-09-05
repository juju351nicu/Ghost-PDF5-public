package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PathUtils} の単体テスト。
 */
class PathUtilsTest {

	@Test
	@DisplayName("ファイルパスから親ディレクトリを取得できる")
	void extractDirectoryPath_returnsParentDirectory() {
		assertEquals("src/main/resources", PathUtils.extractDirectoryPath("src/main/resources/sample.pdf"));
	}

	@Test
	@DisplayName("拡張子取得はnullと拡張子なしを空文字として扱う")
	void getExtension_returnsEmptyWhenFileNameIsNullOrHasNoExtension() {
		assertEquals("", PathUtils.getExtension(null));
		assertEquals("", PathUtils.getExtension("sample"));
	}

	@Test
	@DisplayName("拡張子削除はcommons-ioの結果を維持する")
	void removeExtension_returnsFileNameWithoutExtension() {
		assertNull(PathUtils.removeExtension(null));
		assertEquals("sample", PathUtils.removeExtension("sample.pdf"));
	}

	@Test
	@DisplayName("区切り文字がないファイル名取得は既存互換のためnullを返す")
	void extractFileName_returnsNullWhenSeparatorDoesNotExist() {
		assertNull(PathUtils.extractFileName("sample.pdf"));
	}

	@Test
	@DisplayName("フォルダパス未指定のパス連結はファイル名だけを返す")
	void mergePathAndFileName_returnsFileNameWhenFolderPathIsEmpty() {
		assertEquals("sample.pdf", PathUtils.mergePathAndFileName(null, "sample.pdf"));
		assertEquals("sample.pdf", PathUtils.mergePathAndFileName("", "sample.pdf"));
	}

	@Test
	@DisplayName("ファイル名未指定のパス連結はフォルダパスだけを返す")
	void mergePathAndFileName_returnsFolderPathWhenFileNameIsEmpty() {
		assertEquals("src/main/resources", PathUtils.mergePathAndFileName("src/main/resources", null));
		assertEquals("src/main/resources", PathUtils.mergePathAndFileName("src/main/resources", ""));
	}

	@Test
	@DisplayName("両方未指定のパス連結は空文字を返す")
	void mergePathAndFileName_returnsEmptyWhenBothArgumentsAreEmpty() {
		assertEquals("", PathUtils.mergePathAndFileName(null, null));
		assertEquals("", PathUtils.mergePathAndFileName("", ""));
	}

	@Test
	@DisplayName("PDF拡張子を大文字小文字を無視して判定できる")
	void isPdfFileName_ignoresCase() {
		assertTrue(PathUtils.isPdfFileName("SAMPLE.PDF"));
	}

	@Test
	@DisplayName("PDFファイル名判定はnullをfalseとして扱う")
	void isPdfFileName_returnsFalseWhenFileNameIsNull() {
		assertFalse(PathUtils.isPdfFileName(null));
	}

	@Test
	@DisplayName("Markdown拡張子を大文字小文字を無視して判定できる")
	void isMarkdownFileName_ignoresCase() {
		assertTrue(PathUtils.isMarkdownFileName("SAMPLE.MD"));
	}

	@Test
	@DisplayName("Markdownファイル名判定はnullをfalseとして扱う")
	void isMarkdownFileName_returnsFalseWhenFileNameIsNull() {
		assertFalse(PathUtils.isMarkdownFileName(null));
	}
}
