package com.clip.ghost.pdfcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.enums.PdfMarkdownDraftMode;
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
	 * {@code VISION} を指定すると、文字レイヤーの有無に関係なく全ページを画像化して変換する。
	 * <p>
	 * APIが受け取る文字列は従来どおりで、コード値からenumへの変換は
	 * {@link com.clip.ghost.common.converter.StringToCodeEnumConverterFactory} が行う。
	 * 大文字小文字は無視し、未指定は {@code null}（従来動作）になる。
	 */
	@Schema(description = "変換モード。省略で従来動作（文字レイヤーのみ、外部APIを呼ばない）。"
			+ "AUTOは文字が無いページだけを画像化してOCR/vision補完。"
			+ "VISIONは文字レイヤーの有無に関係なく全ページを画像化して変換するため、ページ数分の外部API費用が発生します。"
			+ "小文字指定も受け付けます。", type = "string", example = "AUTO", allowableValues = { "AUTO", "VISION" })
	@JsonProperty("mode")
	private PdfMarkdownDraftMode mode;
}
