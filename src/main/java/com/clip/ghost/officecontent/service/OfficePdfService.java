package com.clip.ghost.officecontent.service;

import java.util.List;
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
import com.clip.ghost.markdowncontent.logic.MarkdownHtmlRenderer;
import com.clip.ghost.markdowncontent.logic.MarkdownPdfRenderer;
import com.clip.ghost.officecontent.dto.OfficeMarkdownRequest;
import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.logic.OfficeMarkdownLogic;
import com.clip.ghost.officecontent.logic.PowerPointPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * Office文書からPDFを生成するサービス。
 * <p>
 * 形式によって経路を変える。
 * <ul>
 * <li>Word / Excel: Markdownを経由し、既存のMarkdown → HTML → PDF の描画へ載せる。
 * 内容レベルの変換になるが、PDF生成の実装を新しく書かずに済み、画面プレビューとも見た目が一致する。</li>
 * <li>PowerPoint: スライドを画像化してページへ貼る。図形の位置関係そのものが情報のため、
 * テキストだけ抜き出すと資料として意味をなさない。代わりにPDFのテキストは選択できない。</li>
 * </ul>
 * この違いはAPIの説明文にも明記し、利用者が期待値を取り違えないようにする。
 */
@Service
@RequiredArgsConstructor
public class OfficePdfService {
	private static final Logger LOGGER = LoggerFactory.getLogger(OfficePdfService.class);
	private static final String DEFAULT_PDF_BASE_NAME = "document";
	private static final String PDF_FILE_SUFFIX = ".pdf";
	private static final String UNKNOWN_FILE_NAME = "（ファイル名不明）";

	/** スライド画像で変換するため、Markdown経由にしない形式。 */
	private static final List<OfficeDocumentType> SLIDE_IMAGE_TYPES = List.of(OfficeDocumentType.PPTX);

	private final OfficeMarkdownLogic officeMarkdownLogic;
	private final PowerPointPdfLogic powerPointPdfLogic;
	private final MarkdownHtmlRenderer htmlRenderer;
	private final MarkdownPdfRenderer pdfRenderer;

	/**
	 * アップロードされたOffice文書をPDFへ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param form PDFへ変換するOffice文書を含むフォーム
	 * @return attachmentダウンロード用PDFレスポンス
	 * @throws OfficeInputException 対応していない形式、またはOffice文書として読み込めない場合
	 */
	public ResponseEntity<Resource> generatePdf(OfficeMarkdownRequest form) {
		MultipartFile officeFile = form.getOfficeFile();
		String originalFileName = StringUtils.defaultIfBlank(officeFile.getOriginalFilename(), UNKNOWN_FILE_NAME);
		OfficeDocumentType documentType = resolveDocumentType(originalFileName);
		String pdfFileName = normalizePdfFileName(originalFileName);
		byte[] pdfBytes = SLIDE_IMAGE_TYPES.contains(documentType)
				? powerPointPdfLogic.renderPdf(officeFile, originalFileName)
				: renderPdfViaMarkdown(officeFile, documentType, pdfFileName);
		// 本文の内容はログへ出さない。出力サイズだけで、生成できたかの確認には足りる。
		LOGGER.info("Office文書からPDFを生成しました。type={}, byteSize={}", documentType.getKey(), pdfBytes.length);
		return ResponseUtils.downloadPdf(pdfFileName, new ByteArrayResource(pdfBytes));
	}

	/**
	 * Markdownを経由してPDFを描画する。
	 *
	 * @param officeFile   アップロードされたOffice文書
	 * @param documentType 判定したOffice形式
	 * @param pdfFileName  出力PDFのファイル名
	 * @return PDFのbyte配列
	 */
	private byte[] renderPdfViaMarkdown(MultipartFile officeFile, OfficeDocumentType documentType,
			String pdfFileName) {
		String markdown = officeMarkdownLogic.readMarkdown(officeFile, documentType);
		return pdfRenderer.render(PathUtils.removeExtension(pdfFileName), htmlRenderer.render(markdown));
	}

	/**
	 * ファイル名の拡張子からOffice形式を判定する。
	 *
	 * @param fileName アップロードファイル名
	 * @return 判定したOffice形式
	 * @throws OfficeInputException 対応する形式が無い場合
	 */
	private OfficeDocumentType resolveDocumentType(String fileName) {
		OfficeDocumentType documentType = OfficeDocumentType.fromFileName(fileName);
		if (Objects.isNull(documentType)) {
			throw new OfficeInputException(fileName);
		}
		return documentType;
	}

	/**
	 * ダウンロードファイル名を1ファイル名に正規化し、PDF拡張子へそろえる。
	 * <p>
	 * 正規化の規則はMarkdown保存・PDF出力と同じ {@link PathUtils#sanitizeFileName(String)} に寄せる。
	 *
	 * @param fileName アップロードファイル名
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
