package com.clip.ghost.webcontent.exception;

/**
 * Webページ取り込みの処理に失敗した場合に送出する例外。
 * <p>
 * アップロードファイルの読み取り失敗など、利用者が入力を直しても通るとは限らない失敗を表す。
 * HTTP 500として扱う。原因例外は包むが、メッセージに取り込んだHTMLの内容を含めない。
 */
public class WebProcessingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public WebProcessingException(String message) {
		super(message);
	}

	/**
	 * エラーメッセージと原因例外を指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 * @param cause   原因例外
	 */
	public WebProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
