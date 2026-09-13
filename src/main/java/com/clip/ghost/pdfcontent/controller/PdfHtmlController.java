package com.clip.ghost.pdfcontent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfMarkdownDraftRequest;
import com.clip.ghost.pdfcontent.service.PdfHtmlService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * PDFをHTML / EPUBへ変換するAPIを提供するコントローラー。
 * <p>
 * リクエストの形はページ単位Markdown下書き（{@code POST /markdownDraftPdf}）と同じにする。
 * 内部でMarkdown下書きを経由するため、受け付けられる条件も結果の精度も下書きと同じであり、
 * 別のフォームを用意すると「同じPDFなのに下書きとHTMLで結果が違う」と誤解させる。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class PdfHtmlController {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfHtmlController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";
	private static final String MEDIA_TYPE_TEXT_HTML_VALUE = "text/html";
	private static final String MEDIA_TYPE_APPLICATION_EPUB_VALUE = "application/epub+zip";

	private final PdfHtmlService htmlService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされたPDFをHTMLへ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        変換元PDFと変換モードを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用HTMLレスポンス
	 */
	@Operation(summary = "PDFからHTML出力", description = "アップロードされたPDFをページ単位Markdownを経由してHTMLへ変換し、ダウンロードとして返却します。変換の精度と条件はPOST /markdownDraftPdf と同じで、modeもそのまま効きます。レイアウトは再現せず、見出しと本文の構造だけを起こします。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PdfMarkdownDraftRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したHTML", content = @Content(mediaType = MEDIA_TYPE_TEXT_HTML_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。変換対象ページ数が上限を超えた場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/htmlPdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MEDIA_TYPE_TEXT_HTML_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> generateHtml(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute PdfMarkdownDraftRequest form, HttpSession session) {
		LOGGER.info("PDFからHTMLを生成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form.getOriginalFile());
		return htmlService.generateHtml(form);
	}

	/**
	 * アップロードされたPDFをEPUBへ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        変換元PDFと変換モードを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用EPUBレスポンス
	 */
	@Operation(summary = "PDFからEPUB出力", description = "アップロードされたPDFをページ単位Markdownを経由してEPUBへ変換し、ダウンロードとして返却します。変換の精度と条件はPOST /markdownDraftPdf と同じで、modeもそのまま効きます。章分けはせず1章構成にします。レイアウトと画像は再現しません。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PdfMarkdownDraftRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したEPUB", content = @Content(mediaType = MEDIA_TYPE_APPLICATION_EPUB_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。変換対象ページ数が上限を超えた場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/epubPdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MEDIA_TYPE_APPLICATION_EPUB_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> generateEpub(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute PdfMarkdownDraftRequest form, HttpSession session) {
		LOGGER.info("PDFからEPUBを生成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form.getOriginalFile());
		return htmlService.generateEpub(form);
	}

	/**
	 * アップロードされたPDFファイルサイズを既存PDF APIと同じ境界で検証する。
	 *
	 * @param originalFile アップロードされたPDF
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateOriginalPdfFileSize(MultipartFile originalFile) {
		if (originalFile.getSize() >= PdfConstants.MAX_PDF_FILE_SIZE_BYTES) {
			throw new MultipartException("サイズの超過");
		}
	}
}
