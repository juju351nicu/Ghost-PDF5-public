package com.clip.ghost.officecontent.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link OfficeDocumentType} のコード値変換と拡張子判定を検証するテスト。
 */
class OfficeDocumentTypeTest {
	private static final String INVALID_KEY_MESSAGE = "Office形式はDOCX、XLSX、PPTXのいずれかで指定してください。";

	@Test
	@DisplayName("キー値からOffice形式enumを取得でき、小文字も受け付ける")
	void fromKeyReturnsDocumentTypeIgnoringCase() {
		assertEquals(OfficeDocumentType.DOCX, OfficeDocumentType.fromKey("DOCX"));
		assertEquals(OfficeDocumentType.XLSX, OfficeDocumentType.fromKey("xlsx"));
		assertEquals(OfficeDocumentType.PPTX, OfficeDocumentType.fromKey("Pptx"));
	}

	@Test
	@DisplayName("対象外のキー値の場合は説明付き例外を送出する")
	void fromKeyThrowsExceptionWhenKeyIsInvalid() {
		assertInvalidKey("DOC");
		assertInvalidKey("");
		assertInvalidKey(null);
	}

	@Test
	@DisplayName("全てのOffice形式がCodeEnumとしてコード値と表示名と拡張子を持つ")
	void allDocumentTypesHaveKeyValueAndExtension() {
		assertEquals(3, OfficeDocumentType.values().length);
		Arrays.stream(OfficeDocumentType.values()).forEach(type -> {
			assertEquals(type, OfficeDocumentType.fromKey(type.getKey()));
			assertFalse(type.getValue().isBlank());
			assertFalse(type.getFileExtension().isBlank());
		});
	}

	@Test
	@DisplayName("ファイル名の拡張子からOffice形式を判定し、大文字小文字を無視する")
	void fromFileNameResolvesTypeByExtension() {
		assertEquals(OfficeDocumentType.DOCX, OfficeDocumentType.fromFileName("設計書.docx"));
		assertEquals(OfficeDocumentType.XLSX, OfficeDocumentType.fromFileName("一覧.XLSX"));
		assertEquals(OfficeDocumentType.PPTX, OfficeDocumentType.fromFileName("資料.PptX"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "設計書.doc", "一覧.xls", "資料.ppt", "note.pdf", "画像.png", "拡張子なし", "" })
	@DisplayName("旧Office形式や対象外の拡張子はnullになる")
	void fromFileNameReturnsNullForUnsupportedExtension(String fileName) {
		assertNull(OfficeDocumentType.fromFileName(fileName));
	}

	@Test
	@DisplayName("JacksonはOffice形式をキー値でJSON変換する")
	void jacksonUsesKeyAsJsonValue() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		assertEquals("\"DOCX\"", objectMapper.writeValueAsString(OfficeDocumentType.DOCX));
		assertEquals(OfficeDocumentType.PPTX, objectMapper.readValue("\"PPTX\"", OfficeDocumentType.class));
	}

	/**
	 * 不正キーの場合に共通メッセージの例外が送出されることを検証する。
	 *
	 * @param key 不正なキー値
	 */
	private void assertInvalidKey(String key) {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> OfficeDocumentType.fromKey(key));
		assertEquals(INVALID_KEY_MESSAGE, exception.getMessage());
	}
}
