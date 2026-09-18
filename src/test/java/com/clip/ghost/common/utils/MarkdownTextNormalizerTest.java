package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MarkdownTextNormalizer} の改行正規化を検証するテスト。
 */
class MarkdownTextNormalizerTest {

	@Test
	@DisplayName("CRLFをLFへ統一する")
	void normalizesCrlfToLf() {
		assertEquals("1行目\n2行目", MarkdownTextNormalizer.normalize("1行目\r\n2行目"));
	}

	@Test
	@DisplayName("単独のCRもLFへ統一する")
	void normalizesLoneCrToLf() {
		assertEquals("1行目\n2行目", MarkdownTextNormalizer.normalize("1行目\r2行目"));
	}

	@Test
	@DisplayName("末尾の空白文字と改行を除去する")
	void stripsTrailingWhitespace() {
		assertEquals("本文", MarkdownTextNormalizer.normalize("本文  \r\n\r\n"));
	}

	@Test
	@DisplayName("本文途中の空白は維持する")
	void keepsWhitespaceInsideText() {
		assertEquals("行末に空白2つ  \n次の行", MarkdownTextNormalizer.normalize("行末に空白2つ  \r\n次の行"));
	}

	@Test
	@DisplayName("nullはそのまま返し、呼び出し側で例外にしない")
	void returnsNullAsIs() {
		assertNull(MarkdownTextNormalizer.normalize(null));
	}

	@Test
	@DisplayName("空文字はそのまま返す")
	void returnsEmptyAsIs() {
		assertEquals("", MarkdownTextNormalizer.normalize(""));
	}
}
