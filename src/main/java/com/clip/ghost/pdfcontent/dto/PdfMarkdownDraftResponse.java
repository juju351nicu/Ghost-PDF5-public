package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * アップロードされたPDFから生成したページ単位Markdown下書きを返すレスポンスDTO。
 * <p>
 * 全ページを連結したMarkdownとページ順の抽出テキストを返す。下書きは自動保存せず、利用者が確認・編集してから
 * 既存Markdown保存APIへ渡す前提とする。
 */
@Schema(description = "アップロードされたPDFから生成したページ単位Markdown下書き。")
@Getter
@Setter
public class PdfMarkdownDraftResponse {
	/** アップロード時の元ファイル名。 */
	@Schema(description = "アップロード時の元ファイル名。", example = "sample.pdf")
	@JsonProperty("fileName")
	private String fileName;

	/** アップロードファイルサイズ。 */
	@Schema(description = "アップロードファイルサイズ。", example = "123456")
	@JsonProperty("fileSize")
	private Long fileSize;

	/** PDFの総ページ数。 */
	@Schema(description = "PDFの総ページ数。", example = "2")
	@JsonProperty("pageCount")
	private Integer pageCount;

	/** 全ページをページ見出し付きで連結したMarkdown下書き。 */
	@Schema(description = "全ページをページ見出し付きで連結したMarkdown下書き。", example = "## Page 1\n\nfirst page")
	@JsonProperty("markdown")
	private String markdown;

	/** PDF順のページ単位テキスト抽出結果。 */
	@Schema(description = "PDF順のページ単位テキスト抽出結果。")
	@JsonProperty("pages")
	private List<PdfMarkdownDraftPageResponse> pages;
}
