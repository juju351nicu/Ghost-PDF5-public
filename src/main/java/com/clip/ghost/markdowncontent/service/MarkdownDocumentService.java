package com.clip.ghost.markdowncontent.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.clip.ghost.common.response.ApiMessage;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.utils.FileInfoUtils;
import com.clip.ghost.common.utils.FileOperationUtils;
import com.clip.ghost.common.utils.PathUtils;
import com.clip.ghost.markdowncontent.dto.MarkdownDeleteResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownDocumentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownFileResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewContentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownSaveRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownUpdateRequest;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;

/**
 * Markdown文書の保存と保存後メタデータ取得を扱うサービス。
 * <p>
 * PDF編集コアには混ぜず、Markdown保存ディレクトリ配下へUTF-8テキストとして保存する。
 */
@Service
public class MarkdownDocumentService {
	private static final String DEFAULT_MARKDOWN_FILE_BASE_NAME = "document";
	private static final String MARKDOWN_FILE_EXTENSION = "md";
	private static final String MARKDOWN_FILE_SUFFIX = "." + MARKDOWN_FILE_EXTENSION;
	private static final String OVERWRITTEN_MESSAGE_CODE = "markdownFileOverwritten";
	private static final String OVERWRITTEN_MESSAGE = "既存のファイルを上書きしました。";
	private final Path storageDirectory;
	private final MarkdownHtmlRenderer htmlRenderer;

	/**
	 * Markdown保存先ディレクトリを設定から受け取る。
	 * <p>
	 * 既定値はホーム配下にする。保存したMarkdownは利用者の成果物であり、
	 * {@code java.io.tmpdir} 配下はOS（Windowsのストレージセンサーなど）が自動削除するため使わない。
	 *
	 * @param storageDirectory Markdown保存先ディレクトリ
	 * @param htmlRenderer     Markdown本文をsanitize済みHTMLへ変換するレンダラー
	 */
	public MarkdownDocumentService(
			@Value("${ghost.markdown.storage-directory:${user.home}/ghost-pdf5/markdown}") String storageDirectory,
			MarkdownHtmlRenderer htmlRenderer) {
		this.storageDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize();
		// HTML変換はPDF出力と共有する。変換規則が分かれると、プレビューとPDFで見た目がずれる。
		this.htmlRenderer = htmlRenderer;
	}

	/**
	 * Markdown本文を保存し、保存後の最小メタデータを返却する。
	 * <p>
	 * 既存ファイルがある場合も従来どおり上書きし、上書きしたことだけを結果種別WARNINGで事後に伝える。
	 * 保存を拒否すると、下書きを反映しただけの利用者が保存できなくなるため挙動は変えない。
	 * 上書き判定は書き込み前に行う。書き込み後では新規作成と上書きを区別できない。
	 *
	 * @param request Markdown保存リクエスト
	 * @return Markdown保存レスポンス
	 */
	public ResponseEntity<ApiResult<MarkdownFileResponse>> saveMarkdown(MarkdownSaveRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		String fileName = normalizeMarkdownFileName(request.getFileName());
		Path outputPath = storageDirectory.resolve(fileName).normalize();
		if (Files.isSymbolicLink(outputPath)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Markdown file path.");
		}
		boolean overwritten = Files.exists(outputPath);
		FileOperationUtils.writeString(outputPath, request.getContent());
		MarkdownFileResponse response = buildFileResponse(fileName, outputPath);
		if (overwritten) {
			List<ApiMessage> messageList = List.of(new ApiMessage(OVERWRITTEN_MESSAGE_CODE, OVERWRITTEN_MESSAGE));
			return ResponseEntity.ok(ApiResult.warning(response, messageList));
		}
		return ResponseEntity.ok(ApiResult.of(response));
	}

	/**
	 * 保存先ディレクトリ直下のMarkdownファイル一覧を返却する。
	 *
	 * @return 保存済みMarkdownファイル一覧
	 */
	public ResponseEntity<ApiResult<List<MarkdownFileResponse>>> listMarkdownFiles() {
		List<MarkdownFileResponse> files = FileInfoUtils.getFilePaths(storageDirectory, false).stream()
				.filter(path -> !Files.isSymbolicLink(path))
				.filter(path -> PathUtils.isMarkdownFileName(path.getFileName().toString()))
				.sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
				.map(path -> buildFileResponse(path.getFileName().toString(), path)).toList();
		return ResponseEntity.ok(ApiResult.of(files));
	}

