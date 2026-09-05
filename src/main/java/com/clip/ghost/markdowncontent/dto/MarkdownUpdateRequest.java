package com.clip.ghost.markdowncontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存済みMarkdown更新APIのリクエストDTO。
 */
@Schema(description = "保存済みMarkdown更新リクエスト。")
@Getter
@Setter
public class MarkdownUpdateRequest {
	/**
	 * 更新後のMarkdown本文。
	 */
	@Schema(description = "更新後のMarkdown本文。", example = "# 更新後の設計メモ")
	@JsonProperty("content")
	@NotNull
	private String content;
}
