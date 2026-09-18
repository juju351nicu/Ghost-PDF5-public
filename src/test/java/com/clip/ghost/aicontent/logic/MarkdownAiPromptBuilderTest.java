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
	@DisplayName("SUMMARIZEのsystemプロンプトは固有名詞の種類を具体的に挙げ、1回しか登場しなくても保持する指示を含む")
	void summarizeSystemPromptKeepsKeyFacts() {
		// 「固有名詞、結論は保持」とだけ指示した初期版は、本文中に1回しか登場しないシステム名を
		// 実APIで省略した（成果物/4_確認記録/30_実API確認結果_PhaseE_AI整形要約.md）。固有名詞の種類を具体化し、
		// 「1回しか登場しない場合でも省略しない」ことを明示する指示に調整した経緯をこのテストで固定する。
		String systemPrompt = MarkdownAiPromptBuilder.buildSystemPrompt(AiTaskType.SUMMARIZE);

		assertTrue(systemPrompt.contains("人名・製品名・システム名やプロジェクト名・組織名"));
		assertTrue(systemPrompt.contains("1回しか登場しない場合でも省略せず保持"));
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
