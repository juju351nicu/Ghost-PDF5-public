package com.clip.ghost.pdfcontent.controller;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.pdfcontent.dto.ExtractPdfRequest;
import com.clip.ghost.pdfcontent.dto.InsertPdfRequest;
import com.clip.ghost.pdfcontent.dto.MergePdfRequest;
import com.clip.ghost.pdfcontent.dto.OriginalPdfRequest;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.dto.SplitPdfRequest;
import com.clip.ghost.pdfcontent.service.GhostPdfService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * PDF編集画面とPDF操作APIを提供するコントローラー。
 * <p>
 * PDF操作APIは、トップ画面表示時に発行した一時トークンを {@code access-token}
 * ヘッダーで受け取り、セッション内のトークンと照合する。
 * URLやフォーム項目名は既存フロントエンドとの互換性を保つため、変更時は画面側のmultipart送信仕様もあわせて確認する。
 */
@Controller
@Validated
@RequiredArgsConstructor
public class GhostPdfController {
	private static final Logger LOGGER = LoggerFactory.getLogger(GhostPdfController.class);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final HexFormat HEX_FORMAT = HexFormat.of();
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String ACCESS_TOKEN_HEADER_DESCRIPTION = "トップ画面表示時に発行された一時トークン。Cookieのtoken値と同じ値を送信します。";
	private static final String TOKEN_COOKIE_NAME = "token";
	private static final String COOKIE_PATH_ROOT = "/";
	private static final String MEDIA_TYPE_APPLICATION_ZIP_VALUE = "application/zip";
	private static final int TOKEN_BYTE_LENGTH = 32;
	private static final int COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

	private final GhostPdfService pdfService;
	private final AccessTokenValidator accessTokenValidator;

	/**
	 * PDF編集画面を表示し、PDF操作API用の一時トークンをCookieとセッションに設定する。
	 *
	 * @param response Cookieを設定するレスポンス
	 * @param session  一時トークンを保存するHTTPセッション
	 * @return PDF編集画面のテンプレート名
	 */
	@Hidden
	@GetMapping("/")
	public String showMainPage(HttpServletResponse response, HttpSession session) {
		String token = generateToken();
		response.addCookie(buildAccessTokenCookie(token));
		storeAccessToken(session, token);
		return "main";
	}

