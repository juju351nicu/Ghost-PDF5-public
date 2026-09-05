package com.clip.ghost.pdfcontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * アップロードされたPDFから抽出したテキストを返すレスポンスDTO。
 * <p>
 * Markdown / AI拡張の前段として、まずPDFBoxで取得できる素のテキストと最小メタデータを返す。
 */
@Schema(description = "アップロードされたPDFから抽出したテキスト。")
@Getter
@Setter
public class PdfTextResponse {
	/** アップロード時の元ファイル名。 */
	@Schema(description = "アップロード時の元ファイル名。", example = "sample.pdf")
	@JsonProperty("fileName")
	private String fileName;

	/** アップロードファイルサイズ。 */
	@Schema(description = "アップロードファイルサイズ。", example = "123456")
	@JsonProperty("fileSize")
	private Long fileSize;

	/** PDFの総ページ数。 */
	@Schema(description = "PDFの総ページ数。", example = "10")
	@JsonProperty("pageCount")
	private Integer pageCount;

	/** PDFから抽出したテキスト。 */
	@Schema(description = "PDFから抽出したテキスト。", example = "PDFから抽出した本文です。")
	@JsonProperty("text")
	private String text;
}
