package com.clip.ghost.aicontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文AI整形・要約で使用する変換providerの選択設定。
 * <p>
 * provider別の詳細設定は {@link AnthropicAiProperties}（anthropic）や {@link OpenAiAiProperties}（openai）が持ち、
 * このクラスはどのproviderを使うかだけを保持する。{@code imagecontent.config.ImageOcrProperties} と同じ形にする。
 */
@ConfigurationProperties(prefix = "ghost.ai")
@Getter
@Setter
public class AiProperties {
	/** 使用するMarkdown AI変換のprovider。{@code anthropic} または {@code openai}。 */
	private String provider = "anthropic";
}
