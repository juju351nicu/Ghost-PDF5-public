package com.clip.ghost.imagecontent.logic;

import java.util.List;

import com.clip.ghost.imagecontent.dto.OcrWordBox;

/**
 * 画像から単語単位の位置付きOCR結果を取り出す処理の抽象。
 * <p>
 * {@link ImageToMarkdownConverter} とは別interfaceにする。返す型が平文（Markdown）ではなく
 * 単語ごとの座標付き結果であり、契約を混ぜないため。位置情報が取れるのはTesseractの座標付き出力
 * （TSV）だけのため、vision（Anthropic/OpenAI）は実装を持たず、providerによる選択（resolver）も設けない。
 */
public interface TesseractWordBoxExtractor {
	/**
	 * 機能が有効かどうかを返す。
	 * <p>
	 * 機能の有効化フラグや必要なコマンドの有無を含めて判定する。
	 *
	 * @return 利用可能な場合はtrue
	 */
	boolean isEnabled();

	/**
	 * 実装の識別文字列を返す。ログや診断に用いる。
	 *
	 * @return 実装を説明する文字列
	 */
	String describe();

	/**
	 * 画像バイト列から単語単位の位置付きOCR結果を取り出す。
	 *
	 * @param imageBytes 画像のバイト列
	 * @return 認識された単語ボックスの一覧（画像左上原点・ピクセル座標）
	 */
	List<OcrWordBox> extractWordBoxes(byte[] imageBytes);
}
