package com.clip.ghost.webcontent.exception;

import lombok.Getter;

/**
 * 取得先が接続を許さない宛先だった場合に送出する例外。
 * <p>
 * 名前解決した結果がループバック、プライベート、リンクローカル（クラウドのメタデータendpointを含む）、
 * ワイルドカード、マルチキャストのいずれかだった場合に送出する。利用者が別のURLを指定すれば通るため
 * HTTP 400として扱い、取得先の障害（{@link WebFetchException}、502）とは分ける。
 * <p>
 * 保持するのはホスト名だけで、解決済みIPアドレスは保持しない。「そのホスト名がどのIPへ解決されたか」は
 * 内部ネットワークの構成そのもので、画面やログへ出すと、この機能が内部の名前解決結果を読み出す
 * 道具になってしまう。
 */
@Getter
public class WebFetchBlockedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 接続を拒否した宛先のホスト名。利用者が指定した値そのもので、解決結果は含まない。 */
	private final String host;

	/**
	 * 拒否した宛先のホスト名を指定して例外を生成する。
	 *
	 * @param host 接続を拒否した宛先のホスト名
	 */
	public WebFetchBlockedException(String host) {
		super("接続が許可されていない宛先です。host=" + host);
		this.host = host;
	}
}
