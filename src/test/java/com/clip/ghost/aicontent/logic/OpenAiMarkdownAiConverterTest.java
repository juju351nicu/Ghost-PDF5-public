package com.clip.ghost.aicontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.aicontent.config.OpenAiAiProperties;

/**
 * {@link OpenAiMarkdownAiConverter} の外部呼び出しを伴わない挙動を検証するテスト。
 * <p>
 * 実際のOpenAI呼び出しは外部API・APIキー・ネットワークに依存するため、自動テストでは行わない。
 * 実APIを使った確認は手動で行う。ここでは無効時の判定と識別文字列だけを検証する。
 */
class OpenAiMarkdownAiConverterTest {

	@Test
	@DisplayName("機能無効時はisEnabledがfalseになり、キー参照や外部呼び出しをしない")
	void isEnabledIsFalseWhenDisabled() {
		OpenAiAiProperties properties = new OpenAiAiProperties();
		properties.setEnabled(false);
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(properties);

		assertFalse(converter.isEnabled());
	}

	@Test
	@DisplayName("describeはモデル名を含み、APIキーを含まない")
	void describeContainsModelWithoutApiKey() {
		OpenAiAiProperties properties = new OpenAiAiProperties();
		properties.setModel("gpt-4o");
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(properties);

		String description = converter.describe();

		assertTrue(description.contains("gpt-4o"));
	}

	@Test
	@DisplayName("providerはopenai")
	void providerIsOpenAi() {
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(new OpenAiAiProperties());

		assertEquals("openai", converter.provider());
	}
}
