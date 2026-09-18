package com.clip.ghost.webcontent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftRequest;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftResponse;
import com.clip.ghost.webcontent.service.WebMarkdownService;

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
 * Webページ（HTML）をMarkdown下書きへ変換するAPIを提供するコントローラー。
 * <p>
 * token検証、multipart requestのvalidation、ファイルサイズ検証、Service委譲をこのクラスに閉じ込める。
 * <p>
 * この段階ではHTMLファイルのアップロードだけを受け付ける。URLを受け取ってサーバーが取得する
 * {@code POST /markdownDraftUrl} は Stage 2 として別に追加する。取り込み結果の整形・要約は
 * 既存の {@code POST /markdownAiTransform} に任せ、この機能へAI処理を足さない。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class WebMarkdownController {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebMarkdownController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final WebMarkdownService webMarkdownService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされたHTMLファイルからMarkdown下書きを起こす。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        変換対象のHTMLファイルとセレクタを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 起こしたMarkdown下書きを含むレスポンス
	 */
	@Operation(summary = "HTMLファイルからMarkdown下書き", description = "アップロードされたHTMLファイルから本文を取り出し、Markdown下書きを起こします。"
			+ "見出し・段落・リスト・表・コードブロック・引用・リンク・画像参照だけを変換し、レイアウトやスタイルは再現しません。"
			+ "画像は参照URLだけを残し、取得はしません。サーバーが外部へ接続することはありません。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = WebMarkdownDraftRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "起こしたMarkdown下書き"),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。対応していない拡張子、セレクタの書式不正、本文が空の場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "Webページ取り込みに失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/markdownDraftHtml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<ApiResult<WebMarkdownDraftResponse>> generateMarkdownFromHtml(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute WebMarkdownDraftRequest form, HttpSession session) {
		LOGGER.info("HTMLファイルからMarkdown下書きを起こします。");
		accessTokenValidator.validate(accessToken, session);
		validateHtmlFileSize(form.getHtmlFile());
		return webMarkdownService.generateMarkdownFromHtml(form);
	}

	/**
	 * アップロードファイルのサイズを既存PDF APIと同じ境界で検証する。
	 * <p>
	 * 種類ごとに上限を分けると、どの上限が適用されたのか利用者が判断できなくなるため、PDFと同じ値を使う。
	 *
	 * @param htmlFile アップロードされたHTMLファイル
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateHtmlFileSize(MultipartFile htmlFile) {
		if (htmlFile.getSize() >= PdfConstants.MAX_PDF_FILE_SIZE_BYTES) {
			throw new MultipartException("サイズの超過");
		}
	}
}
