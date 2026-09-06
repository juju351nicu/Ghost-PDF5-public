package com.clip.ghost.imagecontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像から生成したMarkdown下書きを返すレスポンスDTO。
 * <p>
 * 文字起こし結果のMarkdownを返す。自動保存はせず、利用者が確認・編集してから既存Markdown保存APIへ渡す前提とする。
 */
@Schema(description = "画像から生成したMarkdown下書き。")
@Getter
@Setter
public class ImageMarkdownDraftResponse {
	/** アップロード時の元ファイル名。 */
	@Schema(description = "アップロード時の元ファイル名。", example = "shot.png")
	@JsonProperty("fileName")
	private String fileName;

	/** アップロードファイルサイズ。 */
	@Schema(description = "アップロードファイルサイズ。", example = "123456")
	@JsonProperty("fileSize")
	private Long fileSize;

	/** 文字起こし結果のMarkdown下書き。 */
	@Schema(description = "文字起こし結果のMarkdown下書き。", example = "## 見出し\n\n本文")
	@JsonProperty("markdown")
	private String markdown;
}
