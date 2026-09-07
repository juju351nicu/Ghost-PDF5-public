package com.clip.ghost.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ApiResult} のfactoryと不変性を検証するテスト。
 */
class ApiResultTest {

	@Test
	@DisplayName("of(data)は結果種別INFOとメッセージ空になる")
	void ofReturnsInfoWithEmptyMessageList() {
		ApiResult<String> result = ApiResult.of("data");

		assertEquals("data", result.getData());
		assertEquals(ApiResultType.INFO, result.getResultType());
		assertTrue(result.getMessageList().isEmpty());
	}

	@Test
	@DisplayName("of(data, messageList)はメッセージを保持したINFOになる")
	void ofWithMessageListKeepsMessagesAsInfo() {
		ApiResult<String> result = ApiResult.of("data", List.of(new ApiMessage("code", "message")));

		assertEquals(ApiResultType.INFO, result.getResultType());
		assertEquals(1, result.getMessageList().size());
		assertEquals("code", result.getMessageList().get(0).code());
		assertEquals("message", result.getMessageList().get(0).message());
	}

	@Test
	@DisplayName("warning(data, messageList)は結果種別WARNINGになる")
	void warningReturnsWarningWithMessages() {
		ApiResult<String> result = ApiResult.warning("data", List.of(new ApiMessage("code", "message")));

		assertEquals("data", result.getData());
		assertEquals(ApiResultType.WARNING, result.getResultType());
		assertEquals(1, result.getMessageList().size());
	}

	@Test
	@DisplayName("メッセージ空のWARNINGは生成できない")
	void warningRejectsEmptyMessageList() {
		// 何を注意すべきか伝えられないWARNINGは利用者の役に立たないため、生成時点で弾く。
		List<ApiMessage> emptyMessages = List.of();

		assertThrows(IllegalArgumentException.class, () -> ApiResult.warning("data", emptyMessages));
	}

	@Test
	@DisplayName("messageListは防御的コピーされ、元のListの変更に影響されない")
	void messageListIsDefensivelyCopied() {
		List<ApiMessage> messages = new ArrayList<>();
		messages.add(new ApiMessage("code", "message"));
		ApiResult<String> result = ApiResult.of("data", messages);

		messages.add(new ApiMessage("added", "added message"));
		messages.clear();

		assertEquals(1, result.getMessageList().size());
		assertEquals("code", result.getMessageList().get(0).code());
	}

	@Test
	@DisplayName("保持したmessageListは変更できない")
	void messageListIsUnmodifiable() {
		ApiResult<String> result = ApiResult.of("data", List.of(new ApiMessage("code", "message")));
		List<ApiMessage> messageList = result.getMessageList();
		ApiMessage additionalMessage = new ApiMessage("added", "added message");

		assertThrows(UnsupportedOperationException.class, () -> messageList.add(additionalMessage));
	}

	@Test
	@DisplayName("empty()はdataがnullのINFOになる")
	void emptyReturnsNullDataWithInfo() {
		ApiResult<Void> result = ApiResult.empty();

		assertNull(result.getData());
		assertEquals(ApiResultType.INFO, result.getResultType());
		assertTrue(result.getMessageList().isEmpty());
	}

	@Test
	@DisplayName("public setterを持たない")
	void hasNoPublicSetter() {
		// factory以外の生成・変更経路が増えると、resultTypeとmessageListの整合が崩れるため固定する。
		boolean hasSetter = Arrays.stream(ApiResult.class.getMethods()).map(Method::getName)
				.anyMatch(methodName -> methodName.startsWith("set"));

		assertFalse(hasSetter);
	}
}
