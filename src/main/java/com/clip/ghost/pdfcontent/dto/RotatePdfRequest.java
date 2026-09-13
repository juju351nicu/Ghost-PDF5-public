package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.validation.CheckNumericList;
import com.clip.ghost.pdfcontent.enums.PdfRotation;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PDFページ回転条件を受け取るリクエストフォーム。
 * <p>
 * 既存のPDF操作APIと同じくmultipartで編集元PDFを受け取り、画面・API仕様の1始まりページ番号で回転対象を指定する。
 * {@code rotatePages} を省略した場合は全ページを回転する。ページを1枚ずつ指定させると、
 * 「全部を横向きにしたい」という最も多い用途で入力が長くなるため。
 */
@Schema(description = "PDFページ回転条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class RotatePdfRequest {
	/** 回転元になるPDFファイル。 */
	@Schema(description = "回転元になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/** 回転角。現在の回転角へ加算する相対回転として扱う。 */
	@Schema(description = "回転角。90、180、270のいずれかを指定します。現在の回転角へ加算する相対回転として扱います。", example = "90", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
			"90", "180", "270" })
	@JsonProperty("rotation")
	@NotNull(message = "回転角を入れてください。")
	private PdfRotation rotation;

	/** 回転対象ページ番号のリスト。画面・リクエスト上は1始まりで扱う。省略時は全ページ。 */
	@Schema(description = "回転対象ページ番号のリスト。画面・リクエスト上は1始まりで扱います。省略時は全ページを回転します。", example = "[1,3,5]")
	@JsonProperty("rotatePages")
	@CheckNumericList
	private List<Integer> rotatePages;

	/** パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しない。 */
	@Schema(description = "パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しません。")
	@JsonProperty("password")
	private String password;
}
