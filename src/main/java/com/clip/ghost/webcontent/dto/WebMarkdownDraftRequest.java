package com.clip.ghost.webcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.webcontent.enums.WebMarkdownDraftMode;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * HTMLファイルからMarkdown下書きを起こす条件を受け取るリクエストフォーム。
 * <p>
 * この段階ではURLを受け取らない。サーバーが任意の宛先へ接続する機能は
 * Stage 2（{@code POST /markdownDraftUrl}）として分けて追加する。
 */
@Schema(description = "HTMLファイルからMarkdown下書きを起こす条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class WebMarkdownDraftRequest {
	/** Markdown下書きへ変換するHTMLファイル。 */
	@Schema(description = "Markdown下書きへ変換するHTMLファイル。.html / .htm を指定します。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("htmlFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile htmlFile;

	/**
	 * 本文を絞り込むCSSセレクタ。
	 * <p>
	 * 未指定ならページ全体（{@code <body>}）が対象になる。想定外の箇所まで拾ってしまうページで、
	 * 利用者が {@code article} や {@code main} を指定して絞り込めるようにする任意項目。
	 */
	@Schema(description = "本文を絞り込むCSSセレクタ。未指定ならページ全体が対象です。", example = "article")
	@JsonProperty("selector")
	@Size(max = 200, message = "セレクタは200文字以内で入力してください。")
	private String selector;

	/**
	 * 出力モード。
	 * <p>
	 * 未指定なら本文だけ（{@code ARTICLE}）。既定値をここへ書かず、未指定のままService層で決めるのは、
	 * {@code PdfMarkdownDraftRequest} と同じ扱いにそろえるため。
	 */
	@Schema(description = "出力モード。ARTICLE（本文のみ・既定）／STRUCTURE（構造レポートのみ）／BOTH（両方）。", example = "ARTICLE")
	@JsonProperty("mode")
	private WebMarkdownDraftMode mode;
}
