package com.clip.ghost.webcontent.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * URLからのWebページ取得（{@code POST /markdownDraftUrl}）の設定。
 * <p>
 * この機能はサーバーが利用者の指定した宛先へ接続する。外部AI送信（{@code ghost.ai.*}）や
 * 画像文字起こし（{@code ghost.ocr.*}）と同じく、**既定は無効**にする。無効のまま運用すれば、
 * アプリから外部へ出る経路はAI providerのSDKだけに保たれる。
 * <p>
 * HTMLファイルのアップロードからの変換（{@code POST /markdownDraftHtml}）はこの設定に影響されない。
 * あちらはネットワークへ出ないため、有効・無効を分ける理由が無い。
 */
@ConfigurationProperties(prefix = "ghost.web.fetch")
@Getter
@Setter
public class WebFetchProperties {
	/** URLからの取得を有効にするか。既定は無効。 */
	private boolean enabled = false;

	/** 接続・読み取りのタイムアウト秒数。 */
	private int timeoutSeconds = 10;

	/** 取得する本文の最大バイト数。宣言値ではなく実読み取りバイト数で判定する。 */
	private long maxBytes = 2_097_152L;

	/** 追跡するリダイレクトの最大回数。 */
	private int maxRedirects = 3;

	/** 直近の取得からこのミリ秒数が経つまで次の取得を受け付けない。 */
	private long minIntervalMillis = 1_000L;

	/** 送信するUser-Agent。ブラウザを偽装しない。 */
	private String userAgent = "Ghost-PDF5";

	/**
	 * ループバック宛の接続を許可するか。
	 * <p>
	 * 既定は拒否。有効にできるのは、ループバックでHTTPサーバーを立てる自動テストと、
	 * 手元で動かしている別サービスを取り込む開発用途だけを想定する。
	 * externalから見える挙動は変わらないが、有効にすると同一ホスト上のサービスへ
	 * アプリ経由で到達できるようになるため、通常運用では触らない。
	 */
	private boolean allowLoopback = false;

	/**
	 * 接続を許可するポート。
	 * <p>
	 * 既定はHTTP / HTTPSの標準ポートだけにする。任意のポートを許すと、
	 * 「Webページの取得」という用途を超えて、社内の管理画面やデータベースの待ち受けポートを
	 * 叩く経路になり得る。ループバックでHTTPサーバーを立てるテストのように、標準ポートを
	 * 使えない場合だけ追加する。
	 */
	private List<Integer> allowedPorts = List.of(80, 443);
}
