package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.validation.CheckPageRangeList;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PDF分割条件を受け取るリクエストフォーム。
 * <p>
 * multipartで受け取ったPDFを分割し、複数PDFをZIPで返す。{@code splitRanges} を指定すると範囲ごとに1ファイル、
 * 省略すると従来どおり1ページずつ分割する。
 * <p>
 * 範囲の入力形式は {@code ["1-5", "6-12"]} の文字列リストにする。{@code List<Integer>}（{@code [1,3,5]}）では
 * 「1-5を1ファイル」と「1ページずつ5ファイル」を区別できないため、既存の {@code extractPages} の形式は使えない。
 */
@Schema(description = "PDF分割条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class SplitPdfRequest {
	/** 分割対象になるPDFファイル。 */
	@Schema(description = "分割対象になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/** 範囲ごとに分割する場合のページ範囲。画面・リクエスト上は1始まりで扱う。 */
	@Schema(description = "範囲ごとに分割する場合のページ範囲。画面・リクエスト上は1始まりで扱います。省略時は1ページずつ分割します。", example = "[\"1-5\",\"6-12\"]")
	@JsonProperty("splitRanges")
	@CheckPageRangeList
	private List<String> splitRanges;
}
