package com.clip.ghost.markdowncontent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.markdowncontent.dto.MarkdownPdfRequest;
import com.clip.ghost.markdowncontent.dto.PdfFromEpubRequest;
import com.clip.ghost.markdowncontent.dto.PdfFromHtmlRequest;
import com.clip.ghost.markdowncontent.service.MarkdownPdfService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Markdown本文からPDFを出力するAPIを提供するコントローラー。
 * <p>
 * Markdownの保存・一覧・プレビューを持つ {@link MarkdownController} へ追加せず、PDF出力の入口をこのクラスへ分ける。
 * レスポンスがJSONではなくPDFバイナリで、扱うヘッダーもエラーの見え方も違うため。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class MarkdownPdfController {
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownPdfController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final MarkdownPdfService markdownPdfService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * Markdown本文からPDFを生成し、ダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param request     Markdown本文とファイル名を含むリクエスト
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用PDFレスポンス
	 */
	@Operation(summary = "MarkdownからPDF出力", description = "Markdown本文をHTMLへ変換してPDFを生成し、ダウンロードとして返却します。生成したPDFは保存しません。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したPDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "PDF出力に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/markdownPdf", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> generateMarkdownPdf(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @RequestBody MarkdownPdfRequest request, HttpSession session) {
		LOGGER.info("MarkdownからPDFを生成します。");
		accessTokenValidator.validate(accessToken, session);
		return markdownPdfService.generatePdf(request);
	}

	/**
	 * アップロードされたHTMLからPDFを生成し、ダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        PDFへ変換するHTMLファイルとファイル名を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用PDFレスポンス
	 */
	@Operation(summary = "HTMLからPDF出力", description = "アップロードされたHTMLをPDFへ描画し、ダウンロードとして返却します。HTMLはMarkdown経由の出力と同じ許可範囲でsanitizeします。外部CSS・外部画像は取り込まないため、見た目を保つにはHTML内でスタイルを完結させてください。", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PdfFromHtmlRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したPDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "PDF出力に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/pdfFromHtml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> generatePdfFromHtml(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute PdfFromHtmlRequest form, HttpSession session) {
		LOGGER.info("HTMLからPDFを生成します。");
		accessTokenValidator.validate(accessToken, session);
		return markdownPdfService.generatePdfFromHtml(form);
	}

	/**
	 * アップロードされたEPUBからPDFを生成し、ダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        PDFへ変換するEPUBファイルとファイル名を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用PDFレスポンス
	 */
	@Operation(summary = "EPUBからPDF出力", description = "アップロードされたEPUBの本文をspine（読む順序）どおりに連結してPDFへ描画し、ダウンロードとして返却します。EPUB内のCSS・画像・フォントは取り込まないため、リーダーで開いたときの見た目とは一致しません。", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PdfFromEpubRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したPDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "EPUBを読めない、またはPDF出力に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/pdfFromEpub", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> generatePdfFromEpub(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute PdfFromEpubRequest form, HttpSession session) {
		LOGGER.info("EPUBからPDFを生成します。");
		accessTokenValidator.validate(accessToken, session);
		return markdownPdfService.generatePdfFromEpub(form);
	}
}
