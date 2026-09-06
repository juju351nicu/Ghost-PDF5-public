package com.clip.ghost.imagecontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像Markdown下書きのAnthropic（Claude）providerに関する設定。
 * <p>
 * 既定は無効で、有効化した場合のみ外部AIへ画像を送信する。APIキーはこのクラスには持たず、
 * {@code apiKeyEnv} で指定した環境変数から実行時に読み取る。
 */
@ConfigurationProperties(prefix = "ghost.ocr.anthropic")
@Getter
@Setter
public class AnthropicProperties {
	/** Anthropic providerの有効化フラグ。falseの間は選択時に画像Markdown下書きAPIが503を返す。 */
	private boolean enabled = false;

	/** 使用するvision対応モデルID。 */
	private String model = "claude-opus-5";

	/** APIキーを読み取る環境変数名。キー値そのものは設定・コード・ログに持たない。 */
	private String apiKeyEnv = "ANTHROPIC_API_KEY";

	/** 1回の変換のタイムアウト秒数。 */
	private int timeoutSeconds = 60;

	/** アップロード画像の許容サイズ上限（byte）。超過時は413。 */
	private long maxImageFileSize = 20_559_957L;

	/** アップロード画像の許容画素数上限（幅×高さ）。超過時は400。 */
	private long maxImagePixels = 40_000_000L;

	/** vision応答の最大出力トークン数。 */
	private int maxOutputTokens = 8000;
}
