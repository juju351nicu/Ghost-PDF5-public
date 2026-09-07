package com.clip.ghost.pdfcontent.dto;

/**
 * PDF1ページ分の抽出結果を表す内部DTO。
 * <p>
 * 文字レイヤーのテキストと、文字を取得できないページを画像化して変換した結果のテキストを持つ。
 * 画像化したPNGバイト列はLogic層の外へ出さないため、このDTOには持たせない。
 * このDTOはService/Logic間の受け渡し用で、JSONレスポンスには使わない。
 * <p>
 * {@code convertedText} がnullになる理由は「変換対象ではなかった」と「変換に失敗した」の2通りあり、
 * 本文が空という結果だけでは区別できない。区別しないと失敗ページを白紙ページとして扱ってしまうため、
 * 失敗の有無を {@code conversionFailed} で明示的に持つ。
 *
 * @param pageNumber       1始まりのページ番号
 * @param text             PDFBoxで抽出したページ本文
 * @param convertedText    画像化して変換器へ渡した結果。文字レイヤーから取得できたページと変換に失敗したページはnull
 * @param conversionFailed 画像化して変換器へ渡したが変換に失敗した場合はtrue
 */
public record PdfPageContent(int pageNumber, String text, String convertedText, boolean conversionFailed) {
}
