package com.clip.ghost.pdfcontent.dto;

/**
 * 画像化したPDF 1ページ分のPNGと、そのページの寸法。
 * <p>
 * 寸法は描画した画素数と解像度から求めたポイント値で持つ。PDFの {@code MediaBox} をそのまま使わないのは、
 * 回転指定のあるページで実際に描かれた向きと食い違うため。描画結果から逆算すれば、常に見たままの寸法になる。
 *
 * @param pageNumber   画面・API仕様の1始まりページ番号
 * @param pngBytes     ページを画像化したPNGのバイト列
 * @param widthPoints  ページの幅（ポイント）
 * @param heightPoints ページの高さ（ポイント）
 */
public record PdfPageImage(int pageNumber, byte[] pngBytes, float widthPoints, float heightPoints) {
}
