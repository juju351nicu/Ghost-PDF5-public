package com.clip.ghost.pdfcontent.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.pdfcontent.config.PdfThumbnailProperties;
import com.clip.ghost.pdfcontent.dto.PdfPageThumbnail;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailRequest;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailResponse;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * PDFのページ選択用サムネイルを生成するサービス。
 * <p>
 * PDFの保存と画像化は {@link GhostPdfLogic} へ委譲し、このクラスはレスポンスDTOの組み立てを担当する。
 * 解像度とページ数上限は {@link PdfThumbnailProperties}（{@code ghost.pdf.thumbnail}）から取る。
 */
@Service
@RequiredArgsConstructor
public class PdfThumbnailService {

	private final GhostPdfLogic pdfLogic;
	private final PdfThumbnailProperties properties;

	/**
	 * アップロードされたPDFの全ページからサムネイルを生成する。
	 *
	 * @param form サムネイルの生成元PDFを含むフォーム
	 * @return PDF情報とページ単位サムネイルを含むレスポンス
	 * @throws PdfPageLimitExceededException 総ページ数が {@code ghost.pdf.thumbnail.max-pages} を超えた場合
	 */
	public ResponseEntity<ApiResult<PdfThumbnailResponse>> generateThumbnails(PdfThumbnailRequest form) {
		MultipartFile originalFile = form.getOriginalFile();
		Path inputPath = pdfLogic.loadPdf(originalFile);
		List<PdfPageThumbnail> thumbnails = pdfLogic.extractPdfThumbnails(inputPath, properties.getDpi(),
				properties.getMaxPages());
		return ResponseEntity.ok(ApiResult.of(buildResponse(originalFile, thumbnails)));
	}

	/**
	 * アップロード情報とサムネイルからレスポンスDTOを生成する。
	 *
	 * @param originalFile アップロードされた元PDF
	 * @param thumbnails   PDF順のページ単位サムネイル
	 * @return サムネイルレスポンスDTO
	 */
	private PdfThumbnailResponse buildResponse(MultipartFile originalFile, List<PdfPageThumbnail> thumbnails) {
		PdfThumbnailResponse response = new PdfThumbnailResponse();
		response.setFileName(originalFile.getOriginalFilename());
		response.setPageCount(thumbnails.size());
		response.setPages(thumbnails.stream().map(this::buildPageResponse).toList());
		return response;
	}

	/**
	 * 1ページ分のサムネイルレスポンスDTOを生成する。
	 *
	 * @param thumbnail ページ単位サムネイル
	 * @return サムネイルページレスポンスDTO
	 */
	private PdfThumbnailPageResponse buildPageResponse(PdfPageThumbnail thumbnail) {
		PdfThumbnailPageResponse page = new PdfThumbnailPageResponse();
		page.setPageNumber(thumbnail.pageNumber());
		page.setDataUri(thumbnail.dataUri());
		page.setWidth(thumbnail.width());
		page.setHeight(thumbnail.height());
		return page;
	}
}
