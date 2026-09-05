package com.clip.ghost.pdfcontent.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.pdfcontent.constant.PdfConstants;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link PdfInsertOption} のコード値変換を検証するテスト。
 */
class PdfInsertOptionTest {
	private static final String INVALID_KEY_MESSAGE = "PDF差し込み方法は1、2、3のいずれかで入力してください。";

	@Test
	@DisplayName("キー値から差し込み方法enumを取得できる")
	void fromKeyReturnsInsertOption() {
		assertEquals(PdfInsertOption.INSERT, PdfInsertOption.fromKey(PdfConstants.OPTION_INSERT));
		assertEquals(PdfInsertOption.REPLACE, PdfInsertOption.fromKey(PdfConstants.OPTION_REPLACE));
		assertEquals(PdfInsertOption.LAST_INSERT, PdfInsertOption.fromKey(PdfConstants.OPTION_LAST_INSERT));
	}

	@Test
	@DisplayName("キー値が不正な場合は説明付き例外を送出する")
	void fromKeyThrowsExceptionWhenKeyIsInvalid() {
		assertInvalidKey(0);
		assertInvalidKey(null);
	}

	@Test
	@DisplayName("全ての差し込み方法がCodeEnumとしてコード値と表示名を持つ")
	void allInsertOptionsHaveKeyAndValue() {
		assertEquals(3, PdfInsertOption.values().length);
		Arrays.stream(PdfInsertOption.values()).forEach(option -> {
			assertEquals(option, PdfInsertOption.fromKey(option.getKey()));
			assertFalse(option.getValue().isBlank());
		});
	}

	@Test
	@DisplayName("Jacksonは差し込み方法をキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("1", objectMapper.writeValueAsString(PdfInsertOption.INSERT));
		assertEquals(PdfInsertOption.REPLACE, objectMapper.readValue("2", PdfInsertOption.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(Integer key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> PdfInsertOption.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
