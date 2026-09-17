package com.clip.ghost.aicontent.exception;

/**
 * Markdown本文のAI整形・要約に失敗した場合に送出する例外。
 * <p>
 * 外部AI呼び出しやSDK/ネットワーク由来の失敗を、AI処理失敗として扱う。HTTP 500として扱う。
 * 原因例外は包むが、メッセージにAPIキーやMarkdown本文を含めない。
 */
public class AiProcessingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public AiProcessingException(String message) {
		super(message);
	}

	/**
	 * エラーメッセージと原因例外を指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 * @param cause   原因例外
	 */
	public AiProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
