package com.clip.ghost.markdowncontent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.markdowncontent.dto.MarkdownPdfRequest;
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
}
