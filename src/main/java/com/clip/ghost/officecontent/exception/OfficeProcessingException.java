package com.clip.ghost.officecontent.exception;

/**
 * Office文書の読み取りや変換で想定外の失敗が起きた場合に送出する例外。
 * <p>
 * 利用者側で打つ手が無い失敗を表し、HTTP 500として扱う。入力の誤りは
 * {@link OfficeInputException}（400）で分けて扱う。
 */
public class OfficeProcessingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * メッセージを指定して例外を生成する。
	 *
	 * @param message 失敗の内容
	 */
	public OfficeProcessingException(String message) {
		super(message);
	}

	/**
	 * メッセージと原因を指定して例外を生成する。
	 *
	 * @param message 失敗の内容
	 * @param cause   原因となった例外
	 */
	public OfficeProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
