package com.clip.ghost.pdfcontent.controller;

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
import com.clip.ghost.pdfcontent.dto.PdfThumbnailRequest;
import com.clip.ghost.pdfcontent.dto.PdfThumbnailResponse;
import com.clip.ghost.pdfcontent.service.PdfThumbnailService;

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
 * PDFのページ選択用サムネイルを返すAPIを提供するコントローラー。
 * <p>
 * 既存PDF編集APIを提供する {@link GhostPdfController} へ新機能を追加せず、token検証、
 * multipart requestのvalidation、ファイルサイズ検証、Service委譲をこのクラスに閉じ込める。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class PdfThumbnailController {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfThumbnailController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final PdfThumbnailService thumbnailService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされたPDFの全ページからサムネイルを生成する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        サムネイルの生成元PDFを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return PDF情報とページ単位サムネイルを含むレスポンス
	 */
	@Operation(summary = "ページ選択用サムネイル取得", description = "アップロードされたPDFの全ページを低解像度で画像化し、data URI形式のサムネイルを返却します。ページ単位のリクエストは受け付けず、1リクエストで全ページ分を返します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PdfThumbnailRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ページ単位サムネイル"),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。総ページ数が上限を超えた場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "サムネイル生成に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/thumbnailsPdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<ApiResult<PdfThumbnailResponse>> generateThumbnails(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute PdfThumbnailRequest form, HttpSession session) {
		LOGGER.info("ページ選択用サムネイルを生成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form.getOriginalFile());
		return thumbnailService.generateThumbnails(form);
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
