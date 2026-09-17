package com.clip.ghost.aicontent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;

import com.clip.ghost.aicontent.dto.MarkdownAiTransformRequest;
import com.clip.ghost.aicontent.dto.MarkdownAiTransformResponse;
import com.clip.ghost.aicontent.service.MarkdownAiService;
import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.security.AccessTokenValidator;

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
 * Markdown本文をAIで整形・要約するAPIを提供するコントローラー。
 * <p>
 * 既存の画像Markdown下書きAPI（{@code /markdownDraftImage}）とは別のAPIで、既存のMarkdown保存・プレビュー・
 * PDF出力の挙動には影響しない。token検証、validation、Service委譲をこのクラスに閉じ込める。
 * 変換手段（Anthropic / OpenAI）はService以下に隠す。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class MarkdownAiController {
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownAiController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final MarkdownAiService markdownAiService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * Markdown本文をAIで整形・要約する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param request     変換対象本文とタスクを含むリクエスト
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 変換結果を含むレスポンス
	 */
	@Operation(summary = "Markdown本文AI整形・要約", description = "入力中/編集中のMarkdown本文を外部AI（Anthropic / OpenAI）で整形または要約し、"
			+ "結果のMarkdownを返却します。既定では無効で、有効化した場合のみ外部AIへ本文を送信します。自動保存は行いません。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdown本文AI整形・要約結果"),
			@ApiResponse(responseCode = "400", description = "入力値が不正、または入力文字数が上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "Markdown本文AI整形・要約に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "503", description = "Markdown本文AI整形・要約機能が無効です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/markdownAiTransform", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<ApiResult<MarkdownAiTransformResponse>> transformMarkdown(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @RequestBody MarkdownAiTransformRequest request, HttpSession session) {
		LOGGER.info("Markdown本文をAIで変換します。task={}", request.getTask());
		accessTokenValidator.validate(accessToken, session);
		return markdownAiService.transformMarkdown(request);
	}
}
