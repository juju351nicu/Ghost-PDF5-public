package com.clip.ghost.imagecontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像Markdown下書きのOpenAI（ChatGPT系）providerに関する設定。
 * <p>
 * 既定は無効で、有効化した場合のみ外部AIへ画像を送信する。APIキーはこのクラスには持たず、
 * {@code apiKeyEnv} で指定した環境変数から実行時に読み取る。
 */
@ConfigurationProperties(prefix = "ghost.ocr.openai")
@Getter
@Setter
public class OpenAiProperties {
	/** OpenAI providerの有効化フラグ。 */
	private boolean enabled = false;

	/** 使用するvision対応モデルID。 */
	private String model = "gpt-4o";

	/** APIキーを読み取る環境変数名。キー値そのものは設定・コード・ログに持たない。 */
	private String apiKeyEnv = "OPENAI_API_KEY";

	/** 1回の変換のタイムアウト秒数。 */
	private int timeoutSeconds = 60;

	/** アップロード画像の許容サイズ上限（byte）。 */
	private long maxImageFileSize = 20_559_957L;

	/** アップロード画像の許容画素数上限（幅×高さ）。 */
	private long maxImagePixels = 40_000_000L;

	/** 応答の最大出力トークン数。 */
	private int maxOutputTokens = 8000;
}
