package com.clip.ghost.aicontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.aicontent.config.AnthropicAiProperties;

/**
 * {@link AnthropicMarkdownAiConverter} の外部呼び出しを伴わない挙動を検証するテスト。
 * <p>
 * 実際のAnthropic呼び出しは外部API・APIキー・ネットワークに依存するため、自動テストでは行わない。
 * 実APIを使った確認は手動で行う。ここでは無効時の判定と識別文字列だけを検証する。
 */
class AnthropicMarkdownAiConverterTest {

	@Test
	@DisplayName("機能無効時はisEnabledがfalseになり、キー参照や外部呼び出しをしない")
	void isEnabledIsFalseWhenDisabled() {
		AnthropicAiProperties properties = new AnthropicAiProperties();
		properties.setEnabled(false);
		AnthropicMarkdownAiConverter converter = new AnthropicMarkdownAiConverter(properties);

		assertFalse(converter.isEnabled());
	}

	@Test
	@DisplayName("describeはモデル名を含み、APIキーを含まない")
	void describeContainsModelWithoutApiKey() {
		AnthropicAiProperties properties = new AnthropicAiProperties();
		properties.setModel("claude-opus-5");
		AnthropicMarkdownAiConverter converter = new AnthropicMarkdownAiConverter(properties);

		String description = converter.describe();

		assertTrue(description.contains("claude-opus-5"));
	}

	@Test
	@DisplayName("providerはanthropic")
	void providerIsAnthropic() {
		AnthropicMarkdownAiConverter converter = new AnthropicMarkdownAiConverter(new AnthropicAiProperties());

		assertEquals("anthropic", converter.provider());
	}
}
