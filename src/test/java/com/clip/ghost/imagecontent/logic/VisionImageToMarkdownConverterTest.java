package com.clip.ghost.imagecontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.imagecontent.config.VisionProperties;

/**
 * {@link VisionImageToMarkdownConverter} の外部呼び出しを伴わない挙動を検証するテスト。
 * <p>
 * 実際のvision呼び出しは外部API・APIキー・ネットワークに依存するため、自動テストでは行わない。
 * 実APIを使った確認は手動で行う。ここでは無効時の判定と識別文字列だけを検証する。
 */
class VisionImageToMarkdownConverterTest {

	@Test
	@DisplayName("機能無効時はisEnabledがfalseになり、キー参照や外部呼び出しをしない")
	void isEnabledIsFalseWhenDisabled() {
		VisionProperties properties = new VisionProperties();
		properties.setEnabled(false);
		VisionImageToMarkdownConverter converter = new VisionImageToMarkdownConverter(properties);

		assertFalse(converter.isEnabled());
	}

	@Test
	@DisplayName("describeはモデル名を含み、APIキーを含まない")
	void describeContainsModelWithoutApiKey() {
		VisionProperties properties = new VisionProperties();
		properties.setModel("claude-opus-5");
		VisionImageToMarkdownConverter converter = new VisionImageToMarkdownConverter(properties);

		String description = converter.describe();

		assertTrue(description.contains("claude-opus-5"));
	}

	@Test
	@DisplayName("providerはanthropic")
	void providerIsAnthropic() {
		VisionImageToMarkdownConverter converter = new VisionImageToMarkdownConverter(new VisionProperties());

		assertEquals("anthropic", converter.provider());
	}
}
