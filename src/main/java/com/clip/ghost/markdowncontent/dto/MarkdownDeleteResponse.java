package com.clip.ghost.markdowncontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存済みMarkdown削除APIのレスポンスDTO。
 */
@Schema(description = "保存済みMarkdown削除レスポンス。")
@Getter
@Setter
public class MarkdownDeleteResponse {
	/**
	 * 削除したMarkdownファイル名。
	 */
	@Schema(description = "削除したMarkdownファイル名。", example = "design-note.md")
	@JsonProperty("fileName")
	private String fileName;

	/**
	 * 削除が完了したか。
	 */
	@Schema(description = "削除が完了したか。", example = "true")
	@JsonProperty("deleted")
	private Boolean deleted;
}
