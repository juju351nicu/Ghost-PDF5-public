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
 * {@link PdfImageFormat} のコード値変換と形式ごとの属性を検証するテスト。
 */
class PdfImageFormatTest {
	private static final String INVALID_KEY_MESSAGE = "画像形式はPNG、JPG、TIFF、BMPのいずれかで指定してください。";

	@Test
	@DisplayName("キー値から画像形式enumを取得でき、小文字も受け付ける")
	void fromKeyReturnsImageFormatIgnoringCase() {
		assertEquals(PdfImageFormat.PNG, PdfImageFormat.fromKey("PNG"));
		assertEquals(PdfImageFormat.JPG, PdfImageFormat.fromKey("jpg"));
		assertEquals(PdfImageFormat.TIFF, PdfImageFormat.fromKey("Tiff"));
		assertEquals(PdfImageFormat.BMP, PdfImageFormat.fromKey("bmp"));
	}

	@Test
	@DisplayName("対象外のキー値の場合は説明付き例外を送出する")
	void fromKeyThrowsExceptionWhenKeyIsInvalid() {
		assertInvalidKey("GIF");
		assertInvalidKey("");
		assertInvalidKey(null);
	}

	@Test
	@DisplayName("全ての画像形式がCodeEnumとしてコード値と表示名を持つ")
	void allImageFormatsHaveKeyAndValue() {
		assertEquals(4, PdfImageFormat.values().length);
		Arrays.stream(PdfImageFormat.values()).forEach(format -> {
			assertEquals(format, PdfImageFormat.fromKey(format.getKey()));
			assertFalse(format.getValue().isBlank());
			assertFalse(format.getImageIoFormatName().isBlank());
			assertFalse(format.getFileExtension().isBlank());
			assertFalse(format.getMimeType().isBlank());
		});
	}

	@Test
	@DisplayName("アルファチャンネルを持てないJPGとBMPだけが不透明化を必要とする")
	void onlyFormatsWithoutAlphaRequireOpaqueImage() {
		assertTrue(PdfImageFormat.JPG.requiresOpaqueImage());
		assertTrue(PdfImageFormat.BMP.requiresOpaqueImage());
		assertFalse(PdfImageFormat.PNG.requiresOpaqueImage());
		assertFalse(PdfImageFormat.TIFF.requiresOpaqueImage());
	}

	@Test
	@DisplayName("Jacksonは画像形式をキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("\"PNG\"", objectMapper.writeValueAsString(PdfImageFormat.PNG));
		assertEquals(PdfImageFormat.TIFF, objectMapper.readValue("\"TIFF\"", PdfImageFormat.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(String key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> PdfImageFormat.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
