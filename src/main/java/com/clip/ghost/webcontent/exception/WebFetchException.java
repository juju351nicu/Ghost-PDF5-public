package com.clip.ghost.webcontent.exception;

/**
 * URLからのWebページ取得に失敗した場合に送出する例外。
 * <p>
 * 取得先の応答が返らない、タイムアウトした、想定外のstatusだった、Content-Typeが対象外だった、
 * 本文がサイズ上限を超えた、リダイレクトが多すぎた、取得間隔の制限に掛かった、がここへ来る。
 * 失敗の原因が取得先または通信にあるため、HTTP 502として扱う。
 * <p>
 * メッセージには解決済みIPアドレスと取得したHTML本文を含めない。前者は内部ネットワークの構成を
 * 外へ返すことになり、後者は取得元の内容をエラー経路から漏らすことになる。
 */
public class WebFetchException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * エラーメッセージを指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 */
	public WebFetchException(String message) {
		super(message);
	}

	/**
	 * エラーメッセージと原因例外を指定して例外を生成する。
	 *
	 * @param message エラーメッセージ
	 * @param cause   原因例外
	 */
	public WebFetchException(String message, Throwable cause) {
		super(message, cause);
	}
}
