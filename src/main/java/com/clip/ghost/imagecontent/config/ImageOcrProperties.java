package com.clip.ghost.imagecontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像Markdown下書きで使用する変換providerの選択設定。
 * <p>
 * provider別の詳細設定は {@link VisionProperties}（anthropic）や {@code OpenAiProperties}（openai）が持ち、
 * このクラスはどのproviderを使うかだけを保持する。
 */
@ConfigurationProperties(prefix = "ghost.ocr")
@Getter
@Setter
public class ImageOcrProperties {
	/** 使用する画像→Markdown変換のprovider。{@code anthropic} または {@code openai}。 */
	private String provider = "anthropic";
}
