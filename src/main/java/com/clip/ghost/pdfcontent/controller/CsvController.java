package com.clip.ghost.pdfcontent.controller;

import java.io.IOException;
import java.io.PrintWriter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CSVダウンロードのサンプルエンドポイントを提供するコントローラー。
 * <p>
 * 既存URLとの互換性を保つため、PDF編集画面からは独立させつつ {@code /showCSV} と {@code /printCSV}
 * のパスは変更しない。
 */
@Hidden
@Controller
public class CsvController {
	private static final String RESULT_CSV_FILE_NAME = "result.csv";
	private static final String PRINT_CSV_FILE_NAME = "result.csv";
	private static final MediaType TEXT_CSV_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");
	private static final String SAMPLE_CSV_DATA = "\"サクランボ\",\"イチゴ\",\"ブドウ\",\"デコポン\",\"カキ\"\n"
			+ "\"リンゴ\",\"ナシ\",\"パイナップル\",\"メロン\",\"スイカ\"";
	private static final String[][] PRINT_CSV_ROWS = { { "0", "ダイナミック", "dynamic@example.com" },
			{ "1", "ホゲ", "hoge@example.com" }, { "2", "モゲ", "moge@example.com" }, { "3", "マゲ", "mage@example.com" },
			{ "4", "フゥ", "foo@example.com" }, { "5", "バァ", "bar@example.com" }, { "6", "グゥ", "goo@example.com" } };

	/**
	 * 固定のCSV文字列をファイルダウンロードとして返却する。
	 *
	 * @return CSV文字列のレスポンス
	 */
	@GetMapping("/showCSV")
	@ResponseBody
	public ResponseEntity<String> downloadSampleCsv() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + RESULT_CSV_FILE_NAME);
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return ResponseEntity.ok().headers(headers).contentType(TEXT_CSV_UTF8).body(SAMPLE_CSV_DATA);
	}

	/**
	 * 固定のCSV行データをレスポンスへ直接書き込む。
	 *
	 * @param response CSVを書き込むHTTPレスポンス
	 * @throws IOException レスポンスへの書き込みに失敗した場合
	 */
	@GetMapping("/printCSV")
	@ResponseBody
	public void printSampleCsv(HttpServletResponse response) throws IOException {
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + PRINT_CSV_FILE_NAME + "\"");

		PrintWriter writer = response.getWriter();
		writer.print(convertRowsToCsv(PRINT_CSV_ROWS));
		writer.flush();
	}

	/**
	 * 2次元配列の文字列をCSV形式に変換する。
	 *
	 * @param rows CSV化する行データ
	 * @return CSV文字列
	 */
	private String convertRowsToCsv(String[][] rows) {
		StringBuilder csv = new StringBuilder();
		for (String[] row : rows) {
			for (int columnIndex = 0; columnIndex < row.length; columnIndex++) {
				if (columnIndex == 0) {
					csv.append("\"");
				} else {
					csv.append("\",\"");
				}
				csv.append(row[columnIndex]);
				if (columnIndex == row.length - 1) {
					csv.append("\"\n");
				}
			}
		}
		return csv.toString();
	}
}
