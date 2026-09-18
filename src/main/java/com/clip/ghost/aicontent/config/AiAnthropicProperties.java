package com.clip.ghost.aicontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文AI整形・要約のAnthropic（Claude）providerに関する設定。
 * <p>
 * 既定は無効で、有効化した場合のみ外部AIへMarkdown本文を送信する。APIキーはこのクラスには持たず、
 * {@code apiKeyEnv} で指定した環境変数から実行時に読み取る。画像Markdown下書き（{@code ghost.ocr.anthropic}）と
 * 同じAnthropicアカウントを想定し、APIキー用の環境変数名は共用してよい。
 * <p>
 * 接頭辞の {@code Ai} は設定prefixの {@code ghost.ai} に対応する。画像文字起こし側の
 * {@code imagecontent.config.OcrAnthropicProperties}（{@code ghost.ocr.anthropic}）とは、
 * モデルも出力トークン上限も別に設定する。
 */
@ConfigurationProperties(prefix = "ghost.ai.anthropic")
@Getter
@Setter
public class AiAnthropicProperties {
	/** Anthropic providerの有効化フラグ。falseの間は選択時にMarkdown AI変換APIが503を返す。 */
	private boolean enabled = false;

	/** 使用するモデルID。 */
	private String model = "claude-opus-5";

	/** APIキーを読み取る環境変数名。キー値そのものは設定・コード・ログに持たない。 */
	private String apiKeyEnv = "ANTHROPIC_API_KEY";

	/** 1回の変換のタイムアウト秒数。 */
	private int timeoutSeconds = 60;

	/** 応答の最大出力トークン数。整形（REFINE）は要約より出力が長くなり得るため、画像文字起こしより高めにする。 */
	private int maxOutputTokens = 16_000;
}
