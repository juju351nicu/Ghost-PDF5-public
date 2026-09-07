package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * アップロードされたPDFの全ページ分のサムネイルを返すレスポンスDTO。
 * <p>
 * ページ選択UIのために1リクエストで全ページ分を返す。ページ数に比例してレスポンスが大きくなるため、
 * 返せるページ数の上限は {@code ghost.pdf.thumbnail.max-pages} で持つ。
 */
@Schema(description = "アップロードされたPDFの全ページ分のサムネイル。")
@Getter
@Setter
public class PdfThumbnailResponse {
	/** アップロード時の元ファイル名。 */
	@Schema(description = "アップロード時の元ファイル名。", example = "sample.pdf")
	@JsonProperty("fileName")
	private String fileName;

	/** PDFの総ページ数。 */
	@Schema(description = "PDFの総ページ数。", example = "3")
	@JsonProperty("pageCount")
	private Integer pageCount;

	/** PDF順のページ単位サムネイル。 */
	@Schema(description = "PDF順のページ単位サムネイル。")
	@JsonProperty("pages")
	private List<PdfThumbnailPageResponse> pages;
}
