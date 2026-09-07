package com.clip.ghost.pdfcontent.dto;

/**
 * PDF1ページ分の抽出結果を表す内部DTO。
 * <p>
 * 文字レイヤーのテキストと、文字を取得できないページを画像化して変換した結果のテキストを持つ。
 * 画像化したPNGバイト列はLogic層の外へ出さないため、このDTOには持たせない。
 * このDTOはService/Logic間の受け渡し用で、JSONレスポンスには使わない。
 *
 * @param pageNumber    1始まりのページ番号
 * @param text          PDFBoxで抽出したページ本文
 * @param convertedText 画像化して変換器へ渡した結果。文字レイヤーから取得できたページはnull
 */
public record PdfPageContent(int pageNumber, String text, String convertedText) {
}
