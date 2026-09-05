package com.clip.ghost.pdfcontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * アップロードされたPDFの基本情報を返すレスポンスDTO。
 * <p>
 * Phase 1のPDF基本API拡張で、ページ抽出・結合・分割などの入力補助に使う最小メタデータを表す。
 */
@Schema(description = "アップロードされたPDFの基本情報。")
@Getter
@Setter
public class PdfMetadataResponse {
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

	/** PDFが暗号化されているか。 */
	@Schema(description = "PDFが暗号化されているか。", example = "false")
	@JsonProperty("encrypted")
	private Boolean encrypted;
}
