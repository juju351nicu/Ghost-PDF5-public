package com.clip.ghost.markdowncontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文プレビューAPIのリクエストDTO。
 */
@Schema(description = "Markdown本文プレビューリクエスト。")
@Getter
@Setter
public class MarkdownPreviewRequest {
	/**
	 * プレビューしたいMarkdown本文。
	 */
	@Schema(description = "プレビューしたいMarkdown本文。", example = "# 設計メモ")
	@JsonProperty("content")
	@NotNull
	private String content;
}
