package com.clip.ghost.pdfcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.enums.SearchablePdfMode;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 検索可能PDF（OCRサンドイッチPDF）生成条件を受け取るリクエストフォーム。
 * <p>
 * 既存のPDF操作APIと同じくmultipartで編集元PDFを受け取る。{@code mode}を省略した場合は
 * {@link SearchablePdfMode#AUTO}として扱う。
 */
@Schema(description = "検索可能PDF生成条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class SearchablePdfRequest {
	/** OCR対象になるPDFファイル。 */
	@Schema(description = "OCR対象になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/**
	 * OCR対象ページを決める変換モード。省略時は{@code AUTO}。
	 * <p>
	 * {@code AUTO}は文字レイヤーが無いページだけ、{@code FORCE_OCR}は文字レイヤーの有無に関係なく
	 * 全ページをOCR対象にする。
	 */
	@Schema(description = "OCR対象ページを決める変換モード。省略時はAUTO。AUTOは文字レイヤーが無いページだけ、"
			+ "FORCE_OCRは全ページをOCR対象にします。", type = "string", example = "AUTO", allowableValues = { "AUTO",
					"FORCE_OCR" })
	@JsonProperty("mode")
	private SearchablePdfMode mode;

	/** パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しない。 */
	@Schema(description = "パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しません。")
	@JsonProperty("password")
	private String password;
}
