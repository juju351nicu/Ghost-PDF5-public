package com.clip.ghost.pdfcontent.logic;

/**
 * 画像化したPDF1ページ分をテキストへ変換する処理の抽象。
 * <p>
 * 実体は呼び出し元（{@code pdfcontent.service}）が共有の画像変換器（OCR/vision）を包んだlambdaとして渡す。
 * これによりLogic層はPNGバイト列をこのinterfaceの外へ出さずに済み、同時にメモリへ載る画像を1ページ分に抑えられる。
 */
@FunctionalInterface
public interface PdfPageImageConverter {
	/**
	 * 画像化した1ページ分のPNGバイト列をテキストへ変換する。
	 *
	 * @param pngBytes 画像化したPNGバイト列
	 * @return 変換結果のテキスト
	 */
	String convert(byte[] pngBytes);
}
