package com.clip.ghost.aicontent.dto;

import com.clip.ghost.aicontent.enums.AiTaskType;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文AI整形・要約APIのリクエストDTO。
 * <p>
 * 保存済みファイル名ではなく本文を直接受け取る。{@code MarkdownPreviewRequest} と同じ形にし、
 * 保存前の下書き段階でも使えるようにする。
 */
@Schema(description = "Markdown本文AI整形・要約リクエスト。")
@Getter
@Setter
public class MarkdownAiTransformRequest {
	/**
	 * 変換対象のMarkdown本文。
	 */
	@Schema(description = "変換対象のMarkdown本文。", example = "# 設計メモ")
	@JsonProperty("content")
	@NotNull
	private String content;

	/**
	 * 変換タスク（整形／要約）。
	 */
	@Schema(description = "変換タスク。SUMMARIZEは要約、REFINEは整形。", example = "REFINE", allowableValues = { "SUMMARIZE",
			"REFINE" })
	@JsonProperty("task")
	@NotNull(message = "taskはSUMMARIZEまたはREFINEで指定してください。")
	private AiTaskType task;
}
