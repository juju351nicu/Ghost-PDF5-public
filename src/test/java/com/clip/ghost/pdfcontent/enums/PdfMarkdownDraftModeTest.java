package com.clip.ghost.pdfcontent.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link PdfMarkdownDraftMode} のコード値変換と変換対象の判定を検証するテスト。
 */
class PdfMarkdownDraftModeTest {
	private static final String INVALID_KEY_MESSAGE = "変換モードはAUTOまたはVISIONで指定してください。";

	@Test
	@DisplayName("キー値から変換モードenumを取得できる")
	void fromKeyReturnsMode() {
		assertEquals(PdfMarkdownDraftMode.AUTO, PdfMarkdownDraftMode.fromKey("AUTO"));
		assertEquals(PdfMarkdownDraftMode.VISION, PdfMarkdownDraftMode.fromKey("VISION"));
	}

	@Test
	@DisplayName("大文字小文字を無視してキー値から変換モードenumを取得できる")
	void fromKeyIgnoresCase() {
		// enum化前のStrings.CI.equalsによる判定をそのまま維持する。mode=autoを送っていた利用者を壊さない。
		assertEquals(PdfMarkdownDraftMode.AUTO, PdfMarkdownDraftMode.fromKey("auto"));
		assertEquals(PdfMarkdownDraftMode.VISION, PdfMarkdownDraftMode.fromKey("vision"));
		assertEquals(PdfMarkdownDraftMode.VISION, PdfMarkdownDraftMode.fromKey("Vision"));
	}

	@Test
	@DisplayName("キー値が不正な場合は説明付き例外を送出する")
	void fromKeyThrowsExceptionWhenKeyIsInvalid() {
		assertInvalidKey("FOO");
		assertInvalidKey("");
		assertInvalidKey(" ");
		assertInvalidKey(null);
	}

	@Test
	@DisplayName("全ての変換モードがCodeEnumとしてコード値と表示名を持つ")
	void allModesHaveKeyAndValue() {
		assertEquals(2, PdfMarkdownDraftMode.values().length);
		Arrays.stream(PdfMarkdownDraftMode.values()).forEach(mode -> {
			assertEquals(mode, PdfMarkdownDraftMode.fromKey(mode.getKey()));
			assertFalse(mode.getValue().isBlank());
		});
	}

	@Test
	@DisplayName("VISIONだけが全ページを変換対象にする")
	void onlyVisionConvertsEveryPage() {
		assertFalse(PdfMarkdownDraftMode.AUTO.convertsEveryPage());
		assertTrue(PdfMarkdownDraftMode.VISION.convertsEveryPage());
	}

	@Test
	@DisplayName("変換対象の説明にモード名と対象の説明を含む")
	void describeConversionTargetContainsKeyAndValue() {
		String description = PdfMarkdownDraftMode.VISION.describeConversionTarget();

		// ページ上限超過のエラーメッセージへ添える文言のため、どのモードの話かが分かる形を固定する。
		assertTrue(description.startsWith("VISION"));
		assertTrue(description.contains(PdfMarkdownDraftMode.VISION.getValue()));
	}

	@Test
	@DisplayName("Jacksonは変換モードをキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("\"VISION\"", objectMapper.writeValueAsString(PdfMarkdownDraftMode.VISION));
		assertEquals(PdfMarkdownDraftMode.AUTO, objectMapper.readValue("\"AUTO\"", PdfMarkdownDraftMode.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(String key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> PdfMarkdownDraftMode.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
