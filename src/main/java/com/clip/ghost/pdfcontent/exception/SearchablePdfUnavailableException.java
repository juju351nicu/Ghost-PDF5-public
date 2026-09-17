package com.clip.ghost.pdfcontent.exception;

/**
 * 検索可能PDF（OCRサンドイッチPDF）生成機能が利用できない場合に送出する例外。
 * <p>
 * Tesseractが無効の状態を表す。HTTP 503として扱う。
 * <p>
 * {@code imagecontent.exception.OcrUnavailableException} は再利用しない。共通の例外ハンドラーは
 * 例外の種類ごとに固定メッセージ（「画像Markdown下書き機能は無効です。」）を返すため、共有すると
 * 本機能の利用者へ無関係な機能名を含む誤ったメッセージが返ってしまう。
 */
public class SearchablePdfUnavailableException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public SearchablePdfUnavailableException(String message) {
		super(message);
	}
}
