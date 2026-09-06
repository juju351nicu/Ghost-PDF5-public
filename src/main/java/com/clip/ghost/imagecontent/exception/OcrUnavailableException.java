package com.clip.ghost.imagecontent.exception;

/**
 * 画像Markdown下書き機能が利用できない場合に送出する例外。
 * <p>
 * 機能が無効、またはAPIキーが未設定の状態を表す。HTTP 503として扱う。
 */
public class OcrUnavailableException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public OcrUnavailableException(String message) {
		super(message);
	}
}
