package com.clip.ghost.pdfcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * ページ単位Markdown下書きの生成元PDFを受け取るリクエストフォーム。
 * <p>
 * 既存PDF APIと同じ {@code originalFile} 名でmultipartファイルを受け取る。下書き生成だけを目的とし、
 * ページ編集条件やMarkdown保存条件は持たない。
 */
@Schema(description = "ページ単位Markdown下書きの生成元PDFを受け取るmultipartフォーム。")
@Getter
@Setter
public class PdfMarkdownDraftRequest {
	/** Markdown下書きの生成元になるPDFファイル。 */
	@Schema(description = "Markdown下書きの生成元になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/**
	 * 変換モード。省略時は文字レイヤーだけを使う従来動作。
	 * {@code AUTO} を指定すると、文字を取得できないページを画像化して外部変換（OCR/vision）で補完する。
	 */
	@Schema(description = "変換モード。省略で従来動作（文字レイヤーのみ）。AUTOで文字が無いページを画像化してOCR/vision補完。", example = "AUTO")
	@JsonProperty("mode")
	private String mode;
}
