package com.clip.ghost.pdfcontent.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.response.ApiMessage;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;
import com.clip.ghost.imagecontent.logic.ImageConverterResolver;
import com.clip.ghost.imagecontent.logic.ImageToMarkdownConverter;
import com.clip.ghost.pdfcontent.config.PdfOcrProperties;
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * PDFからページ単位のMarkdown下書きを生成するサービス。
 * <p>
 * PDFの保存とテキスト抽出は {@link GhostPdfLogic} へ委譲し、このクラスはページ番号の付与、
 * 抽出テキストの正規化、Markdown下書きとレスポンスDTOの組み立てを担当する。
 * {@code mode=AUTO} では、文字を取得できないページを画像化し、共有の画像変換器（OCR/vision）で補完する。
 * 変換にかけるページ数の上限とレンダリング解像度は {@link PdfOcrProperties}（{@code ghost.ocr.pdf}）から取る。
 */
@Service
@RequiredArgsConstructor
public class PdfMarkdownDraftService {
	private static final String PAGE_HEADING_PREFIX = "## Page ";
	private static final String BLOCK_SEPARATOR = "\n\n";
	private static final String MODE_AUTO = "AUTO";
	private static final String SOURCE_TEXT = "TEXT";
	private static final String SOURCE_OCR = "OCR";
	private static final String SOURCE_FAILED = "FAILED";
	private static final String IMAGE_MEDIA_TYPE = "image/png";
	private static final String PARTIAL_FAILURE_CODE = "ocrPagePartiallyFailed";
	private static final String PAGE_NUMBER_SEPARATOR = ", ";

	private final GhostPdfLogic pdfLogic;
	private final ImageConverterResolver converterResolver;
	private final PdfOcrProperties properties;

	/**
	 * アップロードされたPDFからページ単位のMarkdown下書きを生成する。
	 * <p>
	 * AUTOモードで一部のページだけ変換に失敗した場合は、成功したページを返しつつ結果種別をWARNINGにする。
	 * 変換対象があって1ページも成功しなかった場合はLogic側が例外にするため、ここへは戻ってこない。
	 *
	 * @param form Markdown下書きの生成元PDFと変換モードを含むフォーム
	 * @return PDF情報、ページ単位テキスト、Markdown下書きを含むレスポンス
	 * @throws OcrUnavailableException       AUTOモードで画像変換が無効、またはproviderが未対応の場合
	 * @throws PdfPageLimitExceededException AUTOモードで変換対象ページ数が {@code ghost.ocr.pdf.max-pages} を超えた場合
	 */
	public ResponseEntity<ApiResult<PdfMarkdownDraftResponse>> generateMarkdownDraft(PdfMarkdownDraftRequest form) {
		MultipartFile originalFile = form.getOriginalFile();
		if (!isAutoMode(form.getMode())) {
			return ResponseEntity.ok(ApiResult.of(buildResponse(originalFile, buildTextPages(originalFile))));
		}
		List<PdfPageContent> contents = extractAutoPageContents(originalFile);
		PdfMarkdownDraftResponse response = buildResponse(originalFile, buildAutoPages(contents));
		List<Integer> failedPageNumbers = collectConversionFailedPageNumbers(contents);
		if (failedPageNumbers.isEmpty()) {
			return ResponseEntity.ok(ApiResult.of(response));
		}
		return ResponseEntity.ok(ApiResult.warning(response, List.of(buildPartialFailureMessage(failedPageNumbers))));
	}

	/**
	 * 変換モードがAUTOか判定する。
	 *
	 * @param mode リクエストの変換モード
	 * @return AUTOの場合true
	 */
	private boolean isAutoMode(String mode) {
		return Strings.CI.equals(MODE_AUTO, mode);
	}

