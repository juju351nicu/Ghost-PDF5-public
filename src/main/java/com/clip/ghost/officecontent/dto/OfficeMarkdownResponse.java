package com.clip.ghost.officecontent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * Office文書から起こしたMarkdownを返すレスポンス。
 */
@Schema(description = "Office文書から起こしたMarkdown。")
@Getter
@Setter
public class OfficeMarkdownResponse {
	/** 変換元のファイル名。 */
	@Schema(description = "変換元のファイル名。", example = "設計書.docx")
	private String fileName;

	/** 変換元のファイルサイズ（byte）。 */
	@Schema(description = "変換元のファイルサイズ（byte）。", example = "20480")
	private Long fileSize;

	/** 判定したOffice形式。 */
	@Schema(description = "拡張子から判定したOffice形式。", example = "DOCX")
	private String documentType;

	/** 起こしたMarkdown本文。 */
	@Schema(description = "起こしたMarkdown本文。レイアウトは再現せず、見出し・段落・表の構造だけを移します。")
	private String markdown;
}
