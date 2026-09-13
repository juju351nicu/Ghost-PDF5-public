package com.clip.ghost.markdowncontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * EPUBからPDFを作る条件を受け取るリクエストフォーム。
 * <p>
 * multipartでEPUBを1つ受け取り、本文をspine（読む順序）どおりに連結してPDFへ描画する。
 * 画像・CSS・フォントは取り込まない。
 */
@Schema(description = "EPUBからPDFを作る条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class PdfFromEpubRequest {
	/** PDFへ変換するEPUBファイル。 */
	@Schema(description = "PDFへ変換するEPUBファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("epubFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile epubFile;

	/** ダウンロード時のファイル名。省略時はEPUBファイル名から決める。 */
	@Schema(description = "ダウンロード時のファイル名。省略時はEPUBファイル名から決めます。", example = "design-note.pdf")
	@JsonProperty("fileName")
	private String fileName;
}
