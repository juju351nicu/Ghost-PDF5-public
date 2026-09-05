package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * PDF結合対象ファイルを受け取るリクエストフォーム。
 * <p>
 * multipartで複数PDFを受け取り、リクエストされたファイル順のまま1つのPDFへ結合する。
 */
@Schema(description = "PDF結合対象ファイルを受け取るmultipartフォーム。")
@Getter
@Setter
public class MergePdfRequest {
	@Schema(description = "結合対象PDFファイルのリスト。送信順に結合します。", type = "array", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("mergeFiles")
	@NotEmpty(message = "結合するファイルを入れてください。")
	private List<MultipartFile> mergeFiles;
}
