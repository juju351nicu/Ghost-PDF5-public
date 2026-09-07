package com.clip.ghost.pdfcontent.dto;

/**
 * PDF1ページ分のサムネイルを表す内部DTO。
 * <p>
 * 画面の {@code img} タグへそのまま渡せるよう、画像はdata URI文字列として持つ。
 * このDTOはService/Logic間の受け渡し用で、JSONレスポンスには {@code PdfThumbnailPageResponse} を使う。
 *
 * @param pageNumber 1始まりのページ番号
 * @param dataUri    {@code data:image/png;base64,} 形式のサムネイル画像
 * @param width      サムネイル画像の幅（px）
 * @param height     サムネイル画像の高さ（px）
 */
public record PdfPageThumbnail(int pageNumber, String dataUri, int width, int height) {
}
