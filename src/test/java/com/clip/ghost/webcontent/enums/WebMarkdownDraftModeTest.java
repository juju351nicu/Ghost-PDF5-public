package com.clip.ghost.webcontent.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link WebMarkdownDraftMode} のコード値変換と出力内容の判定を検証するテスト。
 */
class WebMarkdownDraftModeTest {
	private static final String INVALID_KEY_MESSAGE = "出力モードはARTICLE、STRUCTURE、BOTHのいずれかで指定してください。";

	@Test
	@DisplayName("キー値から出力モードenumを取得できる")
	void fromKeyReturnsMode() {
		assertEquals(WebMarkdownDraftMode.ARTICLE, WebMarkdownDraftMode.fromKey("ARTICLE"));
		assertEquals(WebMarkdownDraftMode.STRUCTURE, WebMarkdownDraftMode.fromKey("STRUCTURE"));
		assertEquals(WebMarkdownDraftMode.BOTH, WebMarkdownDraftMode.fromKey("BOTH"));
	}

	@Test
	@DisplayName("大文字小文字を無視してキー値から出力モードenumを取得できる")
	void fromKeyIgnoresCase() {
		// 既存のPdfMarkdownDraftModeがmode=visionを受け付けるため、同じAPI群で扱いを変えない。
		assertEquals(WebMarkdownDraftMode.STRUCTURE, WebMarkdownDraftMode.fromKey("structure"));
		assertEquals(WebMarkdownDraftMode.BOTH, WebMarkdownDraftMode.fromKey("Both"));
	}

	@Test
	@DisplayName("キー値が不正な場合は説明付き例外を送出する")
	void fromKeyThrowsExceptionWhenKeyIsInvalid() {
		assertInvalidKey("FOO");
		assertInvalidKey(StringUtils.EMPTY);
		assertInvalidKey(null);
	}

	@Test
	@DisplayName("全ての出力モードがCodeEnumとしてコード値と表示名を持つ")
	void allModesHaveKeyAndValue() {
		assertEquals(3, WebMarkdownDraftMode.values().length);
		Arrays.stream(WebMarkdownDraftMode.values()).forEach(mode -> {
			assertEquals(mode, WebMarkdownDraftMode.fromKey(mode.getKey()));
			assertTrue(StringUtils.isNotBlank(mode.getValue()));
		});
	}

	@Test
	@DisplayName("モードごとに本文と構造レポートの出力有無が決まる")
	void modesDeclareWhatTheyOutput() {
		assertTrue(WebMarkdownDraftMode.ARTICLE.outputsArticle());
		assertFalse(WebMarkdownDraftMode.ARTICLE.outputsStructure());
		assertFalse(WebMarkdownDraftMode.STRUCTURE.outputsArticle());
		assertTrue(WebMarkdownDraftMode.STRUCTURE.outputsStructure());
		assertTrue(WebMarkdownDraftMode.BOTH.outputsArticle());
		assertTrue(WebMarkdownDraftMode.BOTH.outputsStructure());
	}

	/**
	 * 不正なキー値で説明付き例外になることを確認する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(String key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> WebMarkdownDraftMode.fromKey(key));

		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
