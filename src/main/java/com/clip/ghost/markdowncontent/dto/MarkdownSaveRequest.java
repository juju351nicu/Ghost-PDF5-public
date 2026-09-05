package com.clip.ghost.markdowncontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Markdown保存APIのリクエストDTO。
 * <p>
 * PDFから抽出したテキストや、将来画面で編集したMarkdownを保存する最小入力として扱う。
 */
@Schema(description = "Markdown保存リクエスト。")
@Getter
@Setter
public class MarkdownSaveRequest {
	/**
	 * 保存したいMarkdownファイル名。拡張子未指定、または別拡張子の場合は .md として保存する。
	 */
	@Schema(description = "保存したいMarkdownファイル名。", example = "design-note.md")
	@JsonProperty("fileName")
	@NotBlank
	@Size(max = 255)
	private String fileName;

	/**
	 * 保存するMarkdown本文。
	 */
	@Schema(description = "保存するMarkdown本文。", example = "# 設計メモ")
	@JsonProperty("content")
	@NotNull
	private String content;
}
