package com.clip.ghost.aicontent.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link AiTaskType} のコード値変換を検証するテスト。
 */
class AiTaskTypeTest {
	private static final String INVALID_KEY_MESSAGE = "taskはSUMMARIZEまたはREFINEで指定してください。";

	@Test
	@DisplayName("キー値からタスクenumを取得できる")
	void fromKeyReturnsTask() {
		assertEquals(AiTaskType.SUMMARIZE, AiTaskType.fromKey("SUMMARIZE"));
		assertEquals(AiTaskType.REFINE, AiTaskType.fromKey("REFINE"));
	}

	@Test
	@DisplayName("大文字小文字を無視してキー値からタスクenumを取得できる")
	void fromKeyIgnoresCase() {
		assertEquals(AiTaskType.SUMMARIZE, AiTaskType.fromKey("summarize"));
		assertEquals(AiTaskType.REFINE, AiTaskType.fromKey("Refine"));
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
	@DisplayName("全てのタスクがCodeEnumとしてコード値と表示名を持つ")
	void allTasksHaveKeyAndValue() {
		assertEquals(2, AiTaskType.values().length);
		Arrays.stream(AiTaskType.values()).forEach(task -> {
			assertEquals(task, AiTaskType.fromKey(task.getKey()));
			assertFalse(task.getValue().isBlank());
		});
	}

	@Test
	@DisplayName("Jacksonはタスクをキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("\"REFINE\"", objectMapper.writeValueAsString(AiTaskType.REFINE));
		assertEquals(AiTaskType.SUMMARIZE, objectMapper.readValue("\"SUMMARIZE\"", AiTaskType.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(String key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> AiTaskType.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
