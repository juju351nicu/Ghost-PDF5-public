package com.clip.ghost.pdfcontent.dto;

import org.hibernate.validator.constraints.Range;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * 差し込み・差し替え対象PDFの1行分のフォーム。
 * <p>
 * フロントエンドの差し込み行ごとに生成される。ファイル未選択または空ファイルの行は、サービス層でPDF処理対象から除外する。
 */
@Schema(description = "差し込み・差し替え対象PDFの1行分のmultipartフォーム。")
@Getter
@Setter
public class InsertPdfRequest {
	/** 差し込み・差し替えを行うPDFファイル。 */
	@Schema(description = "差し込み・差し替えを行うPDFファイル。", type = "string", format = "binary")
	@JsonProperty("insertFile")
	private MultipartFile insertFile;

	/** 差し込み・差し替え対象のページ番号。1始まりで指定する。 */
	@Schema(description = "差し込み・差し替え対象のページ番号。1始まりで指定します。", minimum = "1", example = "1")
	@JsonProperty("insertPage")
	@Range(min = PdfConstants.START_PAGE, max = Integer.MAX_VALUE, message = "1以上の半角数字で入力してください")
	private Integer insertPage;

	/**
	 * 差し込み方法。
	 * <ul>
	 * <li>1: 対象ページの後に差し込む</li>
	 * <li>2: 対象ページと差し替える</li>
	 * <li>3: 最後のページに差し込む</li>
	 * </ul>
	 */
	@Schema(description = "差し込み方法。1: 対象ページの後に差し込む、2: 対象ページと差し替える、3: 最後のページに差し込む。", allowableValues = { "1", "2",
			"3" }, example = "1")
	@JsonProperty("insertOption")
	@Range(min = PdfConstants.OPTION_INSERT, max = PdfConstants.OPTION_LAST_INSERT, message = "1から3までの値を入れてください")
	private Integer insertOption;
}
