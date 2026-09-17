package com.clip.ghost.aicontent.exception;

import lombok.Getter;

/**
 * Markdown本文AI変換の入力文字数が上限を超えた場合に送出する例外。
 * <p>
 * 外部AI（Anthropic / OpenAI）の課金が発生する前に処理を止めるコストガードで、HTTP 400として扱う。
 * <p>
 * 文字数は文書の内容そのものではないため、メッセージ・ログ・レスポンスに含めても情報漏洩にならない。
 */
@Getter
public class AiInputException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 入力Markdown本文の文字数。 */
	private final int characterCount;

	/** 設定された入力文字数の上限。 */
	private final int maxCharacters;

	/**
	 * 入力文字数と上限を指定して例外を生成する。
	 *
	 * @param characterCount 入力Markdown本文の文字数
	 * @param maxCharacters  設定された入力文字数の上限
	 */
	public AiInputException(int characterCount, int maxCharacters) {
		super("入力文字数が上限を超えています。characterCount=" + characterCount + ", maxCharacters=" + maxCharacters);
		this.characterCount = characterCount;
		this.maxCharacters = maxCharacters;
	}
}
