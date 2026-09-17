package com.clip.ghost.aicontent.logic;

import com.clip.ghost.aicontent.enums.AiTaskType;

/**
 * {@link AiTaskType} ごとのAIへの指示文（system / userプロンプト）を組み立てる。
 * <p>
 * provider実装（Anthropic / OpenAI）ごとにプロンプト文言をそれぞれ持たせると、「Anthropicで整形した結果と
 * OpenAIで整形した結果の書式が違う」という気づきにくい差分になる。プロンプト文言をここへ集約し、
 * providerはこの結果を使うだけにすることで、実AI呼び出しなしで固定入力によるプロンプト組み立てのテストができる。
 */
public final class MarkdownAiPromptBuilder {
	private static final String COMMON_CONSTRAINT = "説明や前置きは書かず、結果のMarkdownだけを返してください。出力全体をコードフェンスで囲まないでください。";
	private static final String REFINE_SYSTEM_PROMPT = "あなたはMarkdown文書の整形者です。原文の意味を変えずに、誤字脱字、表記ゆれ、"
			+ "Markdown構文の乱れ（見出しレベルの不整合、リストの混在等）を直してください。原文の情報を削らないでください。" + COMMON_CONSTRAINT;
	private static final String SUMMARIZE_SYSTEM_PROMPT = "あなたはMarkdown文書の要約者です。原文の要点を落とさずに短くまとめてください。"
			+ "数値と結論に加え、人名・製品名・システム名やプロジェクト名・組織名などの固有名詞は、"
			+ "本文中に1回しか登場しない場合でも省略せず保持してください。" + COMMON_CONSTRAINT;
	private static final String USER_PROMPT_FORMAT = "次のMarkdown本文を%sしてください。%n%n%s";

	private MarkdownAiPromptBuilder() {
	}

	/**
	 * 指定タスクのsystemプロンプトを組み立てる。
	 *
	 * @param taskType 変換タスク（整形／要約）
	 * @return systemプロンプト
	 */
	public static String buildSystemPrompt(AiTaskType taskType) {
		return switch (taskType) {
		case REFINE -> REFINE_SYSTEM_PROMPT;
		case SUMMARIZE -> SUMMARIZE_SYSTEM_PROMPT;
		};
	}

	/**
	 * 指定タスクのuserプロンプトを組み立てる。
	 *
	 * @param markdown 変換対象のMarkdown本文
	 * @param taskType 変換タスク（整形／要約）
	 * @return userプロンプト
	 */
	public static String buildUserPrompt(String markdown, AiTaskType taskType) {
		return USER_PROMPT_FORMAT.formatted(taskType.getValue(), markdown);
	}
}
