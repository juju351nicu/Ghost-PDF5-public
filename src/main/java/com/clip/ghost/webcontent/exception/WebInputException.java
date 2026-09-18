package com.clip.ghost.webcontent.exception;

import lombok.Getter;

/**
 * Webページ取り込みの入力が不正な場合に送出する例外。
 * <p>
 * 対象外の拡張子、HTMLとして解析できない内容、セレクタの書式不正、抽出結果が空、といった
 * 「利用者が別の入力を用意すれば通る」失敗を表す。HTTP 400として扱い、
 * {@link WebProcessingException}（500）とは分ける。
 */
@Getter
public class WebInputException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * 画面へそのまま表示する説明。
	 * <p>
	 * 何を直せば通るのかは原因ごとに違うため、共通の固定文言ではなく呼び出し元が組み立てた文言を返す。
	 * 取り込んだHTMLの本文・属性値をここへ入れない。入力の一部をエラー画面へ echo すると、
	 * 取り込み元の内容が意図せず画面とログへ出る。
	 */
	private final String displayMessage;

	/**
	 * 画面表示用の説明を指定して例外を生成する。
	 *
	 * @param displayMessage 画面へ表示する説明。文書の内容を含めない固定文言
	 */
	public WebInputException(String displayMessage) {
		super(displayMessage);
		this.displayMessage = displayMessage;
	}

	/**
	 * 画面表示用の説明と原因例外を指定して例外を生成する。
	 *
	 * @param displayMessage 画面へ表示する説明。文書の内容を含めない固定文言
	 * @param cause          原因となった例外
	 */
	public WebInputException(String displayMessage, Throwable cause) {
		super(displayMessage, cause);
		this.displayMessage = displayMessage;
	}
}
