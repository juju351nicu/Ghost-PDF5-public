package com.clip.ghost.imagecontent.logic;

/**
 * 画像をMarkdownへ文字起こしする変換器の抽象。
 * <p>
 * 第1実装は外部visionモデル、将来の第2実装はローカルOCR（Tesseract）を想定する。
 * Controller / Serviceはこのinterface越しに使い、実装差し替えの影響を受けないようにする。
 * 外部AIのSDKやOCRエンジン依存は、実装クラスの内部に閉じ込める。
 */
public interface ImageToMarkdownConverter {
	/**
	 * 変換が利用可能かを返す。
	 * <p>
	 * 機能の有効化フラグや必要な資格情報の有無を含めて判定する。
	 *
	 * @return 利用可能な場合はtrue
	 */
	boolean isEnabled();

	/**
	 * 実装の識別文字列を返す。ログや診断に用いる。資格情報や画像内容は含めない。
	 *
	 * @return 実装を説明する文字列
	 */
	String describe();

	/**
	 * 画像バイト列をMarkdownへ文字起こしする。
	 *
	 * @param imageBytes 画像のバイト列
	 * @param mediaType  画像のMIMEタイプ（例: {@code image/png}）
	 * @return 文字起こし結果のMarkdown
	 */
	String convert(byte[] imageBytes, String mediaType);

	/**
	 * この変換器のprovider識別子を返す。設定 {@code ghost.ocr.provider} と突き合わせて選択に使う。
	 *
	 * @return provider識別子（例: {@code anthropic} / {@code openai}）
	 */
	String provider();
}
