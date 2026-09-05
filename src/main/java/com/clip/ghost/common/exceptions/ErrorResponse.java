package com.clip.ghost.common.exceptions;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ControllerAdviceからFEへ返却する共通エラーレスポンス。
 */
@Schema(description = "ControllerAdviceからFEへ返却する共通エラーレスポンス。")
@NoArgsConstructor
@Getter
@Setter
public class ErrorResponse {
	/** 入力項目ごとのエラー一覧 */
	@Schema(description = "入力項目ごとのエラー一覧。")
	@JsonProperty("fieldErrors")
	private List<CustomFieldError> fieldErrors;
}
