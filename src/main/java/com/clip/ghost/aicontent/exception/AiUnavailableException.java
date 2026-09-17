package com.clip.ghost.aicontent.exception;

/**
 * Markdown本文AI整形・要約機能が利用できない場合に送出する例外。
 * <p>
 * 機能が無効、またはAPIキーが未設定の状態を表す。HTTP 503として扱う。
 */
public class AiUnavailableException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public AiUnavailableException(String message) {
		super(message);
	}
}
