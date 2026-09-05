package com.clip.ghost.common.utils;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jacksonを使ったJSON文字列変換とオブジェクト変換をまとめるユーティリティクラス。
 * <p>
 * 新規コードでは、失敗を戻り値で表せる {@code try} 系、または失敗時に例外で止める {@code OrThrow} 系を使用する。 失敗時に
 * {@code null} を返す曖昧なAPIは追加しない。既存互換が必要な場合も、呼び出し元の用途に合わせて明示的なメソッド名を選ぶ。
 */
public final class JsonUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);
	private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

	private JsonUtils() {
	}

	/**
	 * JSON化に成功した場合だけJSON文字列を返す。
	 * <p>
	 * 変換元がnull、または変換失敗時は {@link Optional#empty()} を返す。
	 *
	 * @param <T>     payloadの型
	 * @param payload JSON化する値
	 * @return JSON文字列。変換できない場合は空
	 */
	public static <T> Optional<String> tryToJson(T payload) {
		if (payload == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(toJsonOrThrow(payload));
		} catch (IllegalArgumentException e) {
			logConversionFailure("JSON文字列への変換に失敗しました。payloadType={}", e, payload.getClass().getName());
			return Optional.empty();
		}
	}

	/**
	 * JSON文字列を指定クラスへ変換できた場合だけ値を返す。
	 * <p>
	 * 入力不足、または変換失敗時は {@link Optional#empty()} を返す。
	 *
	 * @param <T>   変換先の型
	 * @param json  JSON文字列
	 * @param clazz 変換先クラス
	 * @return 変換後オブジェクト。変換できない場合は空
	 */
	public static <T> Optional<T> tryParse(String json, Class<T> clazz) {
		if (json == null || clazz == null) {
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(parseOrThrow(json, clazz));
		} catch (IllegalArgumentException e) {
			logConversionFailure("JSON文字列からクラスへの変換に失敗しました。clazz={}", e, clazz.getName());
			return Optional.empty();
		}
	}

	/**
	 * JSON文字列を指定TypeReferenceへ変換できた場合だけ値を返す。
	 * <p>
	 * 入力不足、または変換失敗時は {@link Optional#empty()} を返す。
	 *
	 * @param <T>          変換先の型
	 * @param json         JSON文字列
	 * @param valueTypeRef 変換先の型参照
	 * @return 変換後オブジェクト。変換できない場合は空
	 */
	public static <T> Optional<T> tryParse(String json, TypeReference<T> valueTypeRef) {
		if (json == null || valueTypeRef == null) {
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(parseOrThrow(json, valueTypeRef));
		} catch (IllegalArgumentException e) {
			logConversionFailure("JSON文字列からTypeReferenceへの変換に失敗しました。valueTypeRef={}", e, valueTypeRef);
			return Optional.empty();
		}
	}

	/**
	 * JSON文字列をJackson 3のTypeReferenceへ変換できた場合だけ値を返す。
	 * <p>
	 * 入力不足、または変換失敗時は {@link Optional#empty()} を返す。
	 *
	 * @param <T>          変換先の型
	 * @param json         JSON文字列
	 * @param valueTypeRef Jackson 3の変換先型参照
	 * @return 変換後オブジェクト。変換できない場合は空
	 */
	public static <T> Optional<T> tryParse(String json, tools.jackson.core.type.TypeReference<T> valueTypeRef) {
		if (json == null || valueTypeRef == null) {
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(parseOrThrow(json, valueTypeRef));
		} catch (IllegalArgumentException e) {
			logConversionFailure("JSON文字列からJackson 3 TypeReferenceへの変換に失敗しました。valueTypeRef={}", e,
					valueTypeRef);
			return Optional.empty();
		}
	}

	/**
	 * オブジェクトを指定クラスへ変換できた場合だけ値を返す。
	 * <p>
	 * 入力不足、または変換失敗時は {@link Optional#empty()} を返す。
	 *
	 * @param <T>     変換先の型
	 * @param payload 変換元オブジェクト
	 * @param clazz   変換先クラス
	 * @return 変換後オブジェクト。変換できない場合は空
	 */
	public static <T> Optional<T> tryConvertValue(Object payload, Class<T> clazz) {
		if (payload == null || clazz == null) {
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(convertValueOrThrow(payload, clazz));
		} catch (IllegalArgumentException e) {
			logConversionFailure("オブジェクトからクラスへの変換に失敗しました。payloadType={}, clazz={}", e, payload.getClass().getName(),
					clazz.getName());
			return Optional.empty();
		}
	}

	/**
	 * payloadをJSON文字列へ変換する。失敗時は例外を送出する。
	 *
	 * @param <T>     payloadの型
	 * @param payload JSON化する値
	 * @return JSON文字列
	 * @throws IllegalArgumentException payloadがnull、またはJSON化に失敗した場合
	 */
	public static <T> String toJsonOrThrow(T payload) {
		requireArgument(payload, "payload");
		try {
			return MAPPER.writeValueAsString(payload);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("JSON文字列への変換に失敗しました。", e);
		}
	}

	/**
	 * JSON文字列を指定クラスへ変換する。失敗時は例外を送出する。
	 *
	 * @param <T>   変換先の型
	 * @param json  JSON文字列
	 * @param clazz 変換先クラス
	 * @return 変換後オブジェクト
	 * @throws IllegalArgumentException 入力不足または変換失敗時
	 */
	public static <T> T parseOrThrow(String json, Class<T> clazz) {
		requireArgument(json, "json");
		requireArgument(clazz, "clazz");
		try {
			return MAPPER.readValue(json, clazz);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("JSON文字列からクラスへの変換に失敗しました。", e);
		}
	}

	/**
	 * JSON文字列を指定TypeReferenceへ変換する。失敗時は例外を送出する。
	 *
	 * @param <T>          変換先の型
	 * @param json         JSON文字列
	 * @param valueTypeRef 変換先の型参照
	 * @return 変換後オブジェクト
	 * @throws IllegalArgumentException 入力不足または変換失敗時
	 */
	public static <T> T parseOrThrow(String json, TypeReference<T> valueTypeRef) {
		requireArgument(json, "json");
		requireArgument(valueTypeRef, "valueTypeRef");
		try {
			// Jackson 2の公開型からJava Typeを取り出し、Jackson 3の型情報へ変換する。
			return MAPPER.readValue(json, MAPPER.constructType(valueTypeRef.getType()));
		} catch (JacksonException e) {
			throw new IllegalArgumentException("JSON文字列からTypeReferenceへの変換に失敗しました。", e);
		}
	}

	/**
	 * JSON文字列をJackson 3のTypeReferenceへ変換する。失敗時は例外を送出する。
	 *
	 * @param <T>          変換先の型
	 * @param json         JSON文字列
	 * @param valueTypeRef Jackson 3の変換先型参照
	 * @return 変換後オブジェクト
	 * @throws IllegalArgumentException 入力不足または変換失敗時
	 */
	public static <T> T parseOrThrow(String json, tools.jackson.core.type.TypeReference<T> valueTypeRef) {
		requireArgument(json, "json");
		requireArgument(valueTypeRef, "valueTypeRef");
		try {
			return MAPPER.readValue(json, valueTypeRef);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("JSON文字列からJackson 3 TypeReferenceへの変換に失敗しました。", e);
		}
	}

	/**
	 * オブジェクトを指定クラスへ変換する。失敗時は例外を送出する。
	 *
	 * @param <T>     変換先の型
	 * @param payload 変換元オブジェクト
	 * @param clazz   変換先クラス
	 * @return 変換後オブジェクト
	 * @throws IllegalArgumentException 入力不足または変換失敗時
	 */
	public static <T> T convertValueOrThrow(Object payload, Class<T> clazz) {
		requireArgument(payload, "payload");
		requireArgument(clazz, "clazz");
		try {
			return MAPPER.convertValue(payload, clazz);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("オブジェクトからクラスへの変換に失敗しました。", e);
		}
	}

	/**
	 * try系メソッドで握りつぶす変換失敗をログへ出力する。
	 * <p>
	 * warnには呼び出し元が状況を判断しやすい要約だけを出し、例外詳細はdebugへ分ける。
	 *
	 * @param message warnログのメッセージテンプレート
	 * @param e       変換失敗例外
	 * @param args    メッセージ埋め込み値
	 */
	private static void logConversionFailure(String message, IllegalArgumentException e, Object... args) {
		LOGGER.warn(message, args);
		LOGGER.debug("JSON変換失敗の詳細です。", e);
	}

	/**
	 * 必須引数が指定されていることを検証する。
	 *
	 * @param value 引数値
	 * @param name  引数名
	 */
	private static void requireArgument(Object value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " must not be null.");
		}
	}
}
