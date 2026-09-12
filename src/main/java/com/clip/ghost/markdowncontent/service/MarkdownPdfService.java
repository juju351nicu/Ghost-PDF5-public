package com.clip.ghost.markdowncontent.service;

import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.clip.ghost.common.utils.PathUtils;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.markdowncontent.dto.MarkdownPdfRequest;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.markdowncontent.logic.MarkdownPdfRenderer;

import lombok.RequiredArgsConstructor;

/**
 * Markdown本文からPDFを生成するサービス。
 * <p>
 * Markdown → HTML の変換は画面プレビューと同じ {@link MarkdownHtmlRenderer} を使い、
 * HTML → PDF の描画は {@link MarkdownPdfRenderer} へ委譲する。このクラスはファイル名の正規化と
 * レスポンス組み立てを担当する。
 * <p>
 * 生成したPDFは保存しない。保存済みMarkdownを対象にする場合も、画面が本文を読み込んでから
 * このAPIへ渡す。保存の有無で処理を分けず、入力中の本文をそのまま確認できる形を優先する。
 */
@Service
@RequiredArgsConstructor
public class MarkdownPdfService {
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownPdfService.class);
	private static final String DEFAULT_PDF_BASE_NAME = "document";
	private static final String PDF_FILE_SUFFIX = ".pdf";

	private final MarkdownHtmlRenderer htmlRenderer;
	private final MarkdownPdfRenderer pdfRenderer;

	/**
	 * Markdown本文をPDFへ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param request Markdown本文とファイル名を含むリクエスト
	 * @return attachmentダウンロード用PDFレスポンス
	 */
	public ResponseEntity<Resource> generatePdf(MarkdownPdfRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		String fileName = normalizePdfFileName(request.getFileName());
		String html = htmlRenderer.render(request.getContent());
		byte[] pdfBytes = pdfRenderer.render(PathUtils.removeExtension(fileName), html);
		// 本文の内容はログへ出さない。出力サイズだけで、生成できたかの確認には足りる。
		LOGGER.info("MarkdownからPDFを生成しました。byteSize={}", pdfBytes.length);
		return ResponseUtils.downloadPdf(fileName, buildPdfResource(pdfBytes));
	}

	/**
	 * PDFのbyte配列をレスポンス用リソースへ変換する。
	 *
	 * @param pdfBytes PDFのbyte配列
	 * @return レスポンス本文のリソース
	 */
	private Resource buildPdfResource(byte[] pdfBytes) {
		return new ByteArrayResource(pdfBytes);
	}

	/**
	 * ダウンロードファイル名を1ファイル名に正規化し、PDF拡張子へそろえる。
	 * <p>
	 * ファイル名はレスポンスヘッダーへ出るため、ディレクトリ区切りや制御文字を残さない。
	 * 正規化の規則はMarkdown保存と同じ {@link PathUtils#sanitizeFileName(String)} に寄せる。
	 *
	 * @param fileName 入力ファイル名
	 * @return ダウンロード用PDFファイル名
	 */
	private String normalizePdfFileName(String fileName) {
		String baseFileName = FilenameUtils
				.getName(StringUtils.defaultIfBlank(StringUtils.trim(fileName), DEFAULT_PDF_BASE_NAME));
		String safeFileName = StringUtils.defaultIfBlank(PathUtils.sanitizeFileName(baseFileName),
				DEFAULT_PDF_BASE_NAME);
		return StringUtils.defaultIfBlank(PathUtils.removeExtension(safeFileName), DEFAULT_PDF_BASE_NAME)
				+ PDF_FILE_SUFFIX;
	}
}