	/**
	 * 文字レイヤーだけを使う従来動作でページレスポンスを生成する。
	 *
	 * @param originalFile アップロードされた元PDF
	 * @return PDF順のページレスポンス
	 */
	private List<PdfMarkdownDraftPageResponse> buildTextPages(MultipartFile originalFile) {
		Path inputPath = pdfLogic.loadPdf(originalFile);
		List<String> pageTexts = pdfLogic.extractPdfPageTexts(inputPath);
		List<PdfMarkdownDraftPageResponse> pages = new ArrayList<>(pageTexts.size());
		for (int index = 0; index < pageTexts.size(); index++) {
			pages.add(buildPageResponse(index + PdfConstants.START_PAGE, normalizePageText(pageTexts.get(index)),
					SOURCE_TEXT));
		}
		return pages;
	}

	/**
	 * 文字を取得できないページを画像化し、共有の画像変換器で補完したページ内容を取得する。
	 * <p>
	 * 変換器の有効性は入力PDFを保存する前に確認し、無効時は入力一時ファイルを作らずに503相当で止める。
	 * 変換対象ページ数の上限判定はLogic側が画像化前に行うため、上限超過時は変換器が1度も呼ばれない。
	 *
	 * @param originalFile アップロードされた元PDF
	 * @return PDF順のページ内容
	 * @throws OcrUnavailableException       画像変換が無効、またはproviderが未対応の場合
	 * @throws PdfPageLimitExceededException 変換対象ページ数が上限を超えた場合
	 */
	private List<PdfPageContent> extractAutoPageContents(MultipartFile originalFile) {
		ImageToMarkdownConverter converter = converterResolver.resolve();
		if (!converter.isEnabled()) {
			throw new OcrUnavailableException("画像PDFのOCRは無効です。providerを有効化してください。");
		}
		Path inputPath = pdfLogic.loadPdf(originalFile);
		return pdfLogic.extractPdfPageContents(inputPath, properties.getRenderDpi(), properties.getMaxPages(),
				pngBytes -> converter.convert(pngBytes, IMAGE_MEDIA_TYPE));
	}

	/**
	 * 画像変換まで済んだページ内容からページレスポンスを生成する。
	 *
	 * @param contents PDF順のページ内容
	 * @return PDF順のページレスポンス
	 */
	private List<PdfMarkdownDraftPageResponse> buildAutoPages(List<PdfPageContent> contents) {
		return contents.stream().map(this::buildAutoPage).toList();
	}

	/**
	 * 画像変換に失敗したページ番号を抽出する。
	 *
	 * @param contents PDF順のページ内容
	 * @return 変換に失敗したページ番号（1始まり、ページ順）
	 */
	private List<Integer> collectConversionFailedPageNumbers(List<PdfPageContent> contents) {
		return contents.stream().filter(PdfPageContent::conversionFailed).map(PdfPageContent::pageNumber).toList();
	}

	/**
	 * 一部ページの変換失敗を伝える画面表示用メッセージを生成する。
	 * <p>
	 * 失敗したページ番号を含める。利用者は失敗したページだけを画像として文字起こしし直せるため、
	 * 件数だけ伝えるより対処に直結する。変換対象は {@code ghost.ocr.pdf.max-pages}（既定20）で上限があるため、
	 * 全件を並べてもメッセージが極端に長くならない。
	 *
	 * @param failedPageNumbers 変換に失敗したページ番号（1始まり、ページ順）
	 * @return 部分失敗を伝えるメッセージ
	 */
	private ApiMessage buildPartialFailureMessage(List<Integer> failedPageNumbers) {
		String pageNumbers = failedPageNumbers.stream().map(String::valueOf)
				.collect(Collectors.joining(PAGE_NUMBER_SEPARATOR));
		return new ApiMessage(PARTIAL_FAILURE_CODE,
				failedPageNumbers.size() + "ページの文字起こしに失敗しました。（失敗したページ: " + pageNumbers + "）");
	}

