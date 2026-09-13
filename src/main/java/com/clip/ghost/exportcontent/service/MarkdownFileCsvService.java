package com.clip.ghost.exportcontent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.exportcontent.logic.CsvWriterSupport;
import com.clip.ghost.markdowncontent.dto.MarkdownFileResponse;
import com.clip.ghost.markdowncontent.service.MarkdownDocumentService;

import lombok.RequiredArgsConstructor;

/**
 * 保存済みMarkdownの一覧をCSVとして出力するサービス。
 * <p>
 * 一覧の取得は画面と同じ {@link MarkdownDocumentService} を使う。CSV専用の一覧取得を作ると、
 * 「画面には出ているのにCSVには出ていない」という差が生まれる。
 * <p>
 * これは {@code docs/future-document-ai-roadmap.md} の Phase G が本来の目的としていた
 * 「文書メタ情報をレポート形式で出す」ための最初の1本にあたる。
 */
@Service
@RequiredArgsConstructor
public class MarkdownFileCsvService {
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownFileCsvService.class);
	private static final String CSV_FILE_NAME = "markdown-files.csv";
	private static final List<String> CSV_HEADER = List.of("ファイル名", "サイズ(byte)", "行数", "更新日時");

	private final MarkdownDocumentService markdownDocumentService;
	private final CsvWriterSupport csvWriterSupport;

	/**
	 * 保存済みMarkdownの一覧をCSVのダウンロードレスポンスとして返却する。
	 *
	 * @param withBom UTF-8のバイト順マークを付ける場合true
	 * @return attachmentダウンロード用CSVレスポンス
	 */
	public ResponseEntity<Resource> exportMarkdownFileList(boolean withBom) {
		ApiResult<List<MarkdownFileResponse>> listResult = markdownDocumentService.listMarkdownFiles().getBody();
		List<MarkdownFileResponse> files = Objects.requireNonNull(listResult).getData();
		byte[] csvBytes = csvWriterSupport.write(CSV_HEADER, buildRows(files), withBom);
		// ファイル名は利用者の成果物なのでログへ出さない。件数だけで、出力できたかの確認には足りる。
		LOGGER.info("保存済みMarkdown一覧をCSVへ出力しました。fileCount={}", CollectionUtils.size(files));
		return ResponseUtils.downloadCsv(CSV_FILE_NAME, new ByteArrayResource(csvBytes));
	}

	/**
	 * 一覧レスポンスをCSVの行データへ変換する。
	 *
	 * @param files 保存済みMarkdownの一覧
	 * @return CSVの行データ
	 */
	private List<List<String>> buildRows(List<MarkdownFileResponse> files) {
		List<List<String>> rows = new ArrayList<>(CollectionUtils.size(files));
		for (MarkdownFileResponse file : CollectionUtils.emptyIfNull(files)) {
			rows.add(List.of(StringUtils.defaultString(file.getFileName()), toText(file.getByteSize()),
					toText(file.getLineCount()), StringUtils.defaultString(file.getLastModifiedTime())));
		}
		return rows;
	}

	/**
	 * 数値をCSVのセル文字列へ変換する。
	 * <p>
	 * 桁区切りは入れない。Excelで開いたときに数値ではなく文字列として扱われ、集計できなくなるため。
	 *
	 * @param value 数値。取得できなかった場合はnull
	 * @return セル文字列。nullの場合は空文字
	 */
	private String toText(Long value) {
		return Objects.isNull(value) ? StringUtils.EMPTY : String.valueOf(value);
	}
}
