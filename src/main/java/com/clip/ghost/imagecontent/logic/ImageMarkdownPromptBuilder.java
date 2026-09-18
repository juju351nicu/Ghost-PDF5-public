package com.clip.ghost.imagecontent.logic;

/**
 * 画像文字起こしでAIへ渡す指示文（system / userプロンプト）を保持する。
 * <p>
 * provider実装（Anthropic / OpenAI）がそれぞれ同じ文言を持つと、片方だけ手を入れたときに
 * 「Anthropicでは表になるのにOpenAIでは表にならない」という気づきにくい差になる。文言をここへ集約し、
 * providerはこの値を送るだけにする。Markdown本文AI変換の {@code aicontent.logic.MarkdownAiPromptBuilder}
 * と同じ形にする。
 */
public final class ImageMarkdownPromptBuilder {
	/** 画像内のテキストをMarkdownへ写すときの方針。 */
	private static final String SYSTEM_PROMPT = "あなたは画像内のテキストを忠実にMarkdownへ文字起こしします。表はMarkdownの表、コードはコードフェンスで囲みます。読み取れない箇所や推測で補った箇所は明示します。説明や前置きは書かず、文字起こし結果のMarkdownだけを返します。出力全体をコードフェンスで囲まないでください。コードフェンスは、画像内にソースコードが写っている部分にだけ使います。";

	/** 画像と一緒に送る依頼文。 */
	private static final String USER_PROMPT = "この画像を文字起こししてMarkdownで返してください。";

	private ImageMarkdownPromptBuilder() {
	}

	/**
	 * 画像文字起こしのsystemプロンプトを返す。
	 *
	 * @return systemプロンプト
	 */
	public static String buildSystemPrompt() {
		return SYSTEM_PROMPT;
	}

	/**
	 * 画像文字起こしのuserプロンプトを返す。
	 *
	 * @return userプロンプト
	 */
	public static String buildUserPrompt() {
		return USER_PROMPT;
	}
}
