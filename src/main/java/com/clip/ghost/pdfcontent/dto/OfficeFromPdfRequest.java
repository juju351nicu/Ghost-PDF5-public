package com.clip.ghost.pdfcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PDFからOffice文書を作る条件を受け取るリクエストフォーム。
 * <p>
 * 出力形式は利用者に選ばせる。入力がPDF1種類なので拡張子から決められず、
 * かつ「Wordで直したい」「Excelへ貼りたい」といった目的が利用者側にしかないため。
 */
@Schema(description = "PDFからOffice文書を作る条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class OfficeFromPdfRequest {
	/** 変換元になるPDFファイル。 */
	@Schema(description = "変換元になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/** 出力するOffice形式。 */
	@Schema(description = "出力するOffice形式。", example = "DOCX", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
			"DOCX", "XLSX", "PPTX" })
	@JsonProperty("format")
	@NotNull(message = "出力形式を入れてください。")
	private OfficeDocumentType format;

	/** パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しない。 */
	@Schema(description = "パスワードで保護されたPDFを開くためのパスワード。保護されていない場合は指定しません。")
	@JsonProperty("password")
	private String password;
}
