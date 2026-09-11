package com.clip.ghost.common.response;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;

/**
 * JSON APIの成功レスポンス共通ラッパー。
 * <p>
 * 「成功したが伝えたいことがある」を、用途別Response DTOへ専用フィールドを足さずに返せるようにする。
 * 失敗は既存の {@code ErrorResponse}（{@code fieldErrors} 形式）が受け持つため、このクラスにERRORは混ぜない。
 * <p>
 * 生成は {@link #of(Object)} / {@link #of(Object, List)} / {@link #warning(Object, List)} / {@link #empty()}
 * のfactoryに限定し、public setterは持たせない。バイナリ（PDF / ZIP）やCSVレスポンスは包まない。
 *
 * @param <T> レスポンスデータの型
 */
@Schema(description = "JSON APIの成功レスポンス共通ラッパー。")
@Getter
public class ApiResult<T> {
	@Schema(description = "レスポンスデータ。データなしの場合のみnull。")
	private final T data;

	@Schema(description = "結果種別。通常はINFO、注意喚起を含む成功時はWARNING。")
	private final ApiResultType resultType;

	@Schema(description = "画面表示用メッセージ。未指定時は空リスト。")
	private final List<ApiMessage> messageList;

	/**
	 * 共通ラッパーを生成する。
	 * <p>
	 * {@code messageList} は呼び出し元のリスト変更に影響されないよう複製し、nullにしない。
	 *
	 * @param data        レスポンスデータ
	 * @param resultType  結果種別
	 * @param messageList 画面表示用メッセージ
	 */
	private ApiResult(T data, ApiResultType resultType, List<ApiMessage> messageList) {
		this.data = data;
		this.resultType = resultType;
		this.messageList = List.copyOf(messageList);
	}

	/**
	 * メッセージなしの成功レスポンスを生成する。
	 *
	 * @param <T>  レスポンスデータの型
	 * @param data レスポンスデータ
	 * @return 結果種別INFO、メッセージ空の共通ラッパー
	 */
	public static <T> ApiResult<T> of(T data) {
		return new ApiResult<>(data, ApiResultType.INFO, List.of());
	}

	/**
	 * 通知メッセージ付きの成功レスポンスを生成する。
	 * <p>
	 * 注意喚起ではなく単なるお知らせを添える場合に使う。注意喚起は {@link #warning(Object, List)} を使う。
	 *
	 * @param <T>         レスポンスデータの型
	 * @param data        レスポンスデータ
	 * @param messageList 画面表示用メッセージ
	 * @return 結果種別INFOの共通ラッパー
	 */
	public static <T> ApiResult<T> of(T data, List<ApiMessage> messageList) {
		return new ApiResult<>(data, ApiResultType.INFO, messageList);
	}

	/**
	 * 注意喚起を伴う成功レスポンスを生成する。
	 * <p>
	 * 部分的に失敗したが結果は返す場合に使う。何を注意すべきか伝えられないWARNINGは利用者の役に立たないため、
	 * メッセージ空での生成は許可しない。
	 *
	 * @param <T>         レスポンスデータの型
	 * @param data        レスポンスデータ
	 * @param messageList 画面表示用メッセージ。1件以上必要
	 * @return 結果種別WARNINGの共通ラッパー
	 * @throws IllegalArgumentException {@code messageList} がnullまたは空の場合
	 */
	public static <T> ApiResult<T> warning(T data, List<ApiMessage> messageList) {
		if (CollectionUtils.isEmpty(messageList)) {
			throw new IllegalArgumentException("messageList must not be empty for WARNING.");
		}
		return new ApiResult<>(data, ApiResultType.WARNING, messageList);
	}

	/**
	 * データなしの成功レスポンスを生成する。
	 *
	 * @return {@code data} がnull、結果種別INFO、メッセージ空の共通ラッパー
	 */
	public static ApiResult<Void> empty() {
		return new ApiResult<>(null, ApiResultType.INFO, List.of());
	}
}
