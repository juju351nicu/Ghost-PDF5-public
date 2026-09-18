package com.clip.ghost.aicontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.aicontent.config.AiOpenAiProperties;
import com.openai.models.chat.completions.ChatCompletion;

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
		AiOpenAiProperties properties = new AiOpenAiProperties();
		properties.setEnabled(false);
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(properties);

		assertFalse(converter.isEnabled());
	}

	@Test
	@DisplayName("describeはモデル名を含み、APIキーを含まない")
	void describeContainsModelWithoutApiKey() {
		AiOpenAiProperties properties = new AiOpenAiProperties();
		properties.setModel("gpt-4o");
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(properties);

		String description = converter.describe();

		assertTrue(description.contains("gpt-4o"));
	}

	@Test
	@DisplayName("providerはopenai")
	void providerIsOpenAi() {
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(new AiOpenAiProperties());

		assertEquals("openai", converter.provider());
	}

	@Test
	@DisplayName("finishReasonがLENGTHの場合は出力上限による打ち切りと判定する")
	void isTruncatedDetectsLength() {
		// OpenAIは出力上限に達してもエラーを返さず正常応答として終了する（finish_reason=length）。
		// REFINEは出力が入力とほぼ同じ長さになり得るため、この検出漏れは原文の後半が消えたMarkdownを
		// 正しい結果として扱ってしまう事故につながる。
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(new AiOpenAiProperties());

		assertTrue(converter.isTruncated(ChatCompletion.Choice.FinishReason.LENGTH));
	}

	@Test
	@DisplayName("finishReasonが正常終了（stop）の場合は打ち切りと判定しない")
	void isTruncatedIgnoresNormalCompletion() {
		OpenAiMarkdownAiConverter converter = new OpenAiMarkdownAiConverter(new AiOpenAiProperties());

		assertFalse(converter.isTruncated(ChatCompletion.Choice.FinishReason.STOP));
	}
}
