package com.clip.ghost.pdfcontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * PDF1ページ分のサムネイルを返すレスポンスDTO。
 * <p>
 * ページ番号は画面・APIと同じ1始まりで表す。画像はdata URIとして返し、画面側が {@code img} の
 * {@code src} へそのまま渡せるようにする。
 */
@Schema(description = "PDF1ページ分のサムネイル。")
@Getter
@Setter
public class PdfThumbnailPageResponse {
	/** 1始まりのページ番号。 */
	@Schema(description = "1始まりのページ番号。", example = "1")
	@JsonProperty("pageNumber")
	private Integer pageNumber;

	/** data URI形式のサムネイル画像。 */
	@Schema(description = "data URI形式のサムネイル画像。", example = "data:image/png;base64,iVBORw0KGgo=")
	@JsonProperty("dataUri")
	private String dataUri;

	/** サムネイル画像の幅（px）。 */
	@Schema(description = "サムネイル画像の幅（px）。", example = "331")
	@JsonProperty("width")
	private Integer width;

	/** サムネイル画像の高さ（px）。 */
	@Schema(description = "サムネイル画像の高さ（px）。", example = "468")
	@JsonProperty("height")
	private Integer height;
}
