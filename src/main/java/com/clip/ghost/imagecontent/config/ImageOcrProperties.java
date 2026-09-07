package com.clip.ghost.imagecontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像Markdown下書きで使用する変換providerの選択設定。
 * <p>
 * provider別の詳細設定は {@link AnthropicProperties}（anthropic）や {@code OpenAiProperties}（openai）が持ち、
 * このクラスはどのproviderを使うかだけを保持する。
 * 画像PDFのAUTO下書き固有の設定（{@code ghost.ocr.pdf.*}）は、内容がPDF固有のため
 * {@code com.clip.ghost.pdfcontent.config.PdfOcrProperties} が持つ。
 */
@ConfigurationProperties(prefix = "ghost.ocr")
@Getter
@Setter
public class ImageOcrProperties {
	/** 使用する画像→Markdown変換のprovider。{@code anthropic} または {@code openai}。 */
	private String provider = "anthropic";
}
