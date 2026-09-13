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
import com.clip.ghost.pdfcontent.dto.OfficeFromPdfRequest;
import com.clip.ghost.pdfcontent.service.PdfOfficeService;

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
 * PDFをOffice文書へ変換するAPIを提供するコントローラー。
 * <p>
 * 出力形式ごとにパスを分けず、{@code format} で選ばせる。入力も処理の入口も同じで、
 * 違うのは書き出し側だけのため、3本へ割ると同じvalidationを3箇所へ持つことになる。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class PdfOfficeController {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfOfficeController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final PdfOfficeService officeService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされたPDFをOffice文書へ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        変換元PDFと出力形式を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用Office文書レスポンス
	 */
	@Operation(summary = "PDFからOffice文書出力", description = "アップロードされたPDFを .docx / .xlsx / .pptx へ変換します。DOCXとXLSXはページ単位のテキストを移す内容レベルの変換で、段組み・罫線・フォント・図の配置は再現しません。XLSXは1ページ1シートで、テキストの1行を1行目の列へ入れます（表としては復元しません）。PPTXはページを画像化してスライドへ貼るため見た目は保たれますが、文字は選択できません。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OfficeFromPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したOffice文書", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。出力形式が対象外の場合、PPTXで対象ページ数が上限を超えた場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "Office文書の生成に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/officeFromPdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> generateOffice(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OfficeFromPdfRequest form, HttpSession session) {
		LOGGER.info("PDFからOffice文書を生成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form.getOriginalFile());
		return officeService.generateOffice(form);
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
