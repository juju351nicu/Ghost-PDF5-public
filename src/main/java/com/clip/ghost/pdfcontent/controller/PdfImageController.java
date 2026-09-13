package com.clip.ghost.pdfcontent.controller;

import org.apache.commons.collections4.CollectionUtils;
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
import com.clip.ghost.pdfcontent.dto.PdfFromImagesRequest;
import com.clip.ghost.pdfcontent.dto.PdfImagesRequest;
import com.clip.ghost.pdfcontent.service.PdfImageService;

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
 * PDFと画像を相互に変換するAPIを提供するコントローラー。
 * <p>
 * 既存PDF編集APIを提供する {@link GhostPdfController} へ新機能を追加せず、token検証、
 * multipart requestのvalidation、ファイルサイズ検証、Service委譲をこのクラスに閉じ込める。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class PdfImageController {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfImageController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";
	private static final String MEDIA_TYPE_APPLICATION_ZIP_VALUE = "application/zip";

	private final PdfImageService imageService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされたPDFのページを画像化し、ZIPレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        画像化元PDF、画像形式、解像度、対象ページを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 画像を格納したZIPのダウンロードレスポンス
	 */
	@Operation(summary = "PDFページ画像化", description = "アップロードされたPDFのページをPNG / JPG / TIFF / BMPへ変換し、ページごとに1ファイルのZIPとして返却します。imagePagesを省略すると全ページを画像化します。ページ数が1枚でもZIPで返します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PdfImagesRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ページ画像のZIP", content = @Content(mediaType = MEDIA_TYPE_APPLICATION_ZIP_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。画像形式が対象外の場合、解像度が上限を超えた場合、対象ページ数が上限を超えた場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/imagesPdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MEDIA_TYPE_APPLICATION_ZIP_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> exportPdfImages(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute PdfImagesRequest form, HttpSession session) {
		LOGGER.info("PDFのページを画像化します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form.getOriginalFile());
		return imageService.exportPdfImages(form);
	}

	/**
	 * アップロードされた画像を1つのPDFへまとめ、PDFレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        PDF化する画像とページサイズを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 生成したPDFのinline表示レスポンス
	 */
	@Operation(summary = "画像からPDF作成", description = "アップロードされた画像を送信順にページへ並べ、1つのPDFとして返却します。PNG / JPEG / TIFF / BMPを指定できます。複数ページTIFFはファイル内のページ順に展開します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = PdfFromImagesRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "生成したPDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。画像として読み込めないファイルが含まれる場合、ページサイズが対象外の場合も400です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/pdfFromImages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> createPdfFromImages(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute PdfFromImagesRequest form, HttpSession session) {
		LOGGER.info("画像からPDFを作成します。");
		accessTokenValidator.validate(accessToken, session);
		CollectionUtils.emptyIfNull(form.getImageFiles()).forEach(this::validateOriginalPdfFileSize);
		return imageService.createPdfFromImages(form);
	}

	/**
	 * アップロードファイルのサイズを既存PDF APIと同じ境界で検証する。
	 * <p>
	 * 画像にもPDFと同じ上限を使う。上限を2種類に分けると、どちらが適用されたのか利用者が判断できなくなるため。
	 *
	 * @param originalFile アップロードされたファイル
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateOriginalPdfFileSize(MultipartFile originalFile) {
		if (originalFile.getSize() >= PdfConstants.MAX_PDF_FILE_SIZE_BYTES) {
			throw new MultipartException("サイズの超過");
		}
	}
}