	/**
	 * アップロードされたPDFをプレビュー表示用のPDFレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        編集元PDFを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return PDFのbyte配列レスポンス
	 */
	@Operation(summary = "PDFプレビュー", description = "アップロードされたPDFをブラウザ表示用のPDFレスポンスとして返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OriginalPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "PDF表示用レスポンス", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/showPdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> showPdfPreview(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OriginalPdfRequest form, HttpSession session) {
		LOGGER.info("プレビュー用PDFを表示します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form);
		return pdfService.showPdf(form);
	}

	/**
	 * アップロードされたPDFの基本メタデータを返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        編集元PDFを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return PDFの基本情報レスポンス
	 */
	@Operation(summary = "PDFメタデータ取得", description = "アップロードされたPDFのファイル名、サイズ、ページ数、暗号化有無を返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OriginalPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "PDFメタデータ", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PdfMetadataResponse.class))),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/metadataPdf", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<PdfMetadataResponse> getPdfMetadata(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OriginalPdfRequest form, HttpSession session) {
		LOGGER.info("PDFメタデータを取得します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form);
		return pdfService.getPdfMetadata(form);
	}

	/**
	 * アップロードされたPDFからテキストを抽出して返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        編集元PDFを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return PDFテキスト抽出レスポンス
	 */
	@Operation(summary = "PDFテキスト抽出", description = "アップロードされたPDFからPDFBoxで取得できるテキストを抽出して返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OriginalPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "PDFテキスト抽出レスポンス", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PdfTextResponse.class))),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDFテキスト抽出に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/textPdf", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<PdfTextResponse> extractPdfText(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OriginalPdfRequest form, HttpSession session) {
		LOGGER.info("PDFテキストを抽出します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form);
		return pdfService.extractPdfText(form);
	}

	/**
	 * アップロードされたPDFから指定ページだけを抽出し、PDFレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        編集元PDFと抽出ページ番号を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 抽出後PDFのbyte配列レスポンス
	 */
	@Operation(summary = "PDFページ抽出", description = "アップロードされたPDFから指定ページだけを抽出し、PDFレスポンスとして返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = ExtractPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ページ抽出後PDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/extractPdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> extractPdfPages(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute ExtractPdfRequest form, HttpSession session) {
		LOGGER.info("指定ページを抽出したPDFを作成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form);
		return pdfService.extractPdfByPages(form);
	}

	/**
	 * アップロードされた複数PDFを結合し、PDFレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        結合対象PDFを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 結合後PDFのbyte配列レスポンス
	 */
	@Operation(summary = "PDF結合", description = "アップロードされた複数PDFを送信順に結合し、PDFレスポンスとして返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = MergePdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "結合後PDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/mergePdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> mergePdfFiles(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute MergePdfRequest form, HttpSession session) {
		LOGGER.info("複数PDFを結合します。");
		accessTokenValidator.validate(accessToken, session);
		validateMergePdfFileSize(form);
		return pdfService.mergePdfs(form);
	}

	/**
	 * アップロードされたPDFを1ページずつ分割し、ZIPレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        分割対象PDFを含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 分割後PDFを格納したZIPのbyte配列レスポンス
	 */
	@Operation(summary = "PDF分割", description = "アップロードされたPDFを1ページずつ分割し、複数PDFをZIPレスポンスとして返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = SplitPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "分割後PDFのZIP", content = @Content(mediaType = MEDIA_TYPE_APPLICATION_ZIP_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/splitPdf", produces = MEDIA_TYPE_APPLICATION_ZIP_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> splitPdf(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute SplitPdfRequest form, HttpSession session) {
		LOGGER.info("PDFを1ページずつ分割します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form);
		return pdfService.splitPdf(form);
	}

	/**
	 * アップロードされたPDFから指定ページを削除し、PDFレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        編集元PDFと削除ページ番号を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 削除後PDFのbyte配列レスポンス
	 */
	@Operation(summary = "PDFページ削除", description = "アップロードされたPDFから指定ページを削除し、PDFレスポンスとして返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OriginalPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "ページ削除後PDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/deletePdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> deletePdfPages(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OriginalPdfRequest form, HttpSession session) {
		LOGGER.info("指定ページを削除したPDFを作成します。");
		accessTokenValidator.validate(accessToken, session);
		validateOriginalPdfFileSize(form);
		return pdfService.deletePdfByPages(form);
	}

	/**
	 * アップロードされたPDFへ別PDFを差し込み、PDFレスポンスとして返却する。
	 *
	 * @param accessToken リクエストヘッダーの一時トークン
	 * @param form        編集元PDF、削除ページ番号、差し込みPDF情報を含むフォーム
	 * @param session     トークン検証に使用するHTTPセッション
	 * @return 差し込み後PDFのbyte配列レスポンス
	 */
	@Operation(summary = "PDF差し込み", description = "アップロードされたPDFへ別PDFを差し込み、PDFレスポンスとして返却します。", requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = OriginalPdfRequest.class))))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "差し込み後PDF", content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
			@ApiResponse(responseCode = "400", description = "入力値が不正です。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "access-tokenが不正です。"),
			@ApiResponse(responseCode = "413", description = "アップロードファイルサイズが上限を超えています。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "500", description = "PDF処理に失敗しました。", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/insertPdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> insertPdfFiles(
			@Parameter(name = ACCESS_TOKEN_HEADER_NAME, in = ParameterIn.HEADER, required = true, description = ACCESS_TOKEN_HEADER_DESCRIPTION) @RequestHeader(ACCESS_TOKEN_HEADER_NAME) String accessToken,
			@Valid @ModelAttribute OriginalPdfRequest form, HttpSession session) {
		LOGGER.info("PDFの差し込み・差し替えを行います。");
		accessTokenValidator.validate(accessToken, session);
		validateInsertPdfFileSize(form);
		return pdfService.insertPdfs(form);
	}

	/**
	 * APIアクセストークンを保持するCookieを作成する。
	 *
	 * @param token APIアクセストークン
	 * @return レスポンスへ追加するCookie
	 */
	private Cookie buildAccessTokenCookie(String token) {
		Cookie cookie = new Cookie(TOKEN_COOKIE_NAME, token);
		cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
		cookie.setPath(COOKIE_PATH_ROOT);
		cookie.setSecure(false);
		return cookie;
	}

	/**
	 * APIアクセストークンをHTTPセッションへ保存する。
	 *
	 * @param session APIアクセストークンを保存するHTTPセッション
	 * @param token   APIアクセストークン
	 */
	private void storeAccessToken(HttpSession session, String token) {
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, token);
	}

	/**
	 * アップロードされた編集元PDFのファイルサイズを検証する。
	 *
	 * @param form 編集元PDFを含むフォーム
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateOriginalPdfFileSize(OriginalPdfRequest form) {
		validateOriginalPdfFileSize(form.getOriginalFile());
	}

	/**
	 * アップロードされた抽出元PDFのファイルサイズを検証する。
	 *
	 * @param form 抽出元PDFを含むフォーム
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateOriginalPdfFileSize(ExtractPdfRequest form) {
		validateOriginalPdfFileSize(form.getOriginalFile());
	}

	/**
	 * アップロードされた分割対象PDFのファイルサイズを検証する。
	 *
	 * @param form 分割対象PDFを含むフォーム
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateOriginalPdfFileSize(SplitPdfRequest form) {
		validateOriginalPdfFileSize(form.getOriginalFile());
	}

	/**
	 * アップロードされた結合対象PDFのファイルサイズを検証する。
	 *
	 * @param form 結合対象PDFを含むフォーム
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateMergePdfFileSize(MergePdfRequest form) {
		form.getMergeFiles().forEach(this::validateOriginalPdfFileSize);
	}

	/**
	 * アップロードされた編集元PDFと差し込みPDFのファイルサイズを検証する。
	 *
	 * @param form 編集元PDFと差し込みPDFを含むフォーム
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateInsertPdfFileSize(OriginalPdfRequest form) {
		validateOriginalPdfFileSize(form);
		CollectionUtils.emptyIfNull(form.getInsertPdfForm()).stream().filter(Objects::nonNull)
				.map(InsertPdfRequest::getInsertFile).filter(Objects::nonNull)
				.forEach(this::validateOriginalPdfFileSize);
	}

	/**
	 * アップロードされたPDFファイルサイズを検証する。
	 *
	 * @param originalFile アップロードされたPDF
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	private void validateOriginalPdfFileSize(MultipartFile originalFile) {
		if (originalFile.getSize() >= PdfConstants.MAX_PDF_FILE_SIZE_BYTES) {
			throw new MultipartException("サイズの超過");
		}
	}

	/**
	 * 推測されにくい一時トークンを生成する。
	 *
	 * @return 64文字の16進数トークン
	 */
	private String generateToken() {
		byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
		SECURE_RANDOM.nextBytes(tokenBytes);
		return HEX_FORMAT.formatHex(tokenBytes);
	}
}
