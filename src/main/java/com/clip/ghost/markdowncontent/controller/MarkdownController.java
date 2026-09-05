package com.clip.ghost.markdowncontent.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.markdowncontent.dto.MarkdownDeleteResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownDocumentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownFileResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewContentResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownPreviewResponse;
import com.clip.ghost.markdowncontent.dto.MarkdownSaveRequest;
import com.clip.ghost.markdowncontent.dto.MarkdownUpdateRequest;
import com.clip.ghost.markdowncontent.service.MarkdownDocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Markdown文書の保存APIを提供するコントローラー。
 * <p>
 * PDF編集画面で発行した一時トークンを利用し、Markdown保存も同じ画面セッションに閉じ込める。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class MarkdownController {
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";

	private final MarkdownDocumentService markdownDocumentService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * 保存済みMarkdownファイル一覧を返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 保存済みMarkdownファイル一覧
	 */
	@Operation(summary = "Markdown一覧取得", description = "保存済みMarkdownファイルの一覧と最小ファイル情報を返却します。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdownファイル一覧", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = MarkdownFileResponse.class)))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "Markdown一覧取得に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@GetMapping(value = "/markdownFiles", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<List<MarkdownFileResponse>> listMarkdownFiles(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			HttpSession session) {
		LOGGER.info("Markdown一覧を取得します。");
		accessTokenValidator.validate(accessToken, session);
		return markdownDocumentService.listMarkdownFiles();
	}

	/**
	 * 保存済みMarkdown本文を返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param fileName    読み取り対象Markdownファイル名
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 保存済みMarkdown本文
	 */
	@Operation(summary = "Markdown本文取得", description = "保存済みMarkdownファイルの本文と最小ファイル情報を返却します。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdown本文", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MarkdownDocumentResponse.class))),
			@ApiResponse(responseCode = "400", description = "ファイル名が不正です。"),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "404", description = "Markdownファイルが見つかりません。"),
			@ApiResponse(responseCode = "500", description = "Markdown本文取得に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@GetMapping(value = "/markdownFile", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<MarkdownDocumentResponse> getMarkdownFile(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Parameter(description = "読み取り対象Markdownファイル名。", required = true) @RequestParam String fileName,
			HttpSession session) {
		LOGGER.info("Markdown本文を取得します。fileName={}", fileName);
		accessTokenValidator.validate(accessToken, session);
		return markdownDocumentService.getMarkdownFile(fileName);
	}

	/**
	 * 保存済みMarkdownのHTMLプレビューを返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param fileName    プレビュー対象Markdownファイル名
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return Markdown HTMLプレビュー
	 */
	@Operation(summary = "Markdownプレビュー取得", description = "保存済みMarkdownファイルをHTMLへ変換し、最小ファイル情報とともに返却します。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdown HTMLプレビュー", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MarkdownPreviewResponse.class))),
			@ApiResponse(responseCode = "400", description = "ファイル名が不正です。"),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "404", description = "Markdownファイルが見つかりません。"),
			@ApiResponse(responseCode = "500", description = "Markdownプレビュー取得に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@GetMapping(value = "/markdownPreview", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<MarkdownPreviewResponse> previewMarkdownFile(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Parameter(description = "プレビュー対象Markdownファイル名。", required = true) @RequestParam String fileName,
			HttpSession session) {
		LOGGER.info("Markdownプレビューを取得します。fileName={}", fileName);
		accessTokenValidator.validate(accessToken, session);
		return markdownDocumentService.previewMarkdownFile(fileName);
	}

	/**
	 * Markdown本文のHTMLプレビューを返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param request     Markdown本文プレビューリクエスト
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return Markdown本文HTMLプレビュー
	 */
	@Operation(summary = "Markdown本文プレビュー", description = "入力中のMarkdown本文をHTMLへ変換して返却します。保存済みファイルは更新しません。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdown本文HTMLプレビュー", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MarkdownPreviewContentResponse.class))),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "Markdown本文プレビューに失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/markdownPreview", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<MarkdownPreviewContentResponse> previewMarkdownContent(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @RequestBody MarkdownPreviewRequest request, HttpSession session) {
		LOGGER.info("Markdown本文プレビューを取得します。");
		accessTokenValidator.validate(accessToken, session);
		return markdownDocumentService.previewMarkdownContent(request);
	}

	/**
	 * 保存済みMarkdown本文を更新する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param fileName    更新対象Markdownファイル名
	 * @param request     Markdown更新リクエスト
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return Markdown更新後メタデータ
	 */
	@Operation(summary = "Markdown更新", description = "保存済みMarkdownファイルの本文をUTF-8で更新し、更新後のファイル情報を返却します。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdown更新レスポンス", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MarkdownFileResponse.class))),
			@ApiResponse(responseCode = "400", description = "入力値またはファイル名が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "404", description = "Markdownファイルが見つかりません。"),
			@ApiResponse(responseCode = "500", description = "Markdown更新に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PutMapping(value = "/markdownFile", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<MarkdownFileResponse> updateMarkdownFile(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Parameter(description = "更新対象Markdownファイル名。", required = true) @RequestParam String fileName,
			@Valid @RequestBody MarkdownUpdateRequest request, HttpSession session) {
		LOGGER.info("Markdownを更新します。fileName={}", fileName);
		accessTokenValidator.validate(accessToken, session);
		return markdownDocumentService.updateMarkdownFile(fileName, request);
	}

	/**
	 * 保存済みMarkdownファイルを削除する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param fileName    削除対象Markdownファイル名
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return Markdown削除レスポンス
	 */
	@Operation(summary = "Markdown削除", description = "保存済みMarkdownファイルを削除し、削除結果を返却します。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdown削除レスポンス", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MarkdownDeleteResponse.class))),
			@ApiResponse(responseCode = "400", description = "ファイル名が不正です。"),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "404", description = "Markdownファイルが見つかりません。"),
			@ApiResponse(responseCode = "500", description = "Markdown削除に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@DeleteMapping(value = "/markdownFile", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<MarkdownDeleteResponse> deleteMarkdownFile(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Parameter(description = "削除対象Markdownファイル名。", required = true) @RequestParam String fileName,
			HttpSession session) {
		LOGGER.info("Markdownを削除します。fileName={}", fileName);
		accessTokenValidator.validate(accessToken, session);
		return markdownDocumentService.deleteMarkdownFile(fileName);
	}

	/**
	 * Markdown本文を保存し、保存後の最小メタデータを返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param request     Markdown保存リクエスト
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return Markdown保存レスポンス
	 */
	@Operation(summary = "Markdown保存", description = "Markdown本文をUTF-8の.mdファイルとして保存し、保存後のファイル情報を返却します。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Markdown保存レスポンス", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MarkdownFileResponse.class))),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "Markdown保存に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/saveMarkdown", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<MarkdownFileResponse> saveMarkdown(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @RequestBody MarkdownSaveRequest request, HttpSession session) {
		LOGGER.info("Markdownを保存します。");
		accessTokenValidator.validate(accessToken, session);
		return markdownDocumentService.saveMarkdown(request);
	}
}
