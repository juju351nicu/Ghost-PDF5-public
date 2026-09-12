package com.clip.ghost.pdfcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
	 * 値は {@link com.clip.ghost.pdfcontent.enums.PdfMarkdownDraftMode} と対応するが、型は {@code String} のまま維持する。
	 * enumで受けるとbindingの失敗が400のvalidation errorではなく別の形で表面化し、既存のエラー表示と揃わないため。
	 */
	@Schema(description = "変換モード。省略で従来動作（文字レイヤーのみ、外部APIを呼ばない）。"
			+ "AUTOは文字が無いページだけを画像化してOCR/vision補完。"
			+ "VISIONは文字レイヤーの有無に関係なく全ページを画像化して変換するため、ページ数分の外部API費用が発生します。"
			+ "小文字指定も受け付けます。", example = "AUTO", allowableValues = { "AUTO", "VISION" })
	@JsonProperty("mode")
	@Pattern(regexp = "(?i)^(AUTO|VISION)?$", message = "変換モードはAUTOまたはVISIONで指定してください。")
	private String mode;
}