	/**
	 * 保存済みMarkdown本文を返却する。
	 *
	 * @param fileName 読み取り対象Markdownファイル名
	 * @return 保存済みMarkdown本文レスポンス
	 */
	public ResponseEntity<ApiResult<MarkdownDocumentResponse>> getMarkdownFile(String fileName) {
		Path filePath = resolveReadableMarkdownPath(fileName);
		MarkdownDocumentResponse response = buildDocumentResponse(filePath.getFileName().toString(), filePath,
				readMarkdownContent(filePath));
		return ResponseEntity.ok(ApiResult.of(response));
	}

	/**
	 * 保存済みMarkdownをHTMLプレビューへ変換して返却する。
	 *
	 * @param fileName 読み取り対象Markdownファイル名
	 * @return Markdownプレビューレスポンス
	 */
	public ResponseEntity<ApiResult<MarkdownPreviewResponse>> previewMarkdownFile(String fileName) {
		Path filePath = resolveReadableMarkdownPath(fileName);
		MarkdownPreviewResponse response = buildPreviewResponse(filePath.getFileName().toString(), filePath,
				htmlRenderer.render(readMarkdownContent(filePath)));
		return ResponseEntity.ok(ApiResult.of(response));
	}

	/**
	 * Markdown本文をHTMLプレビューへ変換して返却する。
	 *
	 * @param request Markdown本文プレビューリクエスト
	 * @return Markdown本文プレビューレスポンス
	 */
	public ResponseEntity<ApiResult<MarkdownPreviewContentResponse>> previewMarkdownContent(
			MarkdownPreviewRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		return ResponseEntity.ok(ApiResult.of(buildPreviewContentResponse(htmlRenderer.render(request.getContent()))));
	}

	/**
	 * 保存済みMarkdown本文を更新する。
	 *
	 * @param fileName 更新対象Markdownファイル名
	 * @param request  Markdown更新リクエスト
	 * @return Markdown更新後メタデータレスポンス
	 */
	public ResponseEntity<ApiResult<MarkdownFileResponse>> updateMarkdownFile(String fileName,
			MarkdownUpdateRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		Path filePath = resolveReadableMarkdownPath(fileName);
		FileOperationUtils.writeString(filePath, request.getContent());
		return ResponseEntity.ok(ApiResult.of(buildFileResponse(filePath.getFileName().toString(), filePath)));
	}

	/**
	 * 保存済みMarkdownファイルを削除する。
	 *
	 * @param fileName 削除対象Markdownファイル名
	 * @return Markdown削除レスポンス
	 */
	public ResponseEntity<ApiResult<MarkdownDeleteResponse>> deleteMarkdownFile(String fileName) {
		Path filePath = resolveReadableMarkdownPath(fileName);
		String resolvedFileName = filePath.getFileName().toString();
		FileOperationUtils.deleteFile(filePath);
		return ResponseEntity.ok(ApiResult.of(buildDeleteResponse(resolvedFileName)));
	}

	/**
	 * 保存ファイル名を1ファイル名に正規化し、Markdown拡張子へそろえる。
	 *
	 * @param fileName 入力ファイル名
	 * @return 保存用Markdownファイル名
	 */
	private String normalizeMarkdownFileName(String fileName) {
		String requestedFileName = StringUtils.trimToNull(fileName);
		String baseFileName = FilenameUtils
				.getName(StringUtils.defaultIfBlank(requestedFileName, DEFAULT_MARKDOWN_FILE_BASE_NAME));
		String safeFileName = PathUtils.sanitizeFileName(baseFileName);
		safeFileName = StringUtils.defaultIfBlank(safeFileName, DEFAULT_MARKDOWN_FILE_BASE_NAME);
		if (PathUtils.isMarkdownFileName(safeFileName)) {
			return safeFileName;
		}
		return StringUtils.defaultIfBlank(PathUtils.removeExtension(safeFileName), DEFAULT_MARKDOWN_FILE_BASE_NAME)
				+ MARKDOWN_FILE_SUFFIX;
	}

