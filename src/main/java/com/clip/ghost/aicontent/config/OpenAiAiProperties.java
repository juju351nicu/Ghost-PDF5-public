package com.clip.ghost.aicontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文AI整形・要約のOpenAI（ChatGPT系）providerに関する設定。
 * <p>
 * 既定は無効で、有効化した場合のみ外部AIへMarkdown本文を送信する。APIキーはこのクラスには持たず、
 * {@code apiKeyEnv} で指定した環境変数から実行時に読み取る。画像Markdown下書き（{@code ghost.ocr.openai}）と
 * 同じOpenAIアカウントを想定し、APIキー用の環境変数名は共用してよい。
 */
@ConfigurationProperties(prefix = "ghost.ai.openai")
@Getter
@Setter
public class OpenAiAiProperties {
	/** OpenAI providerの有効化フラグ。 */
	private boolean enabled = false;

	/** 使用するモデルID。 */
	private String model = "gpt-4o";

	/** APIキーを読み取る環境変数名。キー値そのものは設定・コード・ログに持たない。 */
	private String apiKeyEnv = "OPENAI_API_KEY";

	/** 1回の変換のタイムアウト秒数。 */
	private int timeoutSeconds = 60;

	/** 応答の最大出力トークン数。整形（REFINE）は要約より出力が長くなり得るため、画像文字起こしより高めにする。 */
	private int maxOutputTokens = 16_000;
}
