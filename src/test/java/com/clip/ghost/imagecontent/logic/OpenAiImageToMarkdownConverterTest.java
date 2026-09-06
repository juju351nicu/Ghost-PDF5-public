package com.clip.ghost.imagecontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.imagecontent.config.OpenAiProperties;

/**
 * {@link OpenAiImageToMarkdownConverter} の外部呼び出しを伴わない挙動を検証するテスト。
 * <p>
 * 実際のOpenAI呼び出しは外部API・APIキー・ネットワークに依存するため自動テストでは行わず、手動確認とする。
 */
class OpenAiImageToMarkdownConverterTest {

	@Test
	@DisplayName("機能無効時はisEnabledがfalseになり、providerはopenai")
	void isEnabledIsFalseWhenDisabled() {
		OpenAiProperties properties = new OpenAiProperties();
		properties.setEnabled(false);
		OpenAiImageToMarkdownConverter converter = new OpenAiImageToMarkdownConverter(properties);

		assertFalse(converter.isEnabled());
		assertEquals("openai", converter.provider());
	}

	@Test
	@DisplayName("describeはモデル名を含み、APIキーを含まない")
	void describeContainsModelWithoutApiKey() {
		OpenAiProperties properties = new OpenAiProperties();
		properties.setModel("gpt-4o");
		OpenAiImageToMarkdownConverter converter = new OpenAiImageToMarkdownConverter(properties);

		assertTrue(converter.describe().contains("gpt-4o"));
	}
}
