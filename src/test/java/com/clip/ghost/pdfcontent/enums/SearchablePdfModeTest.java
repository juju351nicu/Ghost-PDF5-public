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
 * {@link SearchablePdfMode} のコード値変換とOCR対象判定を検証するテスト。
 */
class SearchablePdfModeTest {
	private static final String INVALID_KEY_MESSAGE = "modeはAUTOまたはFORCE_OCRで指定してください。";

	@Test
	@DisplayName("キー値から変換モードenumを取得できる")
	void fromKeyReturnsMode() {
		assertEquals(SearchablePdfMode.AUTO, SearchablePdfMode.fromKey("AUTO"));
		assertEquals(SearchablePdfMode.FORCE_OCR, SearchablePdfMode.fromKey("FORCE_OCR"));
	}

	@Test
	@DisplayName("大文字小文字を無視してキー値から変換モードenumを取得できる")
	void fromKeyIgnoresCase() {
		assertEquals(SearchablePdfMode.AUTO, SearchablePdfMode.fromKey("auto"));
		assertEquals(SearchablePdfMode.FORCE_OCR, SearchablePdfMode.fromKey("force_ocr"));
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
		assertEquals(2, SearchablePdfMode.values().length);
		Arrays.stream(SearchablePdfMode.values()).forEach(mode -> {
			assertEquals(mode, SearchablePdfMode.fromKey(mode.getKey()));
			assertFalse(mode.getValue().isBlank());
		});
	}

	@Test
	@DisplayName("FORCE_OCRだけが全ページを対象にする")
	void onlyForceOcrConvertsEveryPage() {
		assertFalse(SearchablePdfMode.AUTO.convertsEveryPage());
		assertTrue(SearchablePdfMode.FORCE_OCR.convertsEveryPage());
	}

	@Test
	@DisplayName("変換対象の説明にモード名と対象の説明を含む")
	void describeConversionTargetContainsKeyAndValue() {
		String description = SearchablePdfMode.FORCE_OCR.describeConversionTarget();

		assertTrue(description.startsWith("FORCE_OCR"));
		assertTrue(description.contains(SearchablePdfMode.FORCE_OCR.getValue()));
	}

	@Test
	@DisplayName("Jacksonは変換モードをキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("\"FORCE_OCR\"", objectMapper.writeValueAsString(SearchablePdfMode.FORCE_OCR));
		assertEquals(SearchablePdfMode.AUTO, objectMapper.readValue("\"AUTO\"", SearchablePdfMode.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(String key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> SearchablePdfMode.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
