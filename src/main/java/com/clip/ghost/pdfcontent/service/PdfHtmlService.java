package com.clip.ghost.pdfcontent.service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.nodes.Entities;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.utils.PathUtils;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.markdowncontent.logic.EpubWriter;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;

import lombok.RequiredArgsConstructor;

/**
 * PDFをHTML / EPUBへ変換するサービス。
 * <p>
 * 変換は新規に書かず、既存の2段を順につなぐだけにする。
 * PDF → ページ単位Markdown は {@link PdfMarkdownDraftService}、Markdown → HTML は
 * 画面プレビューと同じ {@link MarkdownHtmlRenderer} を使う。
 * ここに独自のHTML組み立てを持つと、プレビューとダウンロードHTMLで見た目が食い違う。
 * <p>
 * したがって変換の精度はMarkdown下書きと同じで、{@code mode} もそのまま効く。
 * 文字レイヤーのあるPDFは文字から、{@code VISION} を指定すれば全ページを画像変換から起こす。
 */
@Service
@RequiredArgsConstructor
public class PdfHtmlService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfHtmlService.class);
	private static final String DEFAULT_BASE_NAME = "document";
	private static final String HTML_FILE_SUFFIX = ".html";
	private static final String EPUB_FILE_SUFFIX = ".epub";
	// ブラウザで直接開けるよう、文字コードとviewportだけを持つ最小の外枠を付ける。
	// スタイルは持たせない。見た目を作り込むならMarkdownを編集してPDF出力へ回すほうが調整しやすい。
	private static final String HTML_DOCUMENT_FORMAT = """
			<!DOCTYPE html>
			<html lang="ja">
			<head>
			<meta charset="utf-8">
			<meta name="viewport" content="width=device-width,initial-scale=1.0">
			<title>%s</title>
			</head>
			<body>
			%s
			</body>
			</html>
			""";

	private final PdfMarkdownDraftService markdownDraftService;
	private final MarkdownHtmlRenderer htmlRenderer;
	private final EpubWriter epubWriter;

	/**
	 * アップロードされたPDFをHTMLへ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param form 変換元PDFと変換モードを含むフォーム
	 * @return attachmentダウンロード用HTMLレスポンス
	 */
	public ResponseEntity<Resource> generateHtml(PdfMarkdownDraftRequest form) {
		ApiResult<PdfMarkdownDraftResponse> draftResult = markdownDraftService.generateMarkdownDraft(form).getBody();
		PdfMarkdownDraftResponse draft = Objects.requireNonNull(draftResult).getData();
		String title = resolveTitle(form);
		String html = HTML_DOCUMENT_FORMAT.formatted(Entities.escape(title), htmlRenderer.render(draft.getMarkdown()));
		byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
		// 本文の内容はログへ出さない。出力サイズだけで、生成できたかの確認には足りる。
		LOGGER.info("PDFからHTMLを生成しました。byteSize={}", htmlBytes.length);
		return ResponseUtils.downloadHtml(title + HTML_FILE_SUFFIX, new ByteArrayResource(htmlBytes));
	}

	/**
	 * アップロードされたPDFをEPUBへ変換し、ダウンロードレスポンスとして返却する。
	 * <p>
	 * HTML出力と同じ経路（PDF → ページ単位Markdown → HTML）を通り、最後にEPUBとして包むだけにする。
	 * EPUB専用の変換を別に持つと、同じPDFからHTMLとEPUBで違う本文が出てしまう。
	 * <p>
	 * 章分けはしない。ページで機械的に切ると、リーダー上で文の途中に章境界が入るため。
	 *
	 * @param form 変換元PDFと変換モードを含むフォーム
	 * @return attachmentダウンロード用EPUBレスポンス
	 */
	public ResponseEntity<Resource> generateEpub(PdfMarkdownDraftRequest form) {
		ApiResult<PdfMarkdownDraftResponse> draftResult = markdownDraftService.generateMarkdownDraft(form).getBody();
		PdfMarkdownDraftResponse draft = Objects.requireNonNull(draftResult).getData();
		String title = resolveTitle(form);
		byte[] epubBytes = epubWriter.write(title, htmlRenderer.render(draft.getMarkdown()));
		// 本文の内容はログへ出さない。出力サイズだけで、生成できたかの確認には足りる。
		LOGGER.info("PDFからEPUBを生成しました。byteSize={}", epubBytes.length);
		return ResponseUtils.downloadEpub(title + EPUB_FILE_SUFFIX, new ByteArrayResource(epubBytes));
	}

	/**
	 * 出力ファイル名と文書タイトルに使う文字列を、変換元PDFのファイル名から決める。
	 * <p>
	 * HTMLとEPUBで同じ規則を使う。片方だけ別の決め方にすると、同じPDFから出した2つのファイルで
	 * 名前とタイトルが食い違う。
	 *
	 * @param form 変換元PDFを含むフォーム
	 * @return 拡張子を除いたタイトル
	 */
	private String resolveTitle(PdfMarkdownDraftRequest form) {
		return PathUtils.removeExtension(resolveBaseFileName(form));
	}

	/**
	 * 出力ファイル名の基になるファイル名を1ファイル名に正規化する。
	 * <p>
	 * ファイル名はレスポンスヘッダーへ出るため、ディレクトリ区切りや制御文字を残さない。
	 * 正規化の規則はMarkdown保存・PDF出力と同じ {@link PathUtils#sanitizeFileName(String)} に寄せる。
	 *
	 * @param form 変換元PDFを含むフォーム
	 * @return 正規化したファイル名
	 */
	private String resolveBaseFileName(PdfMarkdownDraftRequest form) {
		String originalFileName = FilenameUtils.getName(
				StringUtils.defaultIfBlank(form.getOriginalFile().getOriginalFilename(), DEFAULT_BASE_NAME));
		return StringUtils.defaultIfBlank(PathUtils.sanitizeFileName(originalFileName), DEFAULT_BASE_NAME);
	}
}
