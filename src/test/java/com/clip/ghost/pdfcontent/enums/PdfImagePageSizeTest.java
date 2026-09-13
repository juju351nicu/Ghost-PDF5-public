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
 * {@link PdfImagePageSize} のコード値変換とページサイズ判定を検証するテスト。
 */
class PdfImagePageSizeTest {
	private static final String INVALID_KEY_MESSAGE = "ページサイズはA4またはFITで指定してください。";

	@Test
	@DisplayName("キー値からページサイズenumを取得でき、小文字も受け付ける")
	void fromKeyReturnsPageSizeIgnoringCase() {
		assertEquals(PdfImagePageSize.A4, PdfImagePageSize.fromKey("A4"));
		assertEquals(PdfImagePageSize.FIT, PdfImagePageSize.fromKey("fit"));
	}

	@Test
	@DisplayName("対象外のキー値の場合は説明付き例外を送出する")
	void fromKeyThrowsExceptionWhenKeyIsInvalid() {
		assertInvalidKey("B5");
		assertInvalidKey("");
		assertInvalidKey(null);
	}

	@Test
	@DisplayName("全てのページサイズがCodeEnumとしてコード値と表示名を持つ")
	void allPageSizesHaveKeyAndValue() {
		assertEquals(2, PdfImagePageSize.values().length);
		Arrays.stream(PdfImagePageSize.values()).forEach(pageSize -> {
			assertEquals(pageSize, PdfImagePageSize.fromKey(pageSize.getKey()));
			assertFalse(pageSize.getValue().isBlank());
		});
	}

	@Test
	@DisplayName("FITだけがページサイズを画像に合わせる")
	void onlyFitFitsPageToImage() {
		assertTrue(PdfImagePageSize.FIT.fitsPageToImage());
		assertFalse(PdfImagePageSize.A4.fitsPageToImage());
	}

	@Test
	@DisplayName("Jacksonはページサイズをキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("\"A4\"", objectMapper.writeValueAsString(PdfImagePageSize.A4));
		assertEquals(PdfImagePageSize.FIT, objectMapper.readValue("\"FIT\"", PdfImagePageSize.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(String key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> PdfImagePageSize.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