	/**
	 * 読み取り対象Markdownファイル名を保存先直下のPathへ解決する。
	 *
	 * @param fileName 読み取り対象Markdownファイル名
	 * @return 読み取り対象Markdownファイルパス
	 */
	private Path resolveReadableMarkdownPath(String fileName) {
		String requestedFileName = StringUtils.trimToNull(fileName);
		if (StringUtils.isBlank(requestedFileName)
				|| !Strings.CS.equals(requestedFileName, FilenameUtils.getName(requestedFileName))
				|| !PathUtils.isMarkdownFileName(requestedFileName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Markdown file name.");
		}
		Path filePath = storageDirectory.resolve(requestedFileName).normalize();
		if (!filePath.startsWith(storageDirectory) || Files.isSymbolicLink(filePath)
				|| !FileInfoUtils.isRegularFile(filePath)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Markdown file not found.");
		}
		return filePath;
	}

	/**
	 * MarkdownファイルをUTF-8文字列として読み取る。
	 *
	 * @param filePath 読み取り対象ファイルパス
	 * @return Markdown本文
	 */
	private String readMarkdownContent(Path filePath) {
		try {
			return Files.readString(filePath, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 保存済みMarkdown本文とファイルメタデータレスポンスを組み立てる。
	 *
	 * @param fileName   保存ファイル名
	 * @param outputPath 保存先パス
	 * @param content    Markdown本文
	 * @return Markdown本文レスポンス
	 */
	private MarkdownDocumentResponse buildDocumentResponse(String fileName, Path outputPath, String content) {
		MarkdownDocumentResponse response = new MarkdownDocumentResponse();
		response.setFileName(fileName);
		response.setByteSize(FileInfoUtils.getFileByteSize(outputPath));
		response.setLineCount(FileInfoUtils.countLines(outputPath));
		response.setLastModifiedTime(FileInfoUtils.getLastModifiedTimeText(outputPath));
		response.setContent(content);
		return response;
	}

	/**
	 * MarkdownプレビューHTMLとファイルメタデータレスポンスを組み立てる。
	 *
	 * @param fileName   保存ファイル名
	 * @param outputPath 保存先パス
	 * @param html       sanitize済みHTML
	 * @return Markdownプレビューレスポンス
	 */
	private MarkdownPreviewResponse buildPreviewResponse(String fileName, Path outputPath, String html) {
		MarkdownPreviewResponse response = new MarkdownPreviewResponse();
		response.setFileName(fileName);
		response.setByteSize(FileInfoUtils.getFileByteSize(outputPath));
		response.setLineCount(FileInfoUtils.countLines(outputPath));
		response.setLastModifiedTime(FileInfoUtils.getLastModifiedTimeText(outputPath));
		response.setHtml(html);
		return response;
	}

	/**
	 * 入力中Markdown本文のHTMLプレビューレスポンスを組み立てる。
	 *
	 * @param html sanitize済みHTML
	 * @return Markdown本文プレビューレスポンス
	 */
	private MarkdownPreviewContentResponse buildPreviewContentResponse(String html) {
		MarkdownPreviewContentResponse response = new MarkdownPreviewContentResponse();
		response.setHtml(html);
		return response;
	}

	/**
	 * Markdown削除レスポンスを組み立てる。
	 *
	 * @param fileName 削除したMarkdownファイル名
	 * @return Markdown削除レスポンス
	 */
	private MarkdownDeleteResponse buildDeleteResponse(String fileName) {
		MarkdownDeleteResponse response = new MarkdownDeleteResponse();
		response.setFileName(fileName);
		response.setDeleted(Boolean.TRUE);
		return response;
	}

	/**
	 * 保存済みファイルのメタデータレスポンスを組み立てる。
	 *
	 * @param fileName   保存ファイル名
	 * @param outputPath 保存先パス
	 * @return Markdown保存レスポンス
	 */
	private MarkdownFileResponse buildFileResponse(String fileName, Path outputPath) {
		MarkdownFileResponse response = new MarkdownFileResponse();
		response.setFileName(fileName);
		response.setByteSize(FileInfoUtils.getFileByteSize(outputPath));
		response.setLineCount(FileInfoUtils.countLines(outputPath));
		response.setLastModifiedTime(FileInfoUtils.getLastModifiedTimeText(outputPath));
		return response;
	}
}
