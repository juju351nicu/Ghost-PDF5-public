package com.clip.ghost.common.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import com.clip.ghost.pdfcontent.enums.PdfInsertOption;
import com.clip.ghost.pdfcontent.enums.PdfMarkdownDraftMode;

/**
 * {@link StringToCodeEnumConverterFactory} のコード値変換を検証するテスト。
 * <p>
 * multipartフォームのbindingはJacksonを通らないため、ここが「APIが受け取るコード値」を守る唯一の場所になる。
 */
class StringToCodeEnumConverterFactoryTest {

	private final StringToCodeEnumConverterFactory factory = new StringToCodeEnumConverterFactory();

	@Test
	@DisplayName("文字列のコード値から変換モードenumへ変換する")
	void convertsStringKeyToMarkdownDraftMode() {
		Converter<String, PdfMarkdownDraftMode> converter = factory.getConverter(PdfMarkdownDraftMode.class);

		assertEquals(PdfMarkdownDraftMode.AUTO, converter.convert("AUTO"));
		assertEquals(PdfMarkdownDraftMode.VISION, converter.convert("VISION"));
	}

	@Test
	@DisplayName("大文字小文字と前後の空白を無視して変換する")
	void convertsIgnoringCaseAndSurroundingSpaces() {
		Converter<String, PdfMarkdownDraftMode> converter = factory.getConverter(PdfMarkdownDraftMode.class);

		// enum化前のStrings.CI.equalsによる判定を維持する。mode=autoを送っていた利用者を壊さない。
		assertEquals(PdfMarkdownDraftMode.VISION, converter.convert("vision"));
		assertEquals(PdfMarkdownDraftMode.AUTO, converter.convert(" Auto "));
	}

	@Test
	@DisplayName("数値のコード値から差し込み方法enumへ変換する")
	void convertsNumericKeyToInsertOption() {
		Converter<String, PdfInsertOption> converter = factory.getConverter(PdfInsertOption.class);

		assertEquals(PdfInsertOption.INSERT, converter.convert("1"));
		assertEquals(PdfInsertOption.REPLACE, converter.convert("2"));
		assertEquals(PdfInsertOption.LAST_INSERT, converter.convert("3"));
	}

	@Test
	@DisplayName("未入力は未指定としてnullを返す")
	void returnsNullWhenSourceIsBlank() {
		Converter<String, PdfInsertOption> insertOptionConverter = factory.getConverter(PdfInsertOption.class);
		Converter<String, PdfMarkdownDraftMode> modeConverter = factory.getConverter(PdfMarkdownDraftMode.class);

		// 未指定時の既定値はservice層が決めるため、変換は「値が無い」ことだけを伝える。
		assertNull(insertOptionConverter.convert(""));
		assertNull(insertOptionConverter.convert("   "));
		assertNull(modeConverter.convert(""));
	}

	@Test
	@DisplayName("対応しないコード値はenum自身の説明付きで例外にする")
	void throwsWithInvalidKeyMessageWhenKeyIsUnknown() {
		Converter<String, PdfMarkdownDraftMode> modeConverter = factory.getConverter(PdfMarkdownDraftMode.class);
		Converter<String, PdfInsertOption> insertOptionConverter = factory.getConverter(PdfInsertOption.class);

		IllegalArgumentException modeException = assertThrows(IllegalArgumentException.class,
				() -> modeConverter.convert("FOO"));
		IllegalArgumentException insertOptionException = assertThrows(IllegalArgumentException.class,
				() -> insertOptionConverter.convert("9"));

		// 画面へ出すメッセージは英語の内部表現にせず、値域を知っているenumの説明を使う。
		assertEquals(PdfMarkdownDraftMode.AUTO.getInvalidKeyMessage(), modeException.getMessage());
		assertEquals(PdfInsertOption.INSERT.getInvalidKeyMessage(), insertOptionException.getMessage());
	}
}
