package com.clip.ghost.exportcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.exportcontent.logic.CsvWriterSupport;
import com.clip.ghost.markdowncontent.dto.MarkdownFileResponse;
import com.clip.ghost.markdowncontent.service.MarkdownDocumentService;

/**
 * {@link MarkdownFileCsvService} のCSV行組み立てとレスポンス整形を検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class MarkdownFileCsvServiceTest {

	@Mock
	private MarkdownDocumentService markdownDocumentService;

	private MarkdownFileCsvService csvService;

	@BeforeEach
	void setup() {
		// CSV組み立ては実物を使う。ここで見たいのは「一覧の内容が欠けずにCSVへ出るか」のため。
		csvService = new MarkdownFileCsvService(markdownDocumentService, new CsvWriterSupport());
	}

	@Test
	@DisplayName("保存済みMarkdownの一覧をヘッダー付きCSVとして返す")
	void exportMarkdownFileListReturnsCsvWithHeader() throws IOException {
		stubFileList(createFile("design-note.md", 1024L, 42L, "2026-09-13 20:00:00"));

		ResponseEntity<Resource> result = csvService.exportMarkdownFileList(false);

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("markdown-files.csv", result.getHeaders().getContentDisposition().getFilename());
		String csv = readBody(result);
		assertTrue(csv.contains("ファイル名,サイズ(byte),行数,更新日時"));
		assertTrue(csv.contains("design-note.md,1024,42,2026-09-13 20:00:00"));
	}

	@Test
	@DisplayName("カンマを含むファイル名でも列がずれない")
	void exportMarkdownFileListEscapesCommaInFileName() throws IOException {
		stubFileList(createFile("a,b.md", 1L, 1L, "2026-09-13 20:00:00"));

		// カンマを含む値だけは引用符で囲まれる。ここが崩れると列がずれる。
		assertTrue(readBody(csvService.exportMarkdownFileList(false)).contains("\"a,b.md\",1,1,"));
	}

	@Test
	@DisplayName("サイズや行数が取得できなかった場合は空欄にする")
	void exportMarkdownFileListLeavesUnknownValuesEmpty() throws IOException {
		stubFileList(createFile("broken.md", null, null, null));

		assertTrue(readBody(csvService.exportMarkdownFileList(false)).contains("broken.md,,,"));
	}

	@Test
	@DisplayName("保存済みMarkdownが1件も無い場合はヘッダーだけのCSVを返す")
	void exportMarkdownFileListReturnsHeaderOnlyWhenNoFile() throws IOException {
		stubFileList();

		String csv = readBody(csvService.exportMarkdownFileList(false));

		assertEquals("ファイル名,サイズ(byte),行数,更新日時\r\n", csv);
	}

	@Test
	@DisplayName("BOM付きを指定した場合はUTF-8のバイト順マークから始まる")
	void exportMarkdownFileListAddsBomWhenRequested() throws IOException {
		stubFileList(createFile("design-note.md", 1L, 1L, "2026-09-13 20:00:00"));

		byte[] csv = csvService.exportMarkdownFileList(true).getBody().getContentAsByteArray();

		assertEquals((byte) 0xEF, csv[0]);
		assertEquals((byte) 0xBB, csv[1]);
		assertEquals((byte) 0xBF, csv[2]);
	}

	/**
	 * Markdown一覧Serviceが指定のファイル一覧を返すようにstubする。
	 *
	 * @param files 返却するファイル一覧
	 */
	private void stubFileList(MarkdownFileResponse... files) {
		doReturn(ResponseEntity.ok(ApiResult.of(List.of(files)))).when(markdownDocumentService).listMarkdownFiles();
	}

	/**
	 * 一覧レスポンスDTOを組み立てる。
	 *
	 * @param fileName         ファイル名
	 * @param byteSize         サイズ
	 * @param lineCount        行数
	 * @param lastModifiedTime 更新日時
	 * @return 一覧レスポンスDTO
	 */
	private MarkdownFileResponse createFile(String fileName, Long byteSize, Long lineCount,
			String lastModifiedTime) {
		MarkdownFileResponse file = new MarkdownFileResponse();
		file.setFileName(fileName);
		file.setByteSize(byteSize);
		file.setLineCount(lineCount);
		file.setLastModifiedTime(lastModifiedTime);
		return file;
	}

	/**
	 * レスポンス本文をUTF-8の文字列として読み出す。
	 *
	 * @param result CSVレスポンス
	 * @return CSV文字列
	 * @throws IOException 本文の読み込みに失敗した場合
	 */
	private String readBody(ResponseEntity<Resource> result) throws IOException {
		return new String(result.getBody().getContentAsByteArray(), StandardCharsets.UTF_8);
	}
}
