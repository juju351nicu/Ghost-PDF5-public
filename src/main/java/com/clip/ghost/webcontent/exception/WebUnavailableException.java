package com.clip.ghost.webcontent.exception;

/**
 * URLからのWebページ取得機能が無効な状態で呼び出された場合に送出する例外。
 * <p>
 * {@code ghost.web.fetch.enabled} が {@code false}（既定）のときの
 * {@code POST /markdownDraftUrl} がここへ来る。設定を有効にすれば通るため、
 * 入力の誤りではなく機能無効としてHTTP 503で返す。
 */
public class WebUnavailableException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public WebUnavailableException(String message) {
		super(message);
	}
}
