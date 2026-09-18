package com.clip.ghost.imagecontent.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ImageMarkdownPromptBuilder} の指示文を、実AI呼び出しなしで検証するテスト。
 * <p>
 * provider実装（Anthropic / OpenAI）が同じ文言を送ることは、それぞれの変換器テストではなくここで固定する。
 */
class ImageMarkdownPromptBuilderTest {

	@Test
	@DisplayName("systemプロンプトは表とコードの扱い、読み取れない箇所の明示を指示する")
	void systemPromptDeclaresTranscriptionRules() {
		String systemPrompt = ImageMarkdownPromptBuilder.buildSystemPrompt();

		assertTrue(systemPrompt.contains("表はMarkdownの表"));
		assertTrue(systemPrompt.contains("読み取れない箇所"));
	}

	@Test
	@DisplayName("systemプロンプトは出力全体をコードフェンスで囲まないよう指示する")
	void systemPromptForbidsWrappingWholeOutputInFence() {
		// 出力全体を ```markdown で包まれるとプレビューで表が表として描画されない。
		// MarkdownFenceUnwrapperの後始末に頼り切らず、プロンプト側でも抑止する。
		String systemPrompt = ImageMarkdownPromptBuilder.buildSystemPrompt();

		assertTrue(systemPrompt.contains("出力全体をコードフェンスで囲まないでください"));
	}

	@Test
	@DisplayName("userプロンプトは画像の文字起こしをMarkdownで求める")
	void userPromptAsksForMarkdownTranscription() {
		String userPrompt = ImageMarkdownPromptBuilder.buildUserPrompt();

		assertTrue(userPrompt.contains("文字起こし"));
		assertTrue(userPrompt.contains("Markdown"));
	}
}
