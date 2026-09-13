package com.clip.ghost.pdfcontent.exception;

import lombok.Getter;

/**
 * 画像からPDFを作る際に、画像として読めないファイルを受け取った場合に送出する例外。
 * <p>
 * 利用者が別のファイルを選べば通るエラーのため400として返し、{@link PdfProcessingException}（500）とは分ける。
 * <p>
 * 画像Markdown下書きの {@code ImageInputException} とは別にする。あちらは外部AIへ送れる形式
 * （PNG / JPEG / GIF / WEBP）の制約で、こちらはImageIOで読めるかどうかの制約であり、
 * 受け付けられる形式が一致しない。メッセージを共有すると利用者に誤った形式を案内してしまう。
 */
@Getter
public class PdfImageInputException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 画像として読めなかったファイルの名前。 */
	private final String fileName;

	/**
	 * 読めなかったファイル名を指定して例外を生成する。
	 *
	 * @param fileName 画像として読めなかったファイルの名前
	 */
	public PdfImageInputException(String fileName) {
		super("画像として読み込めないファイルです。fileName=" + fileName);
		this.fileName = fileName;
	}
}
