package com.clip.ghost.pdfcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PDF分割対象ファイルを受け取るリクエストフォーム。
 * <p>
 * 初期仕様ではmultipartで受け取ったPDFを1ページずつ分割し、複数PDFをZIPで返す。
 */
@Schema(description = "PDF分割対象ファイルを受け取るmultipartフォーム。")
@Getter
@Setter
public class SplitPdfRequest {
	@Schema(description = "分割対象になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;
}