	/**
	 * 1ページ分の内容を、文字レイヤー優先で、無ければ画像変換結果でページレスポンスへ変換する。
	 * <p>
	 * 文字レイヤーが空かどうかの判定はLogic側の1箇所に固定しているため、ここでは変換結果の有無だけで取得元を決める。
	 * 上限チェックで数えるページ集合と実際に変換されたページ集合をズラさないための分担。
	 * <p>
	 * 変換に失敗したページは本文を空にし、取得元を {@code FAILED} にする。{@code TEXT} のままにすると
	 * 「文字レイヤーが空の白紙ページ」と区別できず、利用者が失敗に気付けないため。
	 *
	 * @param content 1ページ分の抽出内容
	 * @return ページレスポンス
	 */
	private PdfMarkdownDraftPageResponse buildAutoPage(PdfPageContent content) {
		if (content.conversionFailed()) {
			return buildPageResponse(content.pageNumber(), "", SOURCE_FAILED);
		}
		if (content.convertedText() != null) {
			return buildPageResponse(content.pageNumber(), normalizePageText(content.convertedText()), SOURCE_OCR);
		}
		return buildPageResponse(content.pageNumber(), normalizePageText(content.text()), SOURCE_TEXT);
	}

	/**
	 * 1ページ分のレスポンスDTOを生成する。
	 *
	 * @param pageNumber 1始まりのページ番号
	 * @param text       正規化済みページ本文
	 * @param source     本文の取得元（TEXT / OCR / FAILED）
	 * @return ページレスポンスDTO
	 */
	private PdfMarkdownDraftPageResponse buildPageResponse(int pageNumber, String text, String source) {
		PdfMarkdownDraftPageResponse page = new PdfMarkdownDraftPageResponse();
		page.setPageNumber(pageNumber);
		page.setText(text);
		page.setSource(source);
		return page;
	}

	/**
	 * PDFBoxの抽出テキストをAPI契約に合わせて正規化する。
	 * <p>
	 * CRLFとCRをLFへ統一し、本文途中の空白は維持したまま末尾の空白文字だけを除去する。
	 *
	 * @param pageText PDFBoxから抽出した1ページ分のテキスト
	 * @return 正規化済みページ本文
	 */
	private String normalizePageText(String pageText) {
		return pageText.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
	}

	/**
	 * アップロード情報とページ本文からレスポンスDTOを生成する。
	 *
	 * @param originalFile アップロードされた元PDF
	 * @param pages        PDF順のページレスポンス
	 * @return Markdown下書きレスポンスDTO
	 */
	private PdfMarkdownDraftResponse buildResponse(MultipartFile originalFile,
			List<PdfMarkdownDraftPageResponse> pages) {
		PdfMarkdownDraftResponse response = new PdfMarkdownDraftResponse();
		response.setFileName(originalFile.getOriginalFilename());
		response.setFileSize(originalFile.getSize());
		response.setPageCount(pages.size());
		response.setMarkdown(buildMarkdown(pages));
		response.setPages(pages);
		return response;
	}

	/**
	 * ページ見出しと本文を空行1行で連結し、末尾改行のないMarkdownを生成する。
	 *
	 * @param pages PDF順のページレスポンス
	 * @return 全ページのMarkdown下書き
	 */
	private String buildMarkdown(List<PdfMarkdownDraftPageResponse> pages) {
		return pages.stream().map(this::buildPageMarkdown).collect(Collectors.joining(BLOCK_SEPARATOR));
	}

	/**
	 * 1ページ分の見出しと本文をMarkdownへ変換する。
	 *
	 * @param page 1ページ分のレスポンス
	 * @return ページ見出しと、本文がある場合はその本文
	 */
	private String buildPageMarkdown(PdfMarkdownDraftPageResponse page) {
		String heading = PAGE_HEADING_PREFIX + page.getPageNumber();
		return page.getText().isEmpty() ? heading : heading + BLOCK_SEPARATOR + page.getText();
	}
}
