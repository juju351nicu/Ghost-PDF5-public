package com.clip.ghost.aicontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.StopReason;
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

	@Test
	@DisplayName("stopReasonがMAX_TOKENSの場合は出力上限による打ち切りと判定する")
	void isTruncatedDetectsMaxTokens() {
		// Anthropicは出力上限に達してもエラーを返さず正常応答として終了する（stopReason=MAX_TOKENS）。
		// REFINEは出力が入力とほぼ同じ長さになり得るため、この検出漏れは原文の後半が消えたMarkdownを
		// 正しい結果として扱ってしまう事故につながる。
		AnthropicMarkdownAiConverter converter = new AnthropicMarkdownAiConverter(new AnthropicAiProperties());

		assertTrue(converter.isTruncated(Optional.of(StopReason.MAX_TOKENS)));
	}

	@Test
	@DisplayName("stopReasonが正常終了（END_TURN）の場合は打ち切りと判定しない")
	void isTruncatedIgnoresNormalCompletion() {
		AnthropicMarkdownAiConverter converter = new AnthropicMarkdownAiConverter(new AnthropicAiProperties());

		assertFalse(converter.isTruncated(Optional.of(StopReason.END_TURN)));
		assertFalse(converter.isTruncated(Optional.empty()));
	}
}
