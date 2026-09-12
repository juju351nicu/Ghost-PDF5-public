package com.clip.ghost.markdowncontent.exception;

/**
 * MarkdownからのPDF出力に失敗した場合に送出する例外。
 * <p>
 * HTTP 500として扱う。メッセージにはMarkdown本文やローカルパスを含めない。
 * 利用者に見せるのは「PDF出力に失敗した」ことだけで、原因の詳細はログ側に残す。
 */
public class MarkdownPdfException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * メッセージを指定して例外を生成する。
	 *
	 * @param message 失敗の内容
	 */
	public MarkdownPdfException(String message) {
		super(message);
	}

	/**
	 * メッセージと原因を指定して例外を生成する。
	 *
	 * @param message 失敗の内容
	 * @param cause   原因となった例外
	 */
	public MarkdownPdfException(String message, Throwable cause) {
		super(message, cause);
	}
}
