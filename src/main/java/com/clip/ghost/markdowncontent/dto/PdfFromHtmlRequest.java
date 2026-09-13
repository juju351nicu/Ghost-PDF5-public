package com.clip.ghost.markdowncontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * HTMLからPDFを作る条件を受け取るリクエストフォーム。
 * <p>
 * multipartでHTMLファイルを1つ受け取り、PDFへ描画する。外部CSSや外部画像は取り込まない。
 * HTMLの中でスタイルを完結させていないと、見た目は素のHTMLに近い形になる。
 */
@Schema(description = "HTMLからPDFを作る条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class PdfFromHtmlRequest {
	/** PDFへ変換するHTMLファイル。 */
	@Schema(description = "PDFへ変換するHTMLファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("htmlFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile htmlFile;

	/** ダウンロード時のファイル名。省略時はHTMLファイル名から決める。 */
	@Schema(description = "ダウンロード時のファイル名。省略時はHTMLファイル名から決めます。", example = "design-note.pdf")
	@JsonProperty("fileName")
	private String fileName;
}
