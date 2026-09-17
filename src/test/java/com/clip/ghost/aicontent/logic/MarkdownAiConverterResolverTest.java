package com.clip.ghost.aicontent.logic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.aicontent.config.AiProperties;
import com.clip.ghost.aicontent.enums.AiTaskType;
import com.clip.ghost.aicontent.exception.AiUnavailableException;

/**
 * {@link MarkdownAiConverterResolver} のprovider選択を検証するテスト。
 */
class MarkdownAiConverterResolverTest {

	@Test
	@DisplayName("設定providerと一致する変換器を返す")
	void resolveReturnsMatchingProvider() {
		MarkdownAiConverter anthropic = stubConverter("anthropic");
		MarkdownAiConverter openai = stubConverter("openai");
		AiProperties properties = new AiProperties();
		properties.setProvider("openai");
		MarkdownAiConverterResolver resolver = new MarkdownAiConverterResolver(List.of(anthropic, openai), properties);

		assertSame(openai, resolver.resolve());
	}

	@Test
	@DisplayName("provider判定は大文字小文字を無視する")
	void resolveIgnoresCase() {
		MarkdownAiConverter anthropic = stubConverter("anthropic");
		AiProperties properties = new AiProperties();
		properties.setProvider("ANTHROPIC");
		MarkdownAiConverterResolver resolver = new MarkdownAiConverterResolver(List.of(anthropic), properties);

		assertSame(anthropic, resolver.resolve());
	}

	@Test
	@DisplayName("一致するproviderが無い場合は503相当の例外を投げる")
	void resolveThrowsForUnknownProvider() {
		AiProperties properties = new AiProperties();
		properties.setProvider("unknown");
		MarkdownAiConverterResolver resolver = new MarkdownAiConverterResolver(List.of(stubConverter("anthropic")),
				properties);

		assertThrows(AiUnavailableException.class, resolver::resolve);
	}

	/**
	 * 指定providerを返すだけのテスト用変換器を生成する。
	 *
	 * @param provider provider識別子
	 * @return テスト用変換器
	 */
	private MarkdownAiConverter stubConverter(String provider) {
		return new MarkdownAiConverter() {
			@Override
			public boolean isEnabled() {
				return false;
			}

			@Override
			public String describe() {
				return provider;
			}

			@Override
			public String transform(String markdown, AiTaskType taskType) {
				return "";
			}

			@Override
			public String provider() {
				return provider;
			}
		};
	}
}
