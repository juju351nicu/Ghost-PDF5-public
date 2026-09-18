package com.clip.ghost.webcontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Webページ取り込みの結果を返すレスポンスDTO。
 * <p>
 * サーバー側では保存しない。結果はMarkdownメモ欄へ返すだけで、保存は利用者が
 * 既存のMarkdown保存APIを実行したときだけ行う。
 */
@Schema(description = "Webページ取り込みで起こしたMarkdown下書き。")
@Getter
@Setter
public class WebMarkdownDraftResponse {
	/** 起こしたMarkdown本文。 */
	@Schema(description = "起こしたMarkdown本文。", example = "# ページタイトル\n\n- 取得元: page.html")
	@JsonProperty("markdown")
	private String markdown;

	/** 取り込んだページのタイトル。 */
	@Schema(description = "取り込んだページのタイトル。判定できない場合は空文字。", example = "ページタイトル")
	@JsonProperty("title")
	private String title;

	/**
	 * 取得元URL。
	 * <p>
	 * HTMLファイルのアップロード（{@code POST /markdownDraftHtml}）ではURLが存在しないためnullになる。
	 * Stage 2（URL取得）で実際に取得したURLを入れる。
	 */
	@Schema(description = "取得元URL。HTMLファイルのアップロードではnull。", example = "https://example.com/article")
	@JsonProperty("sourceUrl")
	private String sourceUrl;

	/** 出力文字数の上限で切り落としたか。 */
	@Schema(description = "出力文字数の上限で切り落とした場合true。", example = "false")
	@JsonProperty("truncated")
	private Boolean truncated;
}
