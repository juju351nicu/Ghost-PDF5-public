package com.clip.ghost.aicontent.dto;

import com.clip.ghost.aicontent.enums.AiTaskType;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文AI整形・要約の結果を返すレスポンスDTO。
 * <p>
 * 変換結果のMarkdownを返す。自動保存はせず、利用者が確認・採用してから既存Markdown保存APIへ渡す前提とする。
 * 要約・整形は原文の意味を変えていないかを機械的に保証できないため、入力・出力の文字数を添えて、
 * 利用者が「極端に短くなっていないか」を一目で気づけるようにする。
 */
@Schema(description = "Markdown本文AI整形・要約の結果。")
@Getter
@Setter
public class MarkdownAiTransformResponse {
	/** 実行した変換タスク（整形／要約）。 */
	@Schema(description = "実行した変換タスク。", example = "REFINE")
	@JsonProperty("task")
	private AiTaskType task;

	/** 変換結果のMarkdown。 */
	@Schema(description = "変換結果のMarkdown。", example = "## 見出し\n\n本文")
	@JsonProperty("markdown")
	private String markdown;

	/** 入力Markdown本文の文字数。 */
	@Schema(description = "入力Markdown本文の文字数。", example = "1234")
	@JsonProperty("inputCharacterCount")
	private Integer inputCharacterCount;

	/** 変換結果Markdownの文字数。 */
	@Schema(description = "変換結果Markdownの文字数。", example = "567")
	@JsonProperty("outputCharacterCount")
	private Integer outputCharacterCount;
}
