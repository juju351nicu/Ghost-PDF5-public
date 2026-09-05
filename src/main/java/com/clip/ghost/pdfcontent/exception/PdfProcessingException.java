package com.clip.ghost.pdfcontent.exception;

/**
 * PDFの保存、読み込み、加工に失敗した場合に送出する例外。
 * <p>
 * PDFBoxやファイルI/O由来の例外をアプリケーションのPDF処理失敗として扱い、 呼び出し元で原因を追いやすくするために使用する。
 */
public class PdfProcessingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public PdfProcessingException(String message) {
		super(message);
	}

	/**
	 * エラーメッセージと原因例外を指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 * @param cause   原因例外
	 */
	public PdfProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}
