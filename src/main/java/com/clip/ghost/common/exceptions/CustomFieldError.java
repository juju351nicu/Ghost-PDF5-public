package com.clip.ghost.common.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FEへ返却する入力エラー1件分の情報。
 * <p>
 * 既存のエラーレスポンス形式に合わせ、JSONのフィールド名は {@link JsonProperty} で明示する。
 */
@Schema(description = "FEへ返却する入力エラー1件分の情報。")
@NoArgsConstructor
@Getter
@Setter
public class CustomFieldError {
	/** エラーコード */
	@Schema(description = "エラーコード。", example = "typeMismatch")
	@JsonProperty("errorCode")
	private String errorCode;

	/** バリデーション対象のフィールド */
	@Schema(description = "バリデーション対象のフィールド。", example = "originalFile")
	@JsonProperty("field")
	private String field;

	/** メッセージ */
	@Schema(description = "画面表示用メッセージ。", example = "ファイルを入れてください。")
	@JsonProperty("message")
	private String message;
}
