package com.clip.ghost.markdowncontent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 編集中のMarkdown本文からPDFを出力するリクエスト。
 * <p>
 * 保存を伴わないため、保存済みファイル名ではなく本文をそのまま受け取る。
 * 画面のMarkdown欄に入力中の内容を、保存しないままPDFで確認できるようにするための契約。
 */
@Schema(description = "Markdown本文からPDFを出力するリクエスト。")
@Getter
@Setter
public class MarkdownPdfRequest {
	/** PDFのファイル名とタイトルに使う名前。未指定時はサービス側の既定名を使う。 */
	@Schema(description = "PDFのファイル名。拡張子は .pdf へそろえます。未指定時はdocument.pdfになります。", example = "design-note.pdf")
	@JsonProperty("fileName")
	private String fileName;

	/** PDF化するMarkdown本文。 */
	@Schema(description = "PDF化するMarkdown本文。", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("content")
	@NotNull(message = "Markdown本文を入力してください。")
	private String content;
}
