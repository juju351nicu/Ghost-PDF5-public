package com.clip.ghost.officecontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Office文書からMarkdownを起こす条件を受け取るリクエストフォーム。
 * <p>
 * 形式はリクエストで指定させず、ファイルの拡張子から判定する。利用者に形式を選ばせると、
 * 選び間違いが「読めない」ではなく「壊れた変換結果」として出てしまう。
 */
@Schema(description = "Office文書からMarkdownを起こす条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class OfficeMarkdownRequest {
	/** Markdownへ変換するOffice文書。 */
	@Schema(description = "Markdownへ変換するOffice文書。.docx / .xlsx / .pptx を指定します。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("officeFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile officeFile;
}
