package com.clip.ghost.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * {@link JsonUtils} の単体テスト。
 */
class JsonUtilsTest {

	@Test
	@DisplayName("toJsonOrThrowはオブジェクトをJSON文字列に変換できる")
	void toJsonOrThrow_returnsJsonString() {
		SamplePayload payload = new SamplePayload("sample", 1);

		assertEquals("{\"name\":\"sample\",\"count\":1}", JsonUtils.toJsonOrThrow(payload));
	}

	@Test
	@DisplayName("tryToJsonは成功時にOptionalへJSON文字列を格納する")
	void tryToJson_returnsOptionalJsonString() {
		SamplePayload payload = new SamplePayload("日本語", 2);

		assertEquals(Optional.of("{\"name\":\"日本語\",\"count\":2}"), JsonUtils.tryToJson(payload));
	}

	@Test
	@DisplayName("parseOrThrowはJSON文字列をクラスに変換できる")
	void parseOrThrow_returnsPayload() {
		SamplePayload payload = JsonUtils.parseOrThrow("{\"name\":\"sample\",\"count\":1}", SamplePayload.class);

		assertEquals(new SamplePayload("sample", 1), payload);
	}

	@Test
	@DisplayName("tryParseは成功時にOptionalへ変換結果を格納する")
	void tryParse_returnsOptionalPayload() {
		SamplePayload payload = new SamplePayload("sample", 1);

		assertEquals(Optional.of(payload),
				JsonUtils.tryParse("{\"name\":\"sample\",\"count\":1}", SamplePayload.class));
	}

	@Test
	@DisplayName("parseOrThrowはJSON文字列をTypeReferenceで変換できる")
	void parseOrThrowWithTypeReference_returnsPayloadList() {
		List<SamplePayload> payloads = JsonUtils.parseOrThrow("[{\"name\":\"sample\",\"count\":1}]",
				new TypeReference<List<SamplePayload>>() {
				});

		assertEquals(List.of(new SamplePayload("sample", 1)), payloads);
	}

	@Test
	@DisplayName("tryParseはTypeReference変換成功時にOptionalへ変換結果を格納する")
	void tryParseWithTypeReference_returnsOptionalPayloadList() {
		SamplePayload payload = new SamplePayload("sample", 1);

		assertEquals(Optional.of(List.of(payload)),
				JsonUtils.tryParse("[{\"name\":\"sample\",\"count\":1}]", new TypeReference<List<SamplePayload>>() {
				}));
	}

	@Test
	@DisplayName("parseOrThrowはJSON文字列をJackson 3 TypeReferenceで変換できる")
	void parseOrThrowWithJackson3TypeReference_returnsPayloadList() {
		List<SamplePayload> payloads = JsonUtils.parseOrThrow("[{\"name\":\"sample\",\"count\":1}]",
				new tools.jackson.core.type.TypeReference<List<SamplePayload>>() {
				});

		assertEquals(List.of(new SamplePayload("sample", 1)), payloads);
	}

	@Test
	@DisplayName("tryParseはJackson 3 TypeReference変換成功時にOptionalへ変換結果を格納する")
	void tryParseWithJackson3TypeReference_returnsOptionalPayloadList() {
		SamplePayload payload = new SamplePayload("sample", 1);

		assertEquals(Optional.of(List.of(payload)), JsonUtils.tryParse("[{\"name\":\"sample\",\"count\":1}]",
				new tools.jackson.core.type.TypeReference<List<SamplePayload>>() {
				}));
	}

	@Test
	@DisplayName("convertValueOrThrowはMap由来のオブジェクトを指定クラスへ変換できる")
	void convertValueOrThrow_returnsPayload() {
		SamplePayload payload = JsonUtils.convertValueOrThrow(Map.of("name", "sample", "count", 1),
				SamplePayload.class);

		assertEquals(new SamplePayload("sample", 1), payload);
	}

	@Test
	@DisplayName("tryConvertValueは成功時にOptionalへ変換結果を格納する")
	void tryConvertValue_returnsOptionalPayload() {
		SamplePayload payload = new SamplePayload("sample", 1);

		assertEquals(Optional.of(payload),
				JsonUtils.tryConvertValue(Map.of("name", "sample", "count", 1), SamplePayload.class));
	}

