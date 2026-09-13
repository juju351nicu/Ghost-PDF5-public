package com.clip.ghost.exportcontent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.exportcontent.service.MarkdownFileCsvService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 保存済みMarkdownの一覧をCSVで出力するAPIを提供するコントローラー。
 * <p>
 * 旧サンプルの {@code CsvController}（{@code /showCSV} / {@code /printCSV}）とは別に置く。
 * あちらは固定文字列を返す学習用の残りで、こちらは実データを出すレポート機能のため、
 * 同じクラスへ混ぜると「どちらが本物か」が読めなくなる。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class MarkdownFileCsvController {
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownFileCsvController.class);
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";
	private static final String MEDIA_TYPE_TEXT_CSV_VALUE = "text/csv";

	private final MarkdownFileCsvService markdownFileCsvService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * 保存済みMarkdownの一覧をCSVのダウンロードレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param withBom     UTF-8のバイト順マークを付ける場合true
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return attachmentダウンロード用CSVレスポンス
	 */
	@Operation(summary = "保存済みMarkdown一覧のCSV出力", description = "保存済みMarkdownのファイル名・サイズ・行数・更新日時をCSVで返却します。既定ではExcelで開いても文字化けしないようUTF-8のBOMを付けます。BOMを嫌うツールへ渡す場合は withBom=false を指定します。")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "保存済みMarkdown一覧のCSV", content = @Content(mediaType = MEDIA_TYPE_TEXT_CSV_VALUE)),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "500", description = "一覧の取得またはCSV出力に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@GetMapping(value = "/markdownFilesCsv", produces = MEDIA_TYPE_TEXT_CSV_VALUE)
	@ResponseBody
	public ResponseEntity<Resource> exportMarkdownFileList(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Parameter(description = "UTF-8のバイト順マークを付けるか。既定はtrue。") @RequestParam(name = "withBom", defaultValue = "true") boolean withBom,
			HttpSession session) {
		LOGGER.info("保存済みMarkdown一覧をCSVへ出力します。");
		accessTokenValidator.validate(accessToken, session);
		return markdownFileCsvService.exportMarkdownFileList(withBom);
	}
}
