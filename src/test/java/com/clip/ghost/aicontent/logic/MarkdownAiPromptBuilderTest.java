package com.clip.ghost.aicontent.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.aicontent.enums.AiTaskType;

/**
 * {@link MarkdownAiPromptBuilder} のプロンプト組み立てを、実AI呼び出しなしで固定入力によって検証するテスト。
 */
class MarkdownAiPromptBuilderTest {

	@Test
	@DisplayName("REFINEのsystemプロンプトは原文の情報を削らない指示を含む")
	void refineSystemPromptKeepsOriginalInformation() {
		String systemPrompt = MarkdownAiPromptBuilder.buildSystemPrompt(AiTaskType.REFINE);

		assertTrue(systemPrompt.contains("原文の情報を削らない"));
		assertTrue(systemPrompt.contains("結果のMarkdownだけを返して"));
	}

	@Test
	@DisplayName("SUMMARIZEのsystemプロンプトは数値・固有名詞・結論の保持を含む")
	void summarizeSystemPromptKeepsKeyFacts() {
		String systemPrompt = MarkdownAiPromptBuilder.buildSystemPrompt(AiTaskType.SUMMARIZE);

		assertTrue(systemPrompt.contains("数値、固有名詞、結論は保持"));
		assertTrue(systemPrompt.contains("結果のMarkdownだけを返して"));
	}

	@Test
	@DisplayName("REFINEとSUMMARIZEのsystemプロンプトは異なる")
	void systemPromptsDifferByTask() {
		assertNotEquals(MarkdownAiPromptBuilder.buildSystemPrompt(AiTaskType.REFINE),
				MarkdownAiPromptBuilder.buildSystemPrompt(AiTaskType.SUMMARIZE));
	}

	@Test
	@DisplayName("両タスクのsystemプロンプトはコードフェンスで囲まない制約を含む")
	void systemPromptsForbidWrappingFence() {
		assertTrue(MarkdownAiPromptBuilder.buildSystemPrompt(AiTaskType.REFINE).contains("コードフェンスで囲まないで"));
		assertTrue(MarkdownAiPromptBuilder.buildSystemPrompt(AiTaskType.SUMMARIZE).contains("コードフェンスで囲まないで"));
	}

	@Test
	@DisplayName("userプロンプトは対象Markdown本文をそのまま含む")
	void userPromptContainsMarkdownBody() {
		String userPrompt = MarkdownAiPromptBuilder.buildUserPrompt("# 見出し\n\n本文", AiTaskType.REFINE);

		assertTrue(userPrompt.contains("# 見出し\n\n本文"));
		assertFalse(userPrompt.isBlank());
	}
}
