package com.clip.ghost.exportcontent.logic;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import lombok.NoArgsConstructor;

/**
 * 行データをCSVのbyte配列へ変換する内部ロジック。
 * <p>
 * commons-csvへの依存はこのpackage（{@code exportcontent.logic}）に閉じ込め、Service層は
 * 文字列の2次元リストだけを渡す。この境界は {@code CodingConventionTest} が機械的に守る。
 * <p>
 * エスケープを自前で書かない。カンマ・ダブルクォート・改行を含む値の扱いは
 * 「だいたい合っている」実装が最も厄介で、壊れるのは決まって本番のデータになる。
 * <p>
 * CSVライブラリはopenCsvではなくcommons-csvを使う。openCsvは commons-beanutils 経由で
 * commons-collections 3.x を引き込み、本プロジェクトが統一している commons-collections4 と
 * 2系統がクラスパスに同居してしまう。ここで必要なのは書き出しだけで、openCsvの強みである
 * Bean⇔CSVマッピングを使わないため、推移依存を持たないcommons-csvのほうが釣り合う。
 */
@Component
@NoArgsConstructor
public class CsvWriterSupport {
	/**
	 * CSVの書式。
	 * <p>
	 * {@link CSVFormat#DEFAULT} は行末がCRLF（RFC 4180の標準）で、引用符は必要なときだけ付く。
	 * 全項目を引用符で囲む形にはしない。囲まない形もRFC 4180として正しく、Excelも同じように開ける。
	 */
	private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT;

	/** ExcelがUTF-8として開けるようにする先頭のバイト順マーク。 */
	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	/**
	 * ヘッダー行とデータ行をCSVのbyte配列へ変換する。
	 *
	 * @param header  ヘッダー行。空の場合はヘッダーを出力しない
	 * @param rows    データ行
	 * @param withBom UTF-8のバイト順マークを先頭へ付ける場合true
	 * @return CSVのbyte配列
	 */
	public byte[] write(List<String> header, List<List<String>> rows, boolean withBom) {
		String csv = buildCsv(header, rows);
		byte[] body = csv.getBytes(StandardCharsets.UTF_8);
		if (!withBom) {
			return body;
		}
		// ExcelはBOMが無いUTF-8のCSVをシステム既定のコードページで開き、日本語が化ける。
		// 化けた状態は利用者から見て「壊れたファイル」に見えるため、既定ではBOMを付ける。
		return ByteBuffer.allocate(UTF8_BOM.length + body.length).put(UTF8_BOM).put(body).array();
	}

	/**
	 * ヘッダー行とデータ行からCSV文字列を組み立てる。
	 * <p>
	 * ヘッダーは書式側（{@code CSVFormat} のheader指定）ではなく最初のレコードとして出す。
	 * 「ヘッダーを出さない」場合の扱いが書式の組み替えではなく分岐1つで済み、
	 * ヘッダーとデータ行がまったく同じ経路を通るためエスケープの差も生まれない。
	 *
	 * @param header ヘッダー行
	 * @param rows   データ行
	 * @return CSV文字列
	 */
	private String buildCsv(List<String> header, List<List<String>> rows) {
		try (StringWriter stringWriter = new StringWriter();
				CSVPrinter csvPrinter = new CSVPrinter(stringWriter, CSV_FORMAT)) {
			if (CollectionUtils.isNotEmpty(header)) {
				csvPrinter.printRecord(header);
			}
			for (List<String> row : CollectionUtils.emptyIfNull(rows)) {
				csvPrinter.printRecord(row);
			}
			csvPrinter.flush();
			return stringWriter.toString();
		} catch (IOException e) {
			// StringWriterへの書き込みは実際には失敗しない。検査例外を呼び出し側へ広げないためここで包む。
			throw new UncheckedIOException(e);
		}
	}
}
