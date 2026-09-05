package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link CsvController} のCSVダウンロードレスポンス仕様を検証するテスト。
 */
class CsvControllerTest {
	private static final String REQUEST_PATH_SHOW_CSV = "/showCSV";
	private static final String REQUEST_PATH_PRINT_CSV = "/printCSV";
	private static final String CONTENT_TYPE_CSV_UTF_8 = "text/csv;charset=UTF-8";
	private static final String CONTENT_TYPE_CSV_UTF_8_WITH_SPACE = "text/csv; charset=UTF-8";
	private static final String DOWNLOAD_CONTENT_DISPOSITION = "attachment; filename=result.csv";
	private static final String PRINT_CONTENT_DISPOSITION = "attachment; filename=\"result.csv\"";
	private static final String SAMPLE_DOWNLOAD_VALUE = "サクランボ";
	private static final String SAMPLE_PRINT_VALUE = "dynamic@example.com";

	private MockMvc mockMvc;

	/**
	 * テスト対象コントローラーをMockMvcに設定する。
	 */
	@BeforeEach
	void setup() {
		mockMvc = MockMvcBuilders.standaloneSetup(new CsvController()).build();
	}

	@Test
	@DisplayName("固定CSV文字列をダウンロードレスポンスとして返す")
	void downloadSampleCsvReturnsCsvAttachment() throws Exception {
		MvcResult result = mockMvc.perform(get(REQUEST_PATH_SHOW_CSV).contentType(MediaType.APPLICATION_JSON_VALUE))
				.andReturn();

		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(CONTENT_TYPE_CSV_UTF_8, result.getResponse().getContentType());
		assertEquals(DOWNLOAD_CONTENT_DISPOSITION, result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
		assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(SAMPLE_DOWNLOAD_VALUE));
	}

	@Test
	@DisplayName("固定CSV行をレスポンスへ直接出力する")
	void printSampleCsvWritesCsvAttachment() throws Exception {
		MvcResult result = mockMvc.perform(get(REQUEST_PATH_PRINT_CSV).contentType(MediaType.APPLICATION_JSON_VALUE))
				.andReturn();

		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals(CONTENT_TYPE_CSV_UTF_8_WITH_SPACE, result.getResponse().getContentType());
		assertEquals(PRINT_CONTENT_DISPOSITION, result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
		assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(SAMPLE_PRINT_VALUE));
	}
}
