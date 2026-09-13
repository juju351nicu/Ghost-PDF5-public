package com.clip.ghost.pdfcontent.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link PdfRotation} のコード値変換と回転角計算を検証するテスト。
 */
class PdfRotationTest {
	private static final String INVALID_KEY_MESSAGE = "回転角は90、180、270のいずれかで入力してください。";

	@Test
	@DisplayName("キー値から回転角enumを取得できる")
	void fromKeyReturnsRotation() {
		assertEquals(PdfRotation.CLOCKWISE_90, PdfRotation.fromKey(90));
		assertEquals(PdfRotation.UPSIDE_DOWN_180, PdfRotation.fromKey(180));
		assertEquals(PdfRotation.COUNTER_CLOCKWISE_270, PdfRotation.fromKey(270));
	}

	@Test
	@DisplayName("90の倍数でも0や360は受け付けず、説明付き例外を送出する")
	void fromKeyThrowsExceptionWhenKeyIsInvalid() {
		assertInvalidKey(0);
		assertInvalidKey(360);
		assertInvalidKey(45);
		assertInvalidKey(null);
	}

	@Test
	@DisplayName("全ての回転角がCodeEnumとしてコード値と表示名を持つ")
	void allRotationsHaveKeyAndValue() {
		assertEquals(3, PdfRotation.values().length);
		Arrays.stream(PdfRotation.values()).forEach(rotation -> {
			assertEquals(rotation, PdfRotation.fromKey(rotation.getKey()));
			assertFalse(rotation.getValue().isBlank());
		});
	}

	@ParameterizedTest
	@CsvSource({ "0, 90, 90", "90, 90, 180", "270, 90, 0", "270, 180, 90", "180, 270, 90", "-90, 90, 0" })
	@DisplayName("現在の回転角へ加算した結果を0以上360未満へ正規化する")
	void applyToNormalizesRotatedAngle(int currentRotation, int rotationKey, int expected) {
		assertEquals(expected, PdfRotation.fromKey(rotationKey).applyTo(currentRotation));
	}

	@Test
	@DisplayName("Jacksonは回転角をキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("90", objectMapper.writeValueAsString(PdfRotation.CLOCKWISE_90));
		assertEquals(PdfRotation.COUNTER_CLOCKWISE_270, objectMapper.readValue("270", PdfRotation.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(Integer key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> PdfRotation.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
