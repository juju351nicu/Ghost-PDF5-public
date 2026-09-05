package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.validation.CheckNumericList;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PDFページ抽出条件を受け取るリクエストフォーム。
 * <p>
 * 既存のPDF操作APIと同じくmultipartで編集元PDFを受け取り、画面・API仕様の1始まりページ番号で抽出対象を指定する。
 */
@Schema(description = "PDFページ抽出条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class ExtractPdfRequest {
	/** 抽出元になるPDFファイル。 */
	@Schema(description = "抽出元になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/** 抽出対象ページ番号のリスト。画面・リクエスト上は1始まりで扱う。 */
	@Schema(description = "抽出対象ページ番号のリスト。画面・リクエスト上は1始まりで扱います。", example = "[1,3,5]", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("extractPages")
	@NotEmpty(message = "抽出ページを入れてください。")
	@CheckNumericList
	private List<Integer> extractPages;
}
