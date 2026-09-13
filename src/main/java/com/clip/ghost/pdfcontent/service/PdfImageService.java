package com.clip.ghost.pdfcontent.service;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.pdfcontent.config.PdfImageProperties;
import com.clip.ghost.pdfcontent.dto.PdfFromImagesRequest;
import com.clip.ghost.pdfcontent.dto.PdfImagesRequest;
import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.clip.ghost.pdfcontent.exception.PdfImageInputException;
import com.clip.ghost.pdfcontent.exception.PdfRenderDpiException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * PDFと画像の相互変換を担当するサービス。
 * <p>
 * PDFの保存、画像化、画像からのPDF生成は {@link GhostPdfLogic} へ委譲し、このクラスは
 * 解像度とページサイズの既定値補完、および解像度の上限検証を担当する。
 * 解像度とページ数上限は {@link PdfImageProperties}（{@code ghost.pdf.image}）から取る。
 */
@Service
@RequiredArgsConstructor
public class PdfImageService {
	private static final String IMAGES_ZIP_FILE_NAME = "images.zip";

	private final GhostPdfLogic pdfLogic;
	private final PdfImageProperties properties;

	/**
	 * アップロードされたPDFのページを画像化し、ZIPレスポンスとして返却する。
	 *
	 * @param form 画像化元PDF、画像形式、解像度、対象ページを含むフォーム
	 * @return 画像を格納したZIPのダウンロードレスポンス
	 * @throws PdfRenderDpiException 指定された解像度が {@code ghost.pdf.image.max-dpi} を超えた場合
	 */
	public ResponseEntity<Resource> exportPdfImages(PdfImagesRequest form) {
		int renderDpi = resolveRenderDpi(form.getDpi());
		Path inputPath = pdfLogic.loadPdf(form.getOriginalFile(), form.getPassword());
		Path imagesZipPath = pdfLogic.exportPdfImages(inputPath, form.getFormat(), renderDpi, properties.getMaxPages(),
				form.getImagePages());
		return ResponseUtils.downloadZip(IMAGES_ZIP_FILE_NAME, pdfLogic.openTemporaryFileForResponse(imagesZipPath));
	}

	/**
	 * アップロードされた画像を1つのPDFへまとめ、PDFレスポンスとして返却する。
	 *
	 * @param form PDF化する画像とページサイズを含むフォーム
	 * @return 生成したPDFのinline表示レスポンス
	 * @throws PdfImageInputException 画像として読めないファイルが含まれる場合
	 */
	public ResponseEntity<Resource> createPdfFromImages(PdfFromImagesRequest form) {
		PdfImagePageSize pageSize = Objects.isNull(form.getPageSize()) ? PdfImagePageSize.A4 : form.getPageSize();
		Path pdfPath = pdfLogic.createPdfFromImages(form.getImageFiles(), pageSize);
		return ResponseUtils.inlinePdf(pdfLogic.openTemporaryFileForResponse(pdfPath));
	}

	/**
	 * 使用する解像度を決める。
	 * <p>
	 * 未指定なら設定の既定値を使い、上限を超える指定は拒否する。上限で丸めずに拒否するのは、
	 * ZIPレスポンスには「丸めた」と伝える経路が無く、利用者が指定と違う画像を受け取ったことに気付けないため。
	 *
	 * @param requestedDpi リクエストで指定された解像度。未指定の場合はnull
	 * @return 画像化に使用する解像度
	 * @throws PdfRenderDpiException 指定された解像度が上限を超えた場合
	 */
	private int resolveRenderDpi(Integer requestedDpi) {
		if (Objects.isNull(requestedDpi)) {
			return properties.getDefaultDpi();
		}
		if (requestedDpi > properties.getMaxDpi()) {
			throw new PdfRenderDpiException(requestedDpi, properties.getMaxDpi());
		}
		return requestedDpi;
	}
}
