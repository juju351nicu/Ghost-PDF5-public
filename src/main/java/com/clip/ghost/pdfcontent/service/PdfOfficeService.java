package com.clip.ghost.pdfcontent.service;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
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
import com.clip.ghost.officecontent.logic.OfficeWriterLogic;
import com.clip.ghost.pdfcontent.config.PdfImageProperties;
import com.clip.ghost.pdfcontent.dto.OfficeFromPdfRequest;
import com.clip.ghost.pdfcontent.dto.PdfPageImage;
import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * PDFからOffice文書を生成するサービス。
 * <p>
 * PDF側の読み取りは {@link GhostPdfLogic}、Office側の書き出しは {@link OfficeWriterLogic} が担当し、
 * このクラスは形式ごとの経路選択とファイル名の正規化だけを持つ。
 * <p>
 * 形式によって取り出す情報が違う。
 * <ul>
 * <li>Word / Excel: ページ単位のテキスト。文字として編集できるが、レイアウトは失われる。</li>
 * <li>PowerPoint: ページ画像。見た目は保たれるが、文字は選択できない。</li>
 * </ul>
 * この違いはAPIの説明文にも明記し、利用者が期待値を取り違えないようにする。
 */
@Service
@RequiredArgsConstructor
public class PdfOfficeService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfOfficeService.class);
	private static final String DEFAULT_BASE_NAME = "document";
	private static final String FILE_NAME_EXTENSION_SEPARATOR = ".";

	private final GhostPdfLogic pdfLogic;
	private final OfficeWriterLogic officeWriterLogic;
	private final PdfImageProperties imageProperties;

	/**
	 * アップロードされたPDFをOffice文書へ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param form 変換元PDFと出力形式を含むフォーム
	 * @return attachmentダウンロード用Office文書レスポンス
	 */
	public ResponseEntity<Resource> generateOffice(OfficeFromPdfRequest form) {
		Path inputPath = pdfLogic.loadPdf(form.getOriginalFile(), form.getPassword());
		byte[] officeBytes = switch (form.getFormat()) {
			case DOCX -> officeWriterLogic.writeWord(pdfLogic.extractPdfPageTexts(inputPath));
			case XLSX -> officeWriterLogic.writeExcel(pdfLogic.extractPdfPageTexts(inputPath));
			case PPTX -> writePowerPoint(inputPath);
		};
		String fileName = buildOfficeFileName(form);
		// 本文の内容はログへ出さない。出力サイズだけで、生成できたかの確認には足りる。
		LOGGER.info("PDFからOffice文書を生成しました。format={}, byteSize={}", form.getFormat().getKey(), officeBytes.length);
		return ResponseUtils.downloadOffice(fileName, new ByteArrayResource(officeBytes));
	}

	/**
	 * PDFのページ画像からPowerPointを生成する。
	 *
	 * @param inputPath 変換元PDFの一時保存先パス
	 * @return PowerPointのbyte配列
	 */
	private byte[] writePowerPoint(Path inputPath) {
		// スライドは画像を貼るだけなので、可逆のPNGで取り出す。JPGだと文字の輪郭にノイズが出る。
		List<PdfPageImage> pageImages = pdfLogic.readPdfPageImages(inputPath, PdfImageFormat.PNG,
				imageProperties.getDefaultDpi(), imageProperties.getMaxPages(), List.of());
		PdfPageImage firstPage = CollectionUtils.isEmpty(pageImages) ? null : pageImages.get(0);
		float widthPoints = firstPage == null ? 0f : firstPage.widthPoints();
		float heightPoints = firstPage == null ? 0f : firstPage.heightPoints();
		return officeWriterLogic.writePowerPoint(pageImages.stream().map(PdfPageImage::pngBytes).toList(), widthPoints,
				heightPoints);
	}

	/**
	 * ダウンロードファイル名を1ファイル名に正規化し、出力形式の拡張子へそろえる。
	 * <p>
	 * 正規化の規則はMarkdown保存・PDF出力と同じ {@link PathUtils#sanitizeFileName(String)} に寄せる。
	 *
	 * @param form 変換元PDFと出力形式を含むフォーム
	 * @return ダウンロード用ファイル名
	 */
	private String buildOfficeFileName(OfficeFromPdfRequest form) {
		String originalFileName = FilenameUtils.getName(StringUtils
				.defaultIfBlank(StringUtils.trim(form.getOriginalFile().getOriginalFilename()), DEFAULT_BASE_NAME));
		String safeFileName = StringUtils.defaultIfBlank(PathUtils.sanitizeFileName(originalFileName),
				DEFAULT_BASE_NAME);
		String baseName = StringUtils.defaultIfBlank(PathUtils.removeExtension(safeFileName), DEFAULT_BASE_NAME);
		return baseName + FILE_NAME_EXTENSION_SEPARATOR + form.getFormat().getFileExtension();
	}
}
