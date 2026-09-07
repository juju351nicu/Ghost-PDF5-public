package com.clip.ghost.pdfcontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * ページ選択用サムネイルの生成元PDFを受け取るリクエストフォーム。
 * <p>
 * ページ単位のリクエストは受け付けない。サーバー側に文書セッションが無く、ページごとに呼ぶと
 * 同じPDFを何度もアップロードすることになるため、1リクエストで全ページ分を返す。
 */
@Schema(description = "ページ選択用サムネイルの生成元PDFを受け取るmultipartフォーム。")
@Getter
@Setter
public class PdfThumbnailRequest {
	/** サムネイルの生成元になるPDFファイル。 */
	@Schema(description = "サムネイルの生成元になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;
}
