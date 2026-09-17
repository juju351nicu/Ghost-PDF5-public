package com.clip.ghost.aicontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.clip.ghost.aicontent.config.AiMarkdownProperties;
import com.clip.ghost.aicontent.dto.MarkdownAiTransformRequest;
import com.clip.ghost.aicontent.dto.MarkdownAiTransformResponse;
import com.clip.ghost.aicontent.enums.AiTaskType;
import com.clip.ghost.aicontent.exception.AiInputException;
import com.clip.ghost.aicontent.exception.AiProcessingException;
import com.clip.ghost.aicontent.exception.AiUnavailableException;
import com.clip.ghost.aicontent.logic.MarkdownAiConverter;
import com.clip.ghost.aicontent.logic.MarkdownAiConverterResolver;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.response.ApiResultType;

/**
 * {@link MarkdownAiService} の有効性確認、入力文字数の上限チェック、正規化、DTO組み立てを検証するテスト。
 * <p>
 * provider選択は {@link MarkdownAiConverterResolver} をmockし、選択済み変換器の挙動だけを扱う。実APIは呼ばない。
 * {@link AiMarkdownProperties} はテストごとに上限値を変えたいため、mockではなく実インスタンスを都度生成する。
 */
@ExtendWith(MockitoExtension.class)
class MarkdownAiServiceTest {
	@Mock
	private MarkdownAiConverterResolver converterResolver;

	@Mock
	private MarkdownAiConverter converter;

	@Test
	@DisplayName("REFINEで有効時は変換結果を正規化してレスポンスへ組み立てる")
	void transformMarkdownNormalizesConverterResultForRefine() {
		MarkdownAiService service = createService(60_000);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		doReturn("整形後\r\n本文  \r\n").when(converter).transform(eq("整形前本文"), eq(AiTaskType.REFINE));

		ResponseEntity<ApiResult<MarkdownAiTransformResponse>> result = service
				.transformMarkdown(createRequest("整形前本文", AiTaskType.REFINE));

		assertNotNull(result.getBody());
		assertEquals(ApiResultType.INFO, result.getBody().getResultType());
		assertTrue(result.getBody().getMessageList().isEmpty());
		MarkdownAiTransformResponse response = result.getBody().getData();
		assertNotNull(response);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(AiTaskType.REFINE, response.getTask());
		assertEquals("整形後\n本文", response.getMarkdown());
		assertEquals("整形前本文".length(), response.getInputCharacterCount());
		assertEquals("整形後\n本文".length(), response.getOutputCharacterCount());
	}

	@Test
	@DisplayName("SUMMARIZEで有効時は変換結果を正規化してレスポンスへ組み立てる")
	void transformMarkdownNormalizesConverterResultForSummarize() {
		MarkdownAiService service = createService(60_000);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		doReturn("要約結果").when(converter).transform(eq("長い原文"), eq(AiTaskType.SUMMARIZE));

		ResponseEntity<ApiResult<MarkdownAiTransformResponse>> result = service
				.transformMarkdown(createRequest("長い原文", AiTaskType.SUMMARIZE));

		MarkdownAiTransformResponse response = result.getBody().getData();
		assertEquals(AiTaskType.SUMMARIZE, response.getTask());
		assertEquals("要約結果", response.getMarkdown());
	}

	@Test
	@DisplayName("選択された変換器が無効時は503相当の例外を投げ、変換を呼ばない")
	void transformMarkdownThrowsWhenDisabled() {
		MarkdownAiService service = createService(60_000);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(false);

		assertThrows(AiUnavailableException.class,
				() -> service.transformMarkdown(createRequest("本文", AiTaskType.REFINE)));

		verify(converter, never()).transform(any(), any());
	}

	@Test
	@DisplayName("入力文字数が上限を超えた場合は400相当の例外を投げ、変換器を一度も呼ばない")
	void transformMarkdownRejectsOverLengthInputWithoutCallingConverter() {
		MarkdownAiService service = createService(5);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);

		AiInputException exception = assertThrows(AiInputException.class,
				() -> service.transformMarkdown(createRequest("123456", AiTaskType.REFINE)));

		assertEquals(6, exception.getCharacterCount());
		assertEquals(5, exception.getMaxCharacters());
		verify(converter, never()).transform(any(), any());
	}

	@Test
	@DisplayName("変換失敗の例外はそのまま伝播し、本文やAPIキーを含まない")
	void transformMarkdownPropagatesConversionFailure() {
		MarkdownAiService service = createService(60_000);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		doThrow(new AiProcessingException("失敗")).when(converter).transform(any(), any());

		AiProcessingException exception = assertThrows(AiProcessingException.class,
				() -> service.transformMarkdown(createRequest("本文", AiTaskType.REFINE)));

		assertEquals("失敗", exception.getMessage());
	}

	@Test
	@DisplayName("AIから空応答が返った場合は変換失敗として伝播する")
	void transformMarkdownPropagatesEmptyResponseFailure() {
		MarkdownAiService service = createService(60_000);
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		doThrow(new AiProcessingException("空の応答が返りました。")).when(converter).transform(any(), any());

		assertThrows(AiProcessingException.class, () -> service.transformMarkdown(createRequest("本文", AiTaskType.REFINE)));
	}

	/**
	 * 指定した入力文字数上限を持つテスト用サービスを生成する。
	 *
	 * @param maxInputCharacters 入力文字数上限
	 * @return テスト用サービス
	 */
	private MarkdownAiService createService(int maxInputCharacters) {
		AiMarkdownProperties properties = new AiMarkdownProperties();
		properties.setMaxInputCharacters(maxInputCharacters);
		return new MarkdownAiService(converterResolver, properties);
	}

	/**
	 * テスト用のMarkdown本文AI変換リクエストを生成する。
	 *
	 * @param content 変換対象のMarkdown本文
	 * @param task    変換タスク
	 * @return Markdown本文AI変換リクエスト
	 */
	private MarkdownAiTransformRequest createRequest(String content, AiTaskType task) {
		MarkdownAiTransformRequest request = new MarkdownAiTransformRequest();
		request.setContent(content);
		request.setTask(task);
		return request;
	}
}
