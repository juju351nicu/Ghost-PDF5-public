package com.clip.ghost.imagecontent.exception;

/**
 * アップロードされた画像が入力として不正な場合に送出する例外。
 * <p>
 * 対応していない画像形式や、画像として読めない入力を表す。HTTP 400として扱う。
 */
public class ImageInputException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public ImageInputException(String message) {
		super(message);
	}
}
