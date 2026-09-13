package com.clip.ghost.officecontent.controller;

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
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.officecontent.dto.OfficeMarkdownRequest;
import com.clip.ghost.officecontent.dto.OfficeMarkdownResponse;
import com.clip.ghost.officecontent.service.OfficeMarkdownService;
import com.clip.ghost.officecontent.service.OfficePdfService;
import com.clip.ghost.pdfcontent.constant.PdfConstants;

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
 * Office文書をMarkdown / PDFへ変換するAPIを提供するコントローラー。
 * <p>
 * token検証、multipart requestのvalidation、ファイルサイズ検証、Service委譲をこのクラスに閉じ込める。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class OfficeMarkdownController {
	private static final Logger LOGGER = LoggerFactory.getLogger(OfficeMarkdownController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final OfficeMarkdownService officeMarkdownService;
	private final OfficePdfService officePdfService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされたOffice文書からMarkdownを起こす。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        Markdownへ変換するOffice文書を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 変換元情報とMarkdown本文を含むレスポンス
	 */
	@Operation(summary = "Office文書からMarkdown", description = "アップロードされた .docx / .xlsx / .pptx からMarkdownを起こします。形式はファイルの拡張子から判定します。内容レベルの変換であり、レイアウト・書式・図の配置は再現しません。旧形式（.doc / .xls / .ppt）は対象外で400になります。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OfficeMarkdownRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "起こしたMarkdown"),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。対応していない形式、壊れたファイルの場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "Office文書の読み取りに失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/markdownDraftOffice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<ApiResult<OfficeMarkdownResponse>> generateMarkdown(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OfficeMarkdownRequest form, HttpSession session) {
		LOGGER.info("Office文書からMarkdownを起こします。");
		accessTokenValidator.validate(accessToken, session);
		validateOfficeFileSize(form.getOfficeFile());
		return officeMarkdownService.generateMarkdown(form);
	}

	/**
	 * アップロードされたOffice文書をPDFへ変換し、ダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        PDFへ変換するOffice文書を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用PDFレスポンス
	 */
	@Operation(summary = "Office文書からPDF出力", description = "アップロードされた .docx / .xlsx / .pptx をPDFへ変換します。WordとExcelはMarkdownを経由する内容レベルの変換で、レイアウト・書式は再現しません。PowerPointはスライドを画像化してページへ貼るため見た目は保たれますが、PDFのテキストは選択できません。旧形式（.doc / .xls / .ppt）は対象外で400になります。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OfficeMarkdownRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したPDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。対応していない形式、壊れたファイルの場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "Office文書の処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/pdfFromOffice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> generatePdf(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OfficeMarkdownRequest form, HttpSession session) {
		LOGGER.info("Office文書からPDFを生成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOfficeFileSize(form.getOfficeFile());
		return officePdfService.generatePdf(form);
	}

	/**
	 * アップロードファイルのサイズを既存PDF APIと同じ境界で検証する。
	 * <p>
	 * Office文書にもPDFと同じ上限を使う。上限を種類ごとに分けると、どれが適用されたのか利用者が判断できなくなる。
	 *
	 * @param officeFile アップロードされたOffice文書
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateOfficeFileSize(MultipartFile officeFile) {
		if (officeFile.getSize() >= PdfConstants.MAX_PDF_FILE_SIZE_BYTES) {
			throw new MultipartException("サイズの超過");
		}
	}
}