	@Test
	@DisplayName("try系メソッドは入力不足または変換失敗時にOptional.emptyを返す")
	void tryMethods_returnEmptyWhenInputIsMissingOrConversionFails() {
		assertTrue(JsonUtils.tryToJson(null).isEmpty());
		assertTrue(JsonUtils.tryParse(null, SamplePayload.class).isEmpty());
		assertTrue(JsonUtils.tryParse("{invalid", SamplePayload.class).isEmpty());
		assertTrue(JsonUtils.tryParse("{}", (Class<SamplePayload>) null).isEmpty());
		assertTrue(JsonUtils.tryParse(null, new TypeReference<List<SamplePayload>>() {
		}).isEmpty());
		assertTrue(JsonUtils.tryParse("{invalid", new TypeReference<List<SamplePayload>>() {
		}).isEmpty());
		assertTrue(JsonUtils.tryParse(null, new tools.jackson.core.type.TypeReference<List<SamplePayload>>() {
		}).isEmpty());
		assertTrue(JsonUtils.tryParse("{invalid", new tools.jackson.core.type.TypeReference<List<SamplePayload>>() {
		}).isEmpty());
		assertTrue(JsonUtils.tryConvertValue(null, SamplePayload.class).isEmpty());
		assertTrue(JsonUtils.tryConvertValue(Map.of("name", "sample", "count", "not-number"), SamplePayload.class)
				.isEmpty());
	}

	@Test
	@DisplayName("tryToJsonはJSON化できない値をOptional.emptyにする")
	void tryToJson_returnsEmptyWhenSerializationFails() {
		assertTrue(JsonUtils.tryToJson(new SelfReferencingPayload()).isEmpty());
	}

	@Test
	@DisplayName("OrThrow系メソッドは入力不足または変換失敗時にIllegalArgumentExceptionを送出する")
	void orThrowMethods_throwIllegalArgumentExceptionWhenInputIsMissingOrConversionFails() {
		assertThrows(IllegalArgumentException.class, () -> JsonUtils.toJsonOrThrow(null));
		assertThrows(IllegalArgumentException.class, () -> JsonUtils.parseOrThrow(null, SamplePayload.class));
		assertThrows(IllegalArgumentException.class, () -> JsonUtils.parseOrThrow("{invalid", SamplePayload.class));
		assertThrows(IllegalArgumentException.class, () -> JsonUtils.parseOrThrow("{}", (Class<SamplePayload>) null));
		assertThrows(IllegalArgumentException.class,
				() -> JsonUtils.parseOrThrow(null, new TypeReference<List<SamplePayload>>() {
				}));
		assertThrows(IllegalArgumentException.class,
				() -> JsonUtils.parseOrThrow("{invalid", new TypeReference<List<SamplePayload>>() {
				}));
		assertThrows(IllegalArgumentException.class,
				() -> JsonUtils.parseOrThrow(null, new tools.jackson.core.type.TypeReference<List<SamplePayload>>() {
				}));
		assertThrows(IllegalArgumentException.class,
				() -> JsonUtils.parseOrThrow("{invalid", new tools.jackson.core.type.TypeReference<List<SamplePayload>>() {
				}));
		assertThrows(IllegalArgumentException.class, () -> JsonUtils.convertValueOrThrow(null, SamplePayload.class));
		assertThrows(IllegalArgumentException.class,
				() -> JsonUtils.convertValueOrThrow(Map.of("name", "sample"), null));
		assertThrows(IllegalArgumentException.class, () -> JsonUtils
				.convertValueOrThrow(Map.of("name", "sample", "count", "not-number"), SamplePayload.class));
	}

	/**
	 * JSON化失敗を発生させるための自己参照payload。
	 */
	private static final class SelfReferencingPayload {

		/**
		 * 自分自身を返すことでJacksonの自己参照検出を発生させる。
		 *
		 * @return 自分自身
		 */
		@SuppressWarnings("unused")
		public SelfReferencingPayload getSelf() {
			return this;
		}
	}

	/**
	 * JSON変換テスト用の単純なpayload。
	 *
	 * @param name  名前
	 * @param count 件数
	 */
	private record SamplePayload(String name, int count) {
	}
}
