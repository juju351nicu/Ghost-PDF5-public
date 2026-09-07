package com.clip.ghost.pdfcontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * PDFの1ページ分のテキスト抽出結果を返すレスポンスDTO。
 * <p>
 * ページ番号は画面・APIと同じ1始まりで表し、文字を抽出できないページも空文字の本文として保持する。
 */
@Schema(description = "PDFの1ページ分のテキスト抽出結果。")
@Getter
@Setter
public class PdfMarkdownDraftPageResponse {
	/** 1始まりのページ番号。 */
	@Schema(description = "1始まりのページ番号。", example = "1")
	@JsonProperty("pageNumber")
	private Integer pageNumber;

	/** PDFBoxで抽出し、改行を正規化したページ本文。 */
	@Schema(description = "PDFBoxで抽出し、改行を正規化したページ本文。", example = "ページ本文です。")
	@JsonProperty("text")
	private String text;

	/**
	 * 本文の取得元。文字レイヤーからは {@code TEXT}、画像化してOCR/vision変換したページは {@code OCR}、
	 * 画像化したが変換に失敗したページは {@code FAILED}。
	 */
	@Schema(description = "本文の取得元。TEXT（PDFの文字レイヤー）、OCR（画像化して外部変換）、FAILED（画像化したが変換に失敗）。", example = "TEXT")
	@JsonProperty("source")
	private String source;
}
