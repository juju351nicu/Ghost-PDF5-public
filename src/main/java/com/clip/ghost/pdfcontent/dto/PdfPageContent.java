package com.clip.ghost.pdfcontent.dto;

/**
 * PDF1ページ分の抽出結果を表す内部DTO。
 * <p>
 * 文字レイヤーのテキストと、文字を取得できないページを画像化したPNGバイト列を持つ。
 * このDTOはService/Logic間の受け渡し用で、JSONレスポンスには使わない。
 *
 * @param pageNumber 1始まりのページ番号
 * @param text       PDFBoxで抽出したページ本文
 * @param imageBytes 画像化したPNGバイト列。文字を取得できたページや画像化しない場合はnull
 */
public record PdfPageContent(int pageNumber, String text, byte[] imageBytes) {
}
