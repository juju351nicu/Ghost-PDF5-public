package com.clip.ghost.exportcontent.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CsvWriterSupport} のCSV組み立てを検証するテスト。
 */
class CsvWriterSupportTest {
	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private final CsvWriterSupport csvWriterSupport = new CsvWriterSupport();

	@Test
	@DisplayName("ヘッダー行とデータ行をCRLF区切りのCSVへ書き出す")
	void writeCreatesCsvWithHeaderAndRows() {
		byte[] csv = csvWriterSupport.write(List.of("名前", "値"), List.of(List.of("A", "1"), List.of("B", "2")),
				false);

		// 引用符は必要なときだけ付ける（RFC 4180の標準的な形）。行末はCRLF。
		assertEquals("名前,値\r\nA,1\r\nB,2\r\n", toText(csv));
	}

	@Test
	@DisplayName("カンマ・ダブルクォート・改行を含む値を壊さずに書き出す")
	void writeEscapesSpecialCharacters() {
		byte[] csv = csvWriterSupport.write(List.of("値"),
				List.of(List.of("a,b"), List.of("c\"d"), List.of("e\nf")), false);

		String text = toText(csv);
		// カンマは引用符の中、ダブルクォートは2重化、改行はセル内に保たれる。
		// 引用符を省く書式でも、特殊文字を含む値は必ず囲まれる。
		assertTrue(text.contains("\"a,b\""));
		assertTrue(text.contains("\"c\"\"d\""));
		assertTrue(text.contains("\"e\nf\""));
	}

	@Test
	@DisplayName("BOMを付けるとUTF-8のバイト順マークが先頭に入る")
	void writeAddsBomWhenRequested() {
		byte[] csv = csvWriterSupport.write(List.of("名前"), List.of(List.of("A")), true);

		assertArrayEquals(UTF8_BOM, Arrays.copyOf(csv, UTF8_BOM.length));
	}

	@Test
	@DisplayName("BOMを付けない場合は先頭がそのままヘッダーになる")
	void writeOmitsBomWhenNotRequested() {
		byte[] csv = csvWriterSupport.write(List.of("名前"), List.of(List.of("A")), false);

		assertFalse(Arrays.equals(UTF8_BOM, Arrays.copyOf(csv, UTF8_BOM.length)));
		assertTrue(toText(csv).startsWith("名前"));
	}

	@Test
	@DisplayName("ヘッダーが空の場合はデータ行だけを書き出す")
	void writeOmitsHeaderWhenEmpty() {
		byte[] csv = csvWriterSupport.write(List.of(), List.of(List.of("A")), false);

		assertEquals("A\r\n", toText(csv));
	}

	@Test
	@DisplayName("データ行が空の場合はヘッダーだけを書き出す")
	void writeOutputsHeaderOnlyWhenNoRow() {
		byte[] csv = csvWriterSupport.write(List.of("名前"), List.of(), false);

		assertEquals("名前\r\n", toText(csv));
	}

	/**
	 * CSVのbyte配列をUTF-8の文字列へ変換する。
	 *
	 * @param csv CSVのbyte配列
	 * @return CSV文字列
	 */
	private String toText(byte[] csv) {
		return new String(csv, StandardCharsets.UTF_8);
	}
}
