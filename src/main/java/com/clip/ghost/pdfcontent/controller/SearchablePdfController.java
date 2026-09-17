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
import com.clip.ghost.pdfcontent.dto.SearchablePdfRequest;
import com.clip.ghost.pdfcontent.service.SearchablePdfService;

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
 * 検索可能PDF（OCRサンドイッチPDF）生成APIを提供するコントローラー。
 * <p>
 * スキャン/画像PDFの各ページをローカルのTesseractでOCRし、認識した文字を元のPDFへ透明テキスト層として
 * 書き戻したPDFを返却する。既存PDF編集APIを提供する{@link GhostPdfController}へ新機能を追加せず、
 * token検証、multipart requestのvalidation、ファイルサイズ検証、Service委譲をこのクラスに閉じ込める。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class SearchablePdfController {
	private static final Logger LOGGER = LoggerFactory.getLogger(SearchablePdfController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final SearchablePdfService searchablePdfService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされたPDFから検索可能PDFを生成する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        OCR対象PDF、変換モード、パスワードを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 検索可能PDFのinline表示レスポンス
	 */
	@Operation(summary = "検索可能PDF生成", description = "アップロードされたPDFの各ページをローカルのTesseractでOCRし、認識した文字を透明テキスト層として"
			+ "元のPDFへ書き戻します。見た目は変更しません。mode=AUTOは文字レイヤーが無いページだけ、mode=FORCE_OCRは"
			+ "全ページをOCR対象にします（省略時はAUTO）。外部送信は行わず、ローカルのTesseractだけで処理します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = SearchablePdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "検索可能PDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。modeがAUTO / FORCE_OCR以外の場合、OCR対象ページ数が上限を超えた場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "検索可能PDFの生成に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "503", description = "検索可能PDF生成機能（Tesseract）が無効です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/searchablePdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> createSearchablePdf(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute SearchablePdfRequest form, HttpSession session) {
		LOGGER.info("検索可能PDFを生成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form.getOriginalFile());
		return searchablePdfService.createSearchablePdf(form);
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
