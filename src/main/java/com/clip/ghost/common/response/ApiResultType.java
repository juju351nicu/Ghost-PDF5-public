package com.clip.ghost.common.response;

/**
 * JSON成功レスポンスの結果種別。
 * <p>
 * 失敗は {@code ErrorResponse} が受け持つため、ここにERRORは持たせない。
 * この種別はHTTP statusではなく「成功の中での伝えたいことの強さ」を表す。
 */
public enum ApiResultType {
	/** 通常の成功。 */
	INFO,

	/** 成功だが注意喚起を伴う場合（部分的な失敗を含む成功など）。 */
	WARNING
}
