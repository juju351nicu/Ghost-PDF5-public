package com.clip.ghost.officecontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.officecontent.dto.OfficeMarkdownRequest;
import com.clip.ghost.officecontent.dto.OfficeMarkdownResponse;
import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.logic.OfficeMarkdownLogic;

/**
 * {@link OfficeMarkdownService} の形式判定とレスポンス組み立てを検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class OfficeMarkdownServiceTest {

	@Mock
	private OfficeMarkdownLogic officeMarkdownLogic;

	@InjectMocks
	private OfficeMarkdownService officeMarkdownService;

	@ParameterizedTest
	@CsvSource({ "設計書.docx, DOCX", "一覧.xlsx, XLSX", "資料.pptx, PPTX" })
	@DisplayName("拡張子から判定した形式でLogicを呼び、判定結果をレスポンスへ含める")
	void generateMarkdownResolvesDocumentTypeByExtension(String fileName, String expectedType) {
		doReturn("# 本文").when(officeMarkdownLogic).readMarkdown(any(), any());

		ResponseEntity<ApiResult<OfficeMarkdownResponse>> result = officeMarkdownService
				.generateMarkdown(createRequest(fileName));

		verify(officeMarkdownLogic, times(1)).readMarkdown(any(),
				eq(OfficeDocumentType.fromKey(expectedType)));
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals(expectedType, result.getBody().getData().getDocumentType());
		assertEquals("# 本文", result.getBody().getData().getMarkdown());
		assertEquals(fileName, result.getBody().getData().getFileName());
	}

	@ParameterizedTest
	@CsvSource({ "設計書.doc", "一覧.xls", "資料.ppt", "メモ.txt" })
	@DisplayName("対応していない拡張子はLogicを呼ばずOfficeInputExceptionになる")
	void generateMarkdownThrowsWhenExtensionIsNotSupported(String fileName) {
		OfficeMarkdownRequest form = createRequest(fileName);

		OfficeInputException exception = assertThrows(OfficeInputException.class,
				() -> officeMarkdownService.generateMarkdown(form));

		assertEquals(fileName, exception.getFileName());
		verify(officeMarkdownLogic, never()).readMarkdown(any(), any());
	}

	/**
	 * Office文書からのMarkdown生成リクエストを生成する。
	 *
	 * @param fileName ファイル名
	 * @return Markdown生成リクエスト
	 */
	private OfficeMarkdownRequest createRequest(String fileName) {
		OfficeMarkdownRequest form = new OfficeMarkdownRequest();
		form.setOfficeFile(new MockMultipartFile("officeFile", fileName, "application/octet-stream",
				"contents".getBytes(StandardCharsets.UTF_8)));
		return form;
	}
}
