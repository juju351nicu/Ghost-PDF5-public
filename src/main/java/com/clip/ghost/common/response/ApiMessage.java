package com.clip.ghost.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON成功レスポンスに載せる画面表示用メッセージ。
 * <p>
 * メッセージコード体系が必要になるまでは、{@code code} / {@code message} の最小構成に留める。
 *
 * @param code    メッセージコード。画面側の分岐やログ突き合わせに使う
 * @param message 画面表示用メッセージ。文書内容や資格情報は含めない
 */
@Schema(description = "JSON成功レスポンスに載せる画面表示用メッセージ。")
public record ApiMessage(
		@Schema(description = "メッセージコード。", example = "ocrPagePartiallyFailed") String code,
		@Schema(description = "画面表示用メッセージ。", example = "3ページの文字起こしに失敗しました。") String message) {
}
