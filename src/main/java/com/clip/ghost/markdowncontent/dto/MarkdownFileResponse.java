package com.clip.ghost.markdowncontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存済みMarkdownファイルの最小メタデータを返すレスポンスDTO。
 */
@Schema(description = "保存済みMarkdownファイルの情報。")
@Getter
@Setter
public class MarkdownFileResponse {
	/**
	 * 保存されたMarkdownファイル名。
	 */
	@Schema(description = "保存されたMarkdownファイル名。", example = "design-note.md")
	@JsonProperty("fileName")
	private String fileName;

	/**
	 * 保存後のファイルサイズ。
	 */
	@Schema(description = "保存後のファイルサイズ。", example = "1234")
	@JsonProperty("byteSize")
	private Long byteSize;

	/**
	 * 保存後の行数。
	 */
	@Schema(description = "保存後の行数。", example = "12")
	@JsonProperty("lineCount")
	private Long lineCount;

	/**
	 * 保存後の最終更新時刻文字列。
	 */
	@Schema(description = "保存後の最終更新時刻。", example = "2026-07-25T16:45:00")
	@JsonProperty("lastModifiedTime")
	private String lastModifiedTime;
}
