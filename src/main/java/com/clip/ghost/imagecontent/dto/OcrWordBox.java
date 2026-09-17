package com.clip.ghost.imagecontent.dto;

/**
 * OCRが認識した単語1件分の文字列と、画像上の位置を表すDTO。
 * <p>
 * 座標・サイズはOCR入力に使った画像のピクセル値、左上原点（Tesseract TSVの座標系）。
 * PDFへ書き戻す側（{@code pdfcontent}）でpt単位・左下原点のPDF座標へ変換する。
 * DTOはArchUnitの層定義（Controller/Service/Logic）に含まれないため、{@code pdfcontent.logic}からの
 * 参照はレイヤー方向のルールに抵触しない。
 *
 * @param text   認識された文字列
 * @param left   単語ボックスの左端（ピクセル）
 * @param top    単語ボックスの上端（ピクセル）
 * @param width  単語ボックスの幅（ピクセル）
 * @param height 単語ボックスの高さ（ピクセル）
 */
public record OcrWordBox(String text, double left, double top, double width, double height) {
}
