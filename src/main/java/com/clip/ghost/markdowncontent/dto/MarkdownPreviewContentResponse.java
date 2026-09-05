package com.clip.ghost.markdowncontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 入力中Markdown本文のHTMLプレビューを返すレスポンスDTO。
 */
@Schema(description = "入力中Markdown本文のHTMLプレビュー。")
@Getter
@Setter
public class MarkdownPreviewContentResponse {
	/**
	 * sanitize済みHTMLプレビュー。
	 */
	@Schema(description = "sanitize済みHTMLプレビュー。", example = "<h1>設計メモ</h1>")
	@JsonProperty("html")
	private String html;
}
