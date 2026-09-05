package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * PDFプレビュー情報をFEへ返却するためのレスポンスDTO。
 * <p>
 * PDFの総ページ数、Base64化したPDF内容、画面表示用メッセージをまとめる。現在のPDF操作APIはbyte配列レスポンスが中心だが、
 * 既存互換のためこのDTOは維持する。
 */
@Schema(description = "PDFプレビュー情報をFEへ返却するための既存互換レスポンスDTO。")
@Getter
@Setter
public class GhostPdfResponse {
	/** PDFの総ページ数。 */
	@Schema(description = "PDFの総ページ数。", example = "3")
	@JsonProperty("totalPages")
	private Integer totalPages;

	/** Base64形式のPDF内容。 */
	@Schema(description = "Base64形式のPDF内容。")
	@JsonProperty("contents")
	private String contents;

	/** 画面表示用メッセージリスト。 */
	@Schema(description = "画面表示用メッセージリスト。")
	@JsonProperty("messageList")
	private List<String> messagesList;
}
