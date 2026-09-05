package com.clip.ghost.pdfcontent.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftPageResponse;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftResponse;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * PDFからページ単位のMarkdown下書きを生成するサービス。
 * <p>
 * PDFの保存とテキスト抽出は {@link GhostPdfLogic} へ委譲し、このクラスはページ番号の付与、
 * 抽出テキストの正規化、Markdown下書きとレスポンスDTOの組み立てを担当する。
 */
@Service
@RequiredArgsConstructor
public class PdfMarkdownDraftService {
	private static final String PAGE_HEADING_PREFIX = "## Page ";
	private static final String BLOCK_SEPARATOR = "\n\n";

	private final GhostPdfLogic pdfLogic;

	/**
	 * アップロードされたPDFからページ単位のMarkdown下書きを生成する。
	 *
	 * @param form Markdown下書きの生成元PDFを含むフォーム
	 * @return PDF情報、ページ単位テキスト、Markdown下書きを含むレスポンス
	 */
	public ResponseEntity<PdfMarkdownDraftResponse> generateMarkdownDraft(PdfMarkdownDraftRequest form) {
		MultipartFile originalFile = form.getOriginalFile();
		Path inputPath = pdfLogic.loadPdf(originalFile);
		List<String> pageTexts = pdfLogic.extractPdfPageTexts(inputPath);
		List<PdfMarkdownDraftPageResponse> pages = buildPageResponses(pageTexts);
		return ResponseEntity.ok(buildResponse(originalFile, pages));
	}

	/**
	 * PDF順の抽出テキストへ1始まりのページ番号を付与する。
	 *
	 * @param pageTexts PDF順のページ単位抽出テキスト
	 * @return 正規化済みページレスポンス
	 */
	private List<PdfMarkdownDraftPageResponse> buildPageResponses(List<String> pageTexts) {
		List<PdfMarkdownDraftPageResponse> pages = new ArrayList<>(pageTexts.size());
		for (int index = 0; index < pageTexts.size(); index++) {
			pages.add(buildPageResponse(index + PdfConstants.START_PAGE, normalizePageText(pageTexts.get(index))));
		}
		return pages;
	}

	/**
	 * 1ページ分のレスポンスDTOを生成する。
	 *
	 * @param pageNumber 1始まりのページ番号
	 * @param text       正規化済みページ本文
	 * @return ページレスポンスDTO
	 */
	private PdfMarkdownDraftPageResponse buildPageResponse(int pageNumber, String text) {
		PdfMarkdownDraftPageResponse page = new PdfMarkdownDraftPageResponse();
		page.setPageNumber(pageNumber);
		page.setText(text);
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
