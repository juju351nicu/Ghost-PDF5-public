package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.validation.CheckNumericList;
import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PDFページ画像化条件を受け取るリクエストフォーム。
 * <p>
 * multipartで受け取ったPDFのページを画像化し、ページごとに1ファイルのZIPで返す。
 * {@code imagePages} を省略すると全ページを画像化する。
 * <p>
 * {@code dpi} の上限はannotationで固定せず、{@code ghost.pdf.image.max-dpi} の設定値で判定する。
 * 実行環境のヒープに依存する値をコードへ埋めると、環境ごとに調整できなくなるため。
 */
@Schema(description = "PDFページ画像化条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class PdfImagesRequest {
	/** 画像化元になるPDFファイル。 */
	@Schema(description = "画像化元になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/** 出力する画像形式。 */
	@Schema(description = "出力する画像形式。", example = "PNG", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
			"PNG", "JPG", "TIFF", "BMP" })
	@JsonProperty("format")
	@NotNull(message = "画像形式を入れてください。")
	private PdfImageFormat format;

	/** 画像化する解像度（DPI）。省略時は設定の既定値を使う。 */
	@Schema(description = "画像化する解像度（DPI）。省略時はサーバー設定の既定値を使います。上限もサーバー設定で決まります。", example = "150")
	@JsonProperty("dpi")
	@Min(value = 1, message = "解像度は1以上で入力してください。")
	private Integer dpi;

	/** 画像化対象ページ番号のリスト。画面・リクエスト上は1始まりで扱う。省略時は全ページ。 */
	@Schema(description = "画像化対象ページ番号のリスト。画面・リクエスト上は1始まりで扱います。省略時は全ページを画像化します。", example = "[1,3,5]")
	@JsonProperty("imagePages")
	@CheckNumericList
	private List<Integer> imagePages;

	/** パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しない。 */
	@Schema(description = "パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しません。")
	@JsonProperty("password")
	private String password;
}
