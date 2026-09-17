package com.clip.ghost.pdfcontent.logic;

import java.util.List;

import com.clip.ghost.imagecontent.dto.OcrWordBox;

/**
 * 画像化したPDF1ページ分を、単語単位の位置付きOCR結果へ変換する処理の抽象。
 * <p>
 * {@link PdfPageImageConverter} と同じ役割分担で、実体は呼び出し元（{@code pdfcontent.service}）が
 * 共有のOCR変換器（{@code imagecontent.logic.TesseractWordBoxExtractor}）を包んだlambdaとして渡す。
 * これによりLogic層はPNGバイト列をこのinterfaceの外へ出さずに済む。
 */
@FunctionalInterface
public interface SearchablePdfPageOcr {
	/**
	 * 画像化した1ページ分のPNGバイト列を、単語単位の位置付きOCR結果へ変換する。
	 *
	 * @param pngBytes 画像化したPNGバイト列
	 * @return 認識された単語ボックスの一覧（画像左上原点・ピクセル座標）
	 */
	List<OcrWordBox> recognize(byte[] pngBytes);
}
