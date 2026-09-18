package com.clip.ghost.webcontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * URLからMarkdown下書きを起こす条件を受け取るリクエストDTO。
 * <p>
 * URLの書式・スキーム・ポート・宛先の検査はLogic（{@code WebAddressValidator}）で行う。
 * ここでは「空でないこと」と「極端に長くないこと」だけを見る。宛先の可否はvalidationアノテーションの
 * 正規表現では表現できず、名前解決の結果まで見なければ判断できないため。
 */
@Schema(description = "URLからMarkdown下書きを起こす条件。")
@Getter
@Setter
public class WebUrlMarkdownDraftRequest {
	/** 取得するWebページのURL。 */
	@Schema(description = "取得するWebページのURL。http / https のみ。", example = "https://example.com/article", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("url")
	@NotBlank(message = "URLを入力してください。")
	@Size(max = 2000, message = "URLは2000文字以内で入力してください。")
	private String url;

	/** 本文を絞り込むCSSセレクタ。未指定ならページ全体が対象。 */
	@Schema(description = "本文を絞り込むCSSセレクタ。未指定ならページ全体が対象です。", example = "article")
	@JsonProperty("selector")
	@Size(max = 200, message = "セレクタは200文字以内で入力してください。")
	private String selector;
}
