package com.clip.ghost.imagecontent.exception;

/**
 * 画像からMarkdownへの変換に失敗した場合に送出する例外。
 * <p>
 * 外部vision呼び出しやI/O由来の失敗を、画像処理失敗として扱う。HTTP 500として扱う。
 * 原因例外は包むが、メッセージにAPIキーや画像内容を含めない。
 */
public class ImageProcessingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public ImageProcessingException(String message) {
		super(message);
	}

	/**
	 * エラーメッセージと原因例外を指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 * @param cause   原因例外
	 */
	public ImageProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
