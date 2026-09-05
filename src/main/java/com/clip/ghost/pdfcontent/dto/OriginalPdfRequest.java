package com.clip.ghost.pdfcontent.dto;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.validation.CheckNumericList;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 編集元PDFとPDF操作条件を受け取るリクエストフォーム。
 * <p>
 * プレビュー、ページ削除、差し込み処理で共通利用する。既存フロントエンドのフォーム項目名とmultipart requestの構造を維持するため、
 * フィールド名とJSON名は画面側との互換性を確認してから変更する。
 */
@Schema(description = "編集元PDFとPDF操作条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class OriginalPdfRequest {

	/** 旧フォーム互換のトークン項目。現行APIでは主にaccess-tokenヘッダーで検証する。 */
	@Schema(description = "旧フォーム互換のトークン項目。現行APIではaccess-tokenヘッダーを使用します。")
	@JsonProperty("token")
	private String token;

	/** プレビュー、削除、差し込みの編集元になるPDFファイル。 */
	@Schema(description = "プレビュー、削除、差し込みの編集元になるPDFファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("originalFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile originalFile;

	/** 削除対象ページ番号のリスト。画面・リクエスト上は1始まりで扱う。 */
	@Schema(description = "削除対象ページ番号のリスト。画面・リクエスト上は1始まりで扱います。", example = "[1,3]")
	@JsonProperty("originalDeletePages")
	@CheckNumericList
	private List<Integer> originalDeletePages;

	/** 差し込み・差し替え対象PDFの行情報リスト。 */
	@Schema(description = "差し込み・差し替え対象PDFの行情報リスト。")
	@JsonProperty("insertPdfForm")
	private List<@Valid InsertPdfRequest> insertPdfForm;
}
