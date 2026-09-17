package com.clip.ghost.pdfcontent.service;

import java.nio.file.Path;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.imagecontent.logic.TesseractWordBoxExtractor;
import com.clip.ghost.pdfcontent.config.PdfOcrProperties;
import com.clip.ghost.pdfcontent.dto.SearchablePdfRequest;
import com.clip.ghost.pdfcontent.enums.SearchablePdfMode;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;
import com.clip.ghost.pdfcontent.exception.SearchablePdfUnavailableException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * 検索可能PDF（OCRサンドイッチPDF）を生成するサービス。
 * <p>
 * PDFの保存と透明テキスト層の書き込みは{@link GhostPdfLogic}へ委譲し、このクラスは
 * Tesseractの有効性確認とPDFレスポンスの組み立てを担当する。OCR対象ページ数の上限とレンダリング解像度は
 * 既存の{@link PdfOcrProperties}（{@code ghost.ocr.pdf}）から取る（Markdown下書きのAUTO/VISIONと共用）。
 */
@Service
@RequiredArgsConstructor
public class SearchablePdfService {
	private final GhostPdfLogic pdfLogic;
	private final TesseractWordBoxExtractor wordBoxExtractor;
	private final PdfOcrProperties properties;

	/**
	 * アップロードされたPDFから検索可能PDFを生成する。
	 * <p>
	 * Tesseractの有効性は入力PDFを保存する前に確認し、無効時は入力一時ファイルを作らずに503相当で止める。
	 * OCR対象ページ数の上限判定はLogic側が画像化前に行うため、上限超過時はTesseractが1度も呼ばれない。
	 *
	 * @param form OCR対象PDF、変換モード、パスワードを含むフォーム
	 * @return 検索可能PDFのinline表示レスポンス
	 * @throws SearchablePdfUnavailableException Tesseractが無効な場合
	 * @throws PdfPageLimitExceededException     OCR対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException            PDFの読み込み、画像化、透明テキスト書き込み、または保存に失敗した場合
	 */
	public ResponseEntity<Resource> createSearchablePdf(SearchablePdfRequest form) {
		if (!wordBoxExtractor.isEnabled()) {
			throw new SearchablePdfUnavailableException("検索可能PDF生成機能は無効です。Tesseractを有効化してください。");
		}
		MultipartFile originalFile = form.getOriginalFile();
		Path inputPath = pdfLogic.loadPdf(originalFile, form.getPassword());
		SearchablePdfMode mode = resolveMode(form.getMode());
		Path outputPath = pdfLogic.createSearchablePdf(mode, properties.getRenderDpi(), properties.getMaxPages(),
				inputPath, wordBoxExtractor::extractWordBoxes);
		return ResponseUtils.inlinePdf(pdfLogic.openTemporaryFileForResponse(outputPath));
	}

	/**
	 * リクエストの変換モードを取得する。未指定の場合は{@link SearchablePdfMode#AUTO}を既定にする。
	 *
	 * @param mode リクエストの変換モード
	 * @return 変換モード。未指定の場合はAUTO
	 */
	private SearchablePdfMode resolveMode(SearchablePdfMode mode) {
		return Optional.ofNullable(mode).orElse(SearchablePdfMode.AUTO);
	}
}
