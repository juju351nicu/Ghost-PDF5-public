package com.clip.ghost.imagecontent.controller;

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
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftRequest;
import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftResponse;
import com.clip.ghost.imagecontent.service.ImageMarkdownDraftService;

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
 * 画像からMarkdown下書きを生成するAPIを提供するコントローラー。
 * <p>
 * 既存PDF下書きAPIと対になる {@code /markdownDraftImage} を提供する。token検証、multipartのvalidation、
 * ファイルサイズ検証、Service委譲をこのクラスに閉じ込める。変換手段（vision等）はService以下に隠す。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class ImageMarkdownDraftController {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImageMarkdownDraftController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";
	private static final long MAX_IMAGE_FILE_SIZE_BYTES = 20_559_957L;

	private final ImageMarkdownDraftService imageMarkdownDraftService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * アップロードされた画像からMarkdown下書きを生成する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        Markdown下書きの生成元画像を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return ファイル情報とMarkdown下書きを含むレスポンス
	 */
	@Operation(summary = "画像Markdown下書き生成", description = "アップロードされた画像を外部visionモデルで文字起こしし、Markdown下書きを返却します。既定では無効で、有効化した場合のみ外部AIへ画像を送信します。自動保存は行いません。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = ImageMarkdownDraftRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "画像Markdown下書き", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ImageMarkdownDraftResponse.class))),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "画像Markdown下書き生成に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "503", description = "画像Markdown下書き機能が無効です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/markdownDraftImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<ImageMarkdownDraftResponse> generateMarkdownDraft(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute ImageMarkdownDraftRequest form, HttpSession session) {
		LOGGER.info("画像Markdown下書きを生成します。");
		accessTokenValidator.validate(accessToken, session);
		validateImageFileSize(form.getImageFile());
		return imageMarkdownDraftService.generateMarkdownDraft(form);
	}

	/**
	 * アップロードされた画像ファイルサイズを既存PDF APIと同じ境界で検証する。
	 *
	 * @param imageFile アップロードされた画像
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateImageFileSize(MultipartFile imageFile) {
		if (imageFile.getSize() >= MAX_IMAGE_FILE_SIZE_BYTES) {
			throw new MultipartException("サイズの超過");
		}
	}
}
