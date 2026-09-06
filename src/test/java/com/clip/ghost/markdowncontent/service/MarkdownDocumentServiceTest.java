package com.clip.ghost.markdowncontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.clip.ghost.markdowncontent.dto.MarkdownDeleteResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownDocumentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownFileResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewContentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownSaveRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownUpdateRequest;

/**
 * {@link MarkdownDocumentService} の単体テスト。
 */
class MarkdownDocumentServiceTest {

	@TempDir
	Path tempDir;

	/**
	 * シンボリックリンクを作成する。作成できない環境ではテストをskipする。
	 * <p>
	 * Windowsでは管理者権限または開発者モードが無いとシンボリックリンクを作成できず、
	 * {@link java.nio.file.FileSystemException} になる。CIや他マシンでの実行を妨げないよう、
	 * 権限が無い環境ではこのテストを失敗ではなくskip扱いにする。
	 *
	 * @param link   作成するシンボリックリンクのパス
	 * @param target リンク先のパス
	 * @throws IOException リンク作成以外のIOエラーが発生した場合
	 */
	private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
		try {
			Files.createSymbolicLink(link, target);
		} catch (FileSystemException | UnsupportedOperationException e) {
			Assumptions.abort("シンボリックリンクを作成できない環境のためskipします。reason=" + e.getMessage());
		}
	}

	@Test
	@DisplayName("Markdown本文をUTF-8で保存し、保存後メタデータを返す")
	void saveMarkdownWritesUtf8FileAndReturnsMetadata() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownSaveRequest request = createRequest("design-note", "# Title\nbody");

		ResponseEntity<MarkdownFileResponse> response = service.saveMarkdown(request);

		Path savedPath = tempDir.resolve("design-note.md");
		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(Files.isRegularFile(savedPath));
		assertEquals("# Title\nbody", Files.readString(savedPath, StandardCharsets.UTF_8));
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals(Files.size(savedPath), body.getByteSize());
		assertEquals(2L, body.getLineCount());
		assertNotNull(body.getLastModifiedTime());
	}

	@Test
	@DisplayName("Markdown保存はリクエスト未指定時にNullPointerExceptionにする")
	void saveMarkdownRejectsNullRequest() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		NullPointerException exception = assertThrows(NullPointerException.class, () -> service.saveMarkdown(null));

		assertEquals("request must not be null.", exception.getMessage());
	}

	@Test
	@DisplayName("Markdown保存は本文未指定時に空ファイルとして保存する")
	void saveMarkdownWritesEmptyFileWhenContentIsNull() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownSaveRequest request = createRequest("empty-content.md", null);

		ResponseEntity<MarkdownFileResponse> response = service.saveMarkdown(request);

		Path savedPath = tempDir.resolve("empty-content.md");
		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(Files.isRegularFile(savedPath));
		assertEquals("", Files.readString(savedPath, StandardCharsets.UTF_8));
		assertNotNull(body);
		assertEquals("empty-content.md", body.getFileName());
		assertEquals(0L, body.getByteSize());
		assertEquals(0L, body.getLineCount());
		assertNotNull(body.getLastModifiedTime());
	}

	@Test
	@DisplayName("保存ファイル名はパス要素を除外し、Markdown拡張子へそろえる")
	void saveMarkdownNormalizesFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownSaveRequest request = createRequest("../unsafe:name.txt", "body");

		ResponseEntity<MarkdownFileResponse> response = service.saveMarkdown(request);

		MarkdownFileResponse body = response.getBody();
		assertNotNull(body);
		assertEquals("unsafe_name.md", body.getFileName());
		assertTrue(Files.isRegularFile(tempDir.resolve("unsafe_name.md")));
	}

	@Test
	@DisplayName("Markdown保存はファイル名の前後空白を除去して保存する")
	void saveMarkdownTrimsFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownSaveRequest request = createRequest("  design-note.md  ", "body");

		ResponseEntity<MarkdownFileResponse> response = service.saveMarkdown(request);

		MarkdownFileResponse body = response.getBody();
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertTrue(Files.isRegularFile(tempDir.resolve("design-note.md")));
	}

	@Test
	@DisplayName("Markdown保存はファイル名未指定時に既定ファイル名で保存する")
	void saveMarkdownUsesDefaultFileNameWhenFileNameIsNull() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownSaveRequest request = createRequest(null, "body");

		ResponseEntity<MarkdownFileResponse> response = service.saveMarkdown(request);

		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertEquals("document.md", body.getFileName());
		assertTrue(Files.isRegularFile(tempDir.resolve("document.md")));
	}

	@Test
	@DisplayName("Markdown保存は空白ファイル名を既定ファイル名で保存する")
	void saveMarkdownUsesDefaultFileNameWhenFileNameIsBlank() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownSaveRequest request = createRequest("   ", "body");

		ResponseEntity<MarkdownFileResponse> response = service.saveMarkdown(request);

		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertEquals("document.md", body.getFileName());
		assertTrue(Files.isRegularFile(tempDir.resolve("document.md")));
	}

	@Test
	@DisplayName("Markdown保存は保存先ディレクトリが存在しない場合に作成する")
	void saveMarkdownCreatesStorageDirectoryWhenMissing() {
		Path storageDirectory = tempDir.resolve("missing").resolve("markdown");
		MarkdownDocumentService service = new MarkdownDocumentService(storageDirectory.toString());
		MarkdownSaveRequest request = createRequest("design-note.md", "body");

		ResponseEntity<MarkdownFileResponse> response = service.saveMarkdown(request);

		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(Files.isDirectory(storageDirectory));
		assertTrue(Files.isRegularFile(storageDirectory.resolve("design-note.md")));
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
	}

	@Test
	@DisplayName("Markdown保存はシンボリックリンク先を上書きしない")
	void saveMarkdownRejectsSymbolicLinkTarget() throws IOException {
		Path storageDirectory = Files.createDirectories(tempDir.resolve("storage"));
		Path externalPath = tempDir.resolve("external.md");
		Files.writeString(externalPath, "external", StandardCharsets.UTF_8);
		createSymbolicLinkOrSkip(storageDirectory.resolve("linked.md"), externalPath);
		MarkdownDocumentService service = new MarkdownDocumentService(storageDirectory.toString());
		MarkdownSaveRequest request = createRequest("linked.md", "overwritten");

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.saveMarkdown(request));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		assertEquals("external", Files.readString(externalPath, StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("Markdown一覧は保存先直下のMarkdownファイルだけをファイル名順で返す")
	void listMarkdownFilesReturnsDirectMarkdownFilesSortedByName() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Files.writeString(tempDir.resolve("b.md"), "b", StandardCharsets.UTF_8);
		Files.writeString(tempDir.resolve("a.MD"), "a", StandardCharsets.UTF_8);
		Files.writeString(tempDir.resolve("memo.txt"), "memo", StandardCharsets.UTF_8);
		Path nestedDir = Files.createDirectories(tempDir.resolve("nested"));
		Files.writeString(nestedDir.resolve("nested.md"), "nested", StandardCharsets.UTF_8);
		createSymbolicLinkOrSkip(tempDir.resolve("linked.md"), nestedDir.resolve("nested.md"));

		ResponseEntity<List<MarkdownFileResponse>> response = service.listMarkdownFiles();

		List<MarkdownFileResponse> files = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(files);
		assertEquals(List.of("a.MD", "b.md"), files.stream().map(MarkdownFileResponse::getFileName).toList());
		assertTrue(files.stream().allMatch(file -> file.getByteSize() != null));
		assertTrue(files.stream().allMatch(file -> file.getLastModifiedTime() != null));
	}

	@Test
	@DisplayName("Markdown一覧は保存先ディレクトリが存在しない場合に空リストを返す")
	void listMarkdownFilesReturnsEmptyWhenStorageDirectoryDoesNotExist() {
		Path storageDirectory = tempDir.resolve("missing").resolve("markdown");
		MarkdownDocumentService service = new MarkdownDocumentService(storageDirectory.toString());

		ResponseEntity<List<MarkdownFileResponse>> response = service.listMarkdownFiles();

		List<MarkdownFileResponse> files = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(files);
		assertTrue(files.isEmpty());
		assertFalse(Files.exists(storageDirectory));
	}

	@Test
	@DisplayName("Markdown本文取得は保存先直下のMarkdown本文とメタデータを返す")
	void getMarkdownFileReturnsContentAndMetadata() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "# Title\nbody", StandardCharsets.UTF_8);

		ResponseEntity<MarkdownDocumentResponse> response = service.getMarkdownFile("design-note.md");

		MarkdownDocumentResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals("# Title\nbody", body.getContent());
		assertEquals(Files.size(markdownPath), body.getByteSize());
		assertEquals(2L, body.getLineCount());
		assertNotNull(body.getLastModifiedTime());
	}

	@Test
	@DisplayName("Markdown本文取得はファイル名の前後空白を除去して読み込む")
	void getMarkdownFileTrimsFileName() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "body", StandardCharsets.UTF_8);

		ResponseEntity<MarkdownDocumentResponse> response = service.getMarkdownFile("  design-note.md  ");

		MarkdownDocumentResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals("body", body.getContent());
	}

	@Test
	@DisplayName("Markdown本文取得は空白ファイル名を400にする")
	void getMarkdownFileRejectsBlankFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.getMarkdownFile(null)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.getMarkdownFile("   ")).getStatusCode());
	}

	@Test
	@DisplayName("Markdown本文取得はパス要素やMarkdown以外の拡張子を400にする")
	void getMarkdownFileRejectsInvalidFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.getMarkdownFile("../secret.md"))
						.getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.getMarkdownFile("memo.txt")).getStatusCode());
	}

	@Test
	@DisplayName("Markdown本文取得は存在しないMarkdownファイルを404にする")
	void getMarkdownFileReturnsNotFoundWhenFileDoesNotExist() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.getMarkdownFile("missing.md"));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	@Test
	@DisplayName("Markdown本文取得はシンボリックリンクを404にする")
	void getMarkdownFileReturnsNotFoundForSymbolicLink() throws IOException {
		Path storageDirectory = Files.createDirectories(tempDir.resolve("storage"));
		Path externalPath = tempDir.resolve("external.md");
		Files.writeString(externalPath, "external", StandardCharsets.UTF_8);
		createSymbolicLinkOrSkip(storageDirectory.resolve("linked.md"), externalPath);
		MarkdownDocumentService service = new MarkdownDocumentService(storageDirectory.toString());

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.getMarkdownFile("linked.md"));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	@Test
	@DisplayName("Markdownプレビューは保存済みMarkdownをsanitize済みHTMLへ変換する")
	void previewMarkdownFileReturnsSanitizedHtmlAndMetadata() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath,
				"# Title\n\n**bold**\n\n<script>alert('x')</script>\n\n[bad](javascript:alert(1))",
				StandardCharsets.UTF_8);

		ResponseEntity<MarkdownPreviewResponse> response = service.previewMarkdownFile("design-note.md");

		MarkdownPreviewResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals(Files.size(markdownPath), body.getByteSize());
		assertEquals(7L, body.getLineCount());
		assertNotNull(body.getLastModifiedTime());
		assertTrue(body.getHtml().contains("<h1>Title</h1>"));
		assertTrue(body.getHtml().contains("<strong>bold</strong>"));
		assertFalse(body.getHtml().contains("<script>"));
		assertFalse(body.getHtml().contains("javascript:"));
	}

	@Test
	@DisplayName("Markdownプレビューはファイル名の前後空白を除去して読み込む")
	void previewMarkdownFileTrimsFileName() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "# Title", StandardCharsets.UTF_8);

		ResponseEntity<MarkdownPreviewResponse> response = service.previewMarkdownFile("  design-note.md  ");

		MarkdownPreviewResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertTrue(body.getHtml().contains("<h1>Title</h1>"));
	}

	@Test
	@DisplayName("Markdownプレビューは空白ファイル名を400にする")
	void previewMarkdownFileRejectsBlankFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.previewMarkdownFile(null)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.previewMarkdownFile("   ")).getStatusCode());
	}

	@Test
	@DisplayName("Markdownプレビューはパス要素やMarkdown以外の拡張子を400にする")
	void previewMarkdownFileRejectsInvalidFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.previewMarkdownFile("../secret.md"))
						.getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.previewMarkdownFile("memo.txt"))
						.getStatusCode());
	}

	@Test
	@DisplayName("Markdownプレビューは存在しないMarkdownファイルを404にする")
	void previewMarkdownFileReturnsNotFoundWhenFileDoesNotExist() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.previewMarkdownFile("missing.md"));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	@Test
	@DisplayName("Markdown本文プレビューは入力中本文をsanitize済みHTMLへ変換する")
	void previewMarkdownContentReturnsSanitizedHtml() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownPreviewRequest request = createPreviewRequest(
				"# Title\n\n**bold**\n\n<script>alert('x')</script>\n\n[bad](javascript:alert(1))");

		ResponseEntity<MarkdownPreviewContentResponse> response = service.previewMarkdownContent(request);

		MarkdownPreviewContentResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertTrue(body.getHtml().contains("<h1>Title</h1>"));
		assertTrue(body.getHtml().contains("<strong>bold</strong>"));
		assertFalse(body.getHtml().contains("<script>"));
		assertFalse(body.getHtml().contains("javascript:"));
	}

	@Test
	@DisplayName("Markdown本文プレビューはGFMの表を表要素へ変換し、sanitizeを維持する")
	void previewMarkdownContentRendersGfmTable() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownPreviewRequest request = createPreviewRequest(
				"| 項目 | 判定 |\n| --- | :---: |\n| A-1 | OK |\n\n<script>alert('x')</script>");

		ResponseEntity<MarkdownPreviewContentResponse> response = service.previewMarkdownContent(request);

		MarkdownPreviewContentResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		String html = body.getHtml();
		// 表拡張が有効なら段落ではなく表要素になる。
		assertTrue(html.contains("<table>"), html);
		assertTrue(html.contains("<th"), html);
		assertTrue(html.contains("<td"), html);
		assertTrue(html.contains("項目"), html);
		assertTrue(html.contains("A-1"), html);
		// 表拡張を足してもescapeHtml + sanitizeは維持され、scriptは除去される。
		assertFalse(html.contains("<script>"), html);
	}

	@Test
	@DisplayName("Markdown本文プレビューは本文未指定時に空HTMLを返す")
	void previewMarkdownContentReturnsEmptyHtmlWhenContentIsNull() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownPreviewRequest request = createPreviewRequest(null);

		ResponseEntity<MarkdownPreviewContentResponse> response = service.previewMarkdownContent(request);

		MarkdownPreviewContentResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(body);
		assertEquals("", body.getHtml());
	}

	@Test
	@DisplayName("Markdown本文プレビューはリクエスト未指定時にNullPointerExceptionにする")
	void previewMarkdownContentRejectsNullRequest() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		NullPointerException exception = assertThrows(NullPointerException.class,
				() -> service.previewMarkdownContent(null));

		assertEquals("request must not be null.", exception.getMessage());
	}

	@Test
	@DisplayName("Markdown更新は保存先直下に存在するMarkdown本文をUTF-8で更新する")
	void updateMarkdownFileWritesUtf8FileAndReturnsMetadata() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "old", StandardCharsets.UTF_8);
		MarkdownUpdateRequest request = createUpdateRequest("# Updated\nbody");

		ResponseEntity<MarkdownFileResponse> response = service.updateMarkdownFile("design-note.md", request);

		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("# Updated\nbody", Files.readString(markdownPath, StandardCharsets.UTF_8));
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals(Files.size(markdownPath), body.getByteSize());
		assertEquals(2L, body.getLineCount());
		assertNotNull(body.getLastModifiedTime());
	}

	@Test
	@DisplayName("Markdown更新はリクエスト未指定時にNullPointerExceptionにする")
	void updateMarkdownFileRejectsNullRequest() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		NullPointerException exception = assertThrows(NullPointerException.class,
				() -> service.updateMarkdownFile("design-note.md", null));

		assertEquals("request must not be null.", exception.getMessage());
	}

	@Test
	@DisplayName("Markdown更新は本文未指定時に空ファイルとして更新する")
	void updateMarkdownFileWritesEmptyFileWhenContentIsNull() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "old", StandardCharsets.UTF_8);
		MarkdownUpdateRequest request = createUpdateRequest(null);

		ResponseEntity<MarkdownFileResponse> response = service.updateMarkdownFile("design-note.md", request);

		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("", Files.readString(markdownPath, StandardCharsets.UTF_8));
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals(0L, body.getByteSize());
		assertEquals(0L, body.getLineCount());
		assertNotNull(body.getLastModifiedTime());
	}

	@Test
	@DisplayName("Markdown更新はファイル名の前後空白を除去して更新する")
	void updateMarkdownFileTrimsFileName() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "old", StandardCharsets.UTF_8);
		MarkdownUpdateRequest request = createUpdateRequest("updated");

		ResponseEntity<MarkdownFileResponse> response = service.updateMarkdownFile("  design-note.md  ", request);

		MarkdownFileResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals("updated", Files.readString(markdownPath, StandardCharsets.UTF_8));
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
	}

	@Test
	@DisplayName("Markdown更新は空白ファイル名を400にする")
	void updateMarkdownFileRejectsBlankFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownUpdateRequest request = createUpdateRequest("body");

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.updateMarkdownFile(null, request))
						.getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.updateMarkdownFile("   ", request))
						.getStatusCode());
	}

	@Test
	@DisplayName("Markdown更新はパス要素やMarkdown以外の拡張子を400にする")
	void updateMarkdownFileRejectsInvalidFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownUpdateRequest request = createUpdateRequest("body");

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.updateMarkdownFile("../secret.md", request))
						.getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.updateMarkdownFile("memo.txt", request))
						.getStatusCode());
	}

	@Test
	@DisplayName("Markdown更新は存在しないMarkdownファイルを404にする")
	void updateMarkdownFileReturnsNotFoundWhenFileDoesNotExist() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		MarkdownUpdateRequest request = createUpdateRequest("body");

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.updateMarkdownFile("missing.md", request));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	@Test
	@DisplayName("Markdown削除は保存先直下に存在するMarkdownファイルを削除する")
	void deleteMarkdownFileDeletesDirectMarkdownFile() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "body", StandardCharsets.UTF_8);

		ResponseEntity<MarkdownDeleteResponse> response = service.deleteMarkdownFile("design-note.md");

		MarkdownDeleteResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertFalse(Files.exists(markdownPath));
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals(Boolean.TRUE, body.getDeleted());
	}

	@Test
	@DisplayName("Markdown削除はファイル名の前後空白を除去して削除する")
	void deleteMarkdownFileTrimsFileName() throws IOException {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());
		Path markdownPath = tempDir.resolve("design-note.md");
		Files.writeString(markdownPath, "body", StandardCharsets.UTF_8);

		ResponseEntity<MarkdownDeleteResponse> response = service.deleteMarkdownFile("  design-note.md  ");

		MarkdownDeleteResponse body = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertFalse(Files.exists(markdownPath));
		assertNotNull(body);
		assertEquals("design-note.md", body.getFileName());
		assertEquals(Boolean.TRUE, body.getDeleted());
	}

	@Test
	@DisplayName("Markdown削除は空白ファイル名を400にする")
	void deleteMarkdownFileRejectsBlankFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.deleteMarkdownFile(null)).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.deleteMarkdownFile("   ")).getStatusCode());
	}

	@Test
	@DisplayName("Markdown削除はパス要素やMarkdown以外の拡張子を400にする")
	void deleteMarkdownFileRejectsInvalidFileName() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.deleteMarkdownFile("../secret.md"))
						.getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST,
				assertThrows(ResponseStatusException.class, () -> service.deleteMarkdownFile("memo.txt"))
						.getStatusCode());
	}

	@Test
	@DisplayName("Markdown削除は存在しないMarkdownファイルを404にする")
	void deleteMarkdownFileReturnsNotFoundWhenFileDoesNotExist() {
		MarkdownDocumentService service = new MarkdownDocumentService(tempDir.toString());

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.deleteMarkdownFile("missing.md"));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
	}

	private MarkdownSaveRequest createRequest(String fileName, String content) {
		MarkdownSaveRequest request = new MarkdownSaveRequest();
		request.setFileName(fileName);
		request.setContent(content);
		return request;
	}

	private MarkdownUpdateRequest createUpdateRequest(String content) {
		MarkdownUpdateRequest request = new MarkdownUpdateRequest();
		request.setContent(content);
		return request;
	}

	private MarkdownPreviewRequest createPreviewRequest(String content) {
		MarkdownPreviewRequest request = new MarkdownPreviewRequest();
		request.setContent(content);
		return request;
	}
}
