package com.clip.ghost.markdowncontent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.utils.PathUtils;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.markdowncontent.dto.MarkdownPdfRequest;
import com.clip.ghost.markdowncontent.dto.PdfFromEpubRequest;
import com.clip.ghost.markdowncontent.dto.PdfFromHtmlRequest;
import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;
import com.clip.ghost.markdowncontent.logic.EpubReader;
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.markdowncontent.logic.MarkdownPdfRenderer;

import lombok.RequiredArgsConstructor;

/**
 * Markdown本文、HTML、またはEPUBからPDFを生成するサービス。
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
	private final EpubReader epubReader;

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
	 * アップロードされたHTMLをPDFへ変換し、ダウンロードレスポンスとして返却する。
	 * <p>
	 * HTMLはMarkdown変換後と同じ許可範囲でsanitizeしてから描画する。描画器は外部リソースを取得しないため、
	 * HTMLに外部CSSや外部画像が書かれていても取り込まれない。見た目を保つには、HTML内でスタイルを完結させる必要がある。
	 *
	 * @param request PDFへ変換するHTMLファイルとファイル名を含むリクエスト
	 * @return attachmentダウンロード用PDFレスポンス
	 * @throws MarkdownPdfException HTMLを読めない、またはPDFの描画に失敗した場合
	 */
	public ResponseEntity<Resource> generatePdfFromHtml(PdfFromHtmlRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		MultipartFile htmlFile = request.getHtmlFile();
		String fileName = normalizePdfFileName(
				StringUtils.defaultIfBlank(request.getFileName(), htmlFile.getOriginalFilename()));
		String html = htmlRenderer.sanitize(readHtml(htmlFile));
		byte[] pdfBytes = pdfRenderer.render(PathUtils.removeExtension(fileName), html);
		// 本文の内容はログへ出さない。出力サイズだけで、生成できたかの確認には足りる。
		LOGGER.info("HTMLからPDFを生成しました。byteSize={}", pdfBytes.length);
		return ResponseUtils.downloadPdf(fileName, buildPdfResource(pdfBytes));
	}

	/**
	 * アップロードされたEPUBをPDFへ変換し、ダウンロードレスポンスとして返却する。
	 * <p>
	 * 本文はspine（読む順序）どおりに連結してから、HTMLと同じ経路で描画する。
	 * EPUB内のCSS・画像・フォントは取り込まないため、見た目はリーダーで開いたときと一致しない。
	 *
	 * @param request PDFへ変換するEPUBファイルとファイル名を含むリクエスト
	 * @return attachmentダウンロード用PDFレスポンス
	 * @throws MarkdownPdfException EPUBを読めない、またはPDFの描画に失敗した場合
	 */
	public ResponseEntity<Resource> generatePdfFromEpub(PdfFromEpubRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		MultipartFile epubFile = request.getEpubFile();
		String fileName = normalizePdfFileName(
				StringUtils.defaultIfBlank(request.getFileName(), epubFile.getOriginalFilename()));
		String html = htmlRenderer.sanitize(epubReader.readBodyHtml(readBytes(epubFile)));
		byte[] pdfBytes = pdfRenderer.render(PathUtils.removeExtension(fileName), html);
		// 本文の内容はログへ出さない。出力サイズだけで、生成できたかの確認には足りる。
		LOGGER.info("EPUBからPDFを生成しました。byteSize={}", pdfBytes.length);
		return ResponseUtils.downloadPdf(fileName, buildPdfResource(pdfBytes));
	}

	/**
	 * アップロードファイルの内容をbyte配列として読み込む。
	 *
	 * @param multipartFile アップロードファイル
	 * @return ファイルの内容
	 * @throws MarkdownPdfException ファイルを読み込めない場合
	 */
	private byte[] readBytes(MultipartFile multipartFile) {
		try {
			return multipartFile.getBytes();
		} catch (IOException e) {
			throw new MarkdownPdfException("アップロードされたファイルを読み込めませんでした。", e);
		}
	}

	/**
	 * アップロードされたHTMLをUTF-8のテキストとして読み込む。
	 * <p>
	 * 文字コードは常にUTF-8として扱う。HTMLの {@code meta charset} を見て切り替えると、宣言と実体がずれた
	 * ファイルで無言のまま文字化けする。読めない前提を1つに固定し、化けたら利用者が変換して渡し直せるようにする。
	 *
	 * @param htmlFile アップロードされたHTMLファイル
	 * @return HTML本文
	 * @throws MarkdownPdfException HTMLを読み込めない場合
	 */
	private String readHtml(MultipartFile htmlFile) {
		return new String(readBytes(htmlFile), StandardCharsets.UTF_8);
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
