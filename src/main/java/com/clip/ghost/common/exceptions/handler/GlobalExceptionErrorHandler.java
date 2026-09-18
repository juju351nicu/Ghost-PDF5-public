package com.clip.ghost.common.exceptions.handler;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.clip.ghost.aicontent.exception.AiInputException;
import com.clip.ghost.aicontent.exception.AiProcessingException;
import com.clip.ghost.aicontent.exception.AiUnavailableException;
import com.clip.ghost.common.exceptions.CustomFieldError;
import com.clip.ghost.common.exceptions.ErrorResponse;
import com.clip.ghost.imagecontent.exception.ImageInputException;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;
import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;
import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.exception.OfficeProcessingException;
import com.clip.ghost.pdfcontent.exception.PdfImageInputException;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfPasswordProtectedException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;
import com.clip.ghost.pdfcontent.exception.PdfRenderDpiException;
import com.clip.ghost.pdfcontent.exception.PdfSplitRangeException;
import com.clip.ghost.pdfcontent.exception.SearchablePdfUnavailableException;
import com.clip.ghost.webcontent.exception.WebFetchBlockedException;
import com.clip.ghost.webcontent.exception.WebFetchException;
import com.clip.ghost.webcontent.exception.WebInputException;
import com.clip.ghost.webcontent.exception.WebProcessingException;
import com.clip.ghost.webcontent.exception.WebUnavailableException;

/**
 * アプリケーション共通の例外をHTTPレスポンスへ変換するREST用例外ハンドラー。
 * <p>
 * multipartアップロード失敗やPDF処理失敗を、FEの既存エラー表示で扱える {@code fieldErrors} 形式へ寄せる。
 */
@RestControllerAdvice
public class GlobalExceptionErrorHandler extends ResponseEntityExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionErrorHandler.class);
	private static final String CACHE_CONTROL_VALUE = "must-revalidate, post-check=0, pre-check=0";
	private static final String MULTIPART_ERROR_CODE = "multipartError";
	private static final String PDF_PROCESSING_ERROR_CODE = "pdfProcessingError";
	private static final String PDF_PAGE_LIMIT_ERROR_CODE = "pdfPageLimitExceeded";
	private static final String MULTIPART_ERROR_MESSAGE = "許可されないサイズのファイルが入っております。";
	private static final String PDF_PROCESSING_ERROR_MESSAGE = "PDF処理に失敗しました。入力ファイルを確認してください。";
	private static final String PDF_PAGE_LIMIT_ERROR_MESSAGE = "画像変換の対象ページ数が上限を超えています。対象 %d ページ / 上限 %d ページ";
	private static final String PDF_PAGE_LIMIT_ERROR_DETAIL_MESSAGE = "%s（%s）";
	private static final String PDF_PASSWORD_PROTECTED_ERROR_CODE = "pdfPasswordProtected";
	private static final String PDF_PASSWORD_INCORRECT_ERROR_CODE = "pdfPasswordIncorrect";
	private static final String PDF_PASSWORD_PROTECTED_ERROR_MESSAGE = "このPDFはパスワードで保護されています。PDFを開くパスワードを入力してください。";
	private static final String PDF_PASSWORD_INCORRECT_ERROR_MESSAGE = "パスワードが違うためPDFを開けません。もう一度入力してください。";
	private static final String PDF_IMAGE_INPUT_ERROR_CODE = "pdfImageInputError";
	private static final String PDF_IMAGE_INPUT_ERROR_MESSAGE = "「%s」を画像として読み込めません。PNG / JPEG / TIFF / BMPを指定してください。";
	private static final String PDF_RENDER_DPI_ERROR_CODE = "pdfRenderDpiExceeded";
	private static final String PDF_RENDER_DPI_ERROR_MESSAGE = "指定された解像度 %d dpi は上限を超えています。%d dpi以下で指定してください。";
	private static final String PDF_SPLIT_RANGE_ERROR_CODE = "pdfSplitRangeOutOfBounds";
	private static final String PDF_SPLIT_RANGE_ERROR_MESSAGE = "分割範囲「%s」はPDFのページ範囲外です。このPDFは全 %d ページです。";
	private static final String IMAGE_INPUT_ERROR_CODE = "imageInputError";
	private static final String IMAGE_PROCESSING_ERROR_CODE = "imageProcessingError";
	private static final String OCR_UNAVAILABLE_ERROR_CODE = "ocrUnavailable";
	private static final String IMAGE_INPUT_ERROR_MESSAGE = "画像として扱えないファイルです。PNG / JPEG / GIF / WEBPを指定してください。";
	private static final String IMAGE_PROCESSING_ERROR_MESSAGE = "画像Markdown下書き生成に失敗しました。";
	private static final String OCR_UNAVAILABLE_ERROR_MESSAGE = "画像Markdown下書き機能は無効です。";
	private static final String OFFICE_INPUT_ERROR_CODE = "officeInputError";
	private static final String OFFICE_INPUT_ERROR_MESSAGE = "「%s」をOffice文書として読み込めません。.docx / .xlsx / .pptx を指定してください。";
	private static final String OFFICE_PROCESSING_ERROR_CODE = "officeProcessingError";
	private static final String OFFICE_PROCESSING_ERROR_MESSAGE = "Office文書の処理に失敗しました。";
	private static final String MARKDOWN_PDF_ERROR_CODE = "markdownPdfError";
	private static final String MARKDOWN_PDF_ERROR_MESSAGE = "MarkdownからのPDF出力に失敗しました。";
	private static final String AI_INPUT_ERROR_CODE = "aiInputError";
	private static final String AI_INPUT_ERROR_MESSAGE = "入力文字数が上限を超えています。入力 %d 文字 / 上限 %d 文字";
	private static final String AI_PROCESSING_ERROR_CODE = "aiProcessingError";
	private static final String AI_PROCESSING_ERROR_MESSAGE = "Markdown本文のAI整形・要約に失敗しました。";
	private static final String AI_UNAVAILABLE_ERROR_CODE = "aiUnavailable";
	private static final String AI_UNAVAILABLE_ERROR_MESSAGE = "Markdown本文のAI整形・要約機能は無効です。";
	private static final String WEB_INPUT_ERROR_CODE = "webInputError";
	private static final String WEB_PROCESSING_ERROR_CODE = "webProcessingError";
	private static final String WEB_PROCESSING_ERROR_MESSAGE = "Webページ取り込みに失敗しました。";
	private static final String WEB_FETCH_ERROR_CODE = "webFetchError";
	private static final String WEB_FETCH_BLOCKED_ERROR_CODE = "webFetchBlocked";
	private static final String WEB_FETCH_BLOCKED_ERROR_MESSAGE = "このURLへは接続できません。社内ネットワークや自分のPC上のアドレスは取得対象にできません。";
	private static final String WEB_UNAVAILABLE_ERROR_CODE = "webUnavailable";
	private static final String WEB_UNAVAILABLE_ERROR_MESSAGE = "URLからのWebページ取得機能は無効です。";
	private static final String SEARCHABLE_PDF_UNAVAILABLE_ERROR_CODE = "searchablePdfUnavailable";
	private static final String SEARCHABLE_PDF_UNAVAILABLE_ERROR_MESSAGE = "検索可能PDF生成機能は無効です。";

	/**
	 * MultipartExceptionがスローされた場合、レスポンスステータスを413にする。<br>
	 * 該当するエラーメッセージを返却する。
	 * 
	 * @param ex MultipartException
	 * @return FieldErrorResponseのレスポンス
	 */
	@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
	@ExceptionHandler(MultipartException.class)
	protected ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex) {
		LOGGER.warn("アップロードファイルサイズまたはmultipart requestが不正です。message={}", ex.getMessage());
		LOGGER.debug("Multipart例外の詳細です。", ex);
		return createErrorResponse(MULTIPART_ERROR_CODE, MULTIPART_ERROR_MESSAGE, HttpStatus.CONTENT_TOO_LARGE);
	}

	/**
	 * PDF処理に失敗した場合、レスポンスステータスを500にする。<br>
	 * フロントエンドが既存のエラー表示で扱えるよう、validation errorと同じfieldErrors形式で返却する。
	 *
	 * @param ex PDF処理例外
	 * @return PDF処理エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(PdfProcessingException.class)
	protected ResponseEntity<ErrorResponse> handlePdfProcessing(PdfProcessingException ex) {
		LOGGER.error("PDF処理に失敗しました。message={}", ex.getMessage());
		LOGGER.debug("PDF処理例外の詳細です。", ex);
		return createErrorResponse(PDF_PROCESSING_ERROR_CODE, PDF_PROCESSING_ERROR_MESSAGE,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * AUTOモードで画像変換の対象ページ数が上限を超えた場合、レスポンスステータスを400にする。
	 * <p>
	 * 外部AIの課金が発生する前に止めるコストガードで、利用者が自分で対処できるエラーのため、対象ページ数と上限を
	 * メッセージへ含める。ページ数は文書の内容ではないため、レスポンス・ログへ出しても情報漏洩にならない。
	 *
	 * @param ex ページ上限超過例外
	 * @return ページ上限超過エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(PdfPageLimitExceededException.class)
	protected ResponseEntity<ErrorResponse> handlePdfPageLimitExceeded(PdfPageLimitExceededException ex) {
		LOGGER.warn("画像変換の対象ページ数が上限を超えました。targetPageCount={}, maxPages={}", ex.getTargetPageCount(),
				ex.getMaxPages());
		return createErrorResponse(PDF_PAGE_LIMIT_ERROR_CODE, buildPageLimitMessage(ex), HttpStatus.BAD_REQUEST);
	}

	/**
	 * ページ上限超過のエラーメッセージを組み立てる。
	 * <p>
	 * 対象ページ数と上限に加え、何を数えた対象かの説明があれば添える。同じページ数でもAUTOなら通り
	 * VISIONなら通らないため、数値だけでは利用者が理由を判断できない。
	 *
	 * @param ex ページ上限超過例外
	 * @return 画面表示用のエラーメッセージ
	 */
	private String buildPageLimitMessage(PdfPageLimitExceededException ex) {
		String message = PDF_PAGE_LIMIT_ERROR_MESSAGE.formatted(ex.getTargetPageCount(), ex.getMaxPages());
		if (StringUtils.isBlank(ex.getTargetDescription())) {
			return message;
		}
		return PDF_PAGE_LIMIT_ERROR_DETAIL_MESSAGE.formatted(message, ex.getTargetDescription());
	}

	/**
	 * パスワードで保護されたPDFを開けなかった場合、レスポンスステータスを400にする。
	 * <p>
	 * 利用者がパスワードを入力すれば通るため、処理失敗（500）とは分ける。
	 * 「パスワードが必要」と「入力されたパスワードが違う」でコードとメッセージを分ける。画面が
	 * 入力欄を出すのか、入力し直しを促すのかを判断できるようにするため。
	 * <p>
	 * 入力されたパスワード、ファイル名、パス、PDFBoxの例外メッセージはレスポンスへ含めない。
	 * 原因の特定はログ側で行う。ログにもパスワードそのものは残さない。
	 *
	 * @param ex パスワード保護例外
	 * @return パスワード保護エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(PdfPasswordProtectedException.class)
	protected ResponseEntity<ErrorResponse> handlePdfPasswordProtected(PdfPasswordProtectedException ex) {
		if (ex.isPasswordProvided()) {
			LOGGER.warn("指定されたパスワードではPDFを開けませんでした。");
			return createErrorResponse(PDF_PASSWORD_INCORRECT_ERROR_CODE, PDF_PASSWORD_INCORRECT_ERROR_MESSAGE,
					HttpStatus.BAD_REQUEST);
		}
		LOGGER.warn("パスワードで保護されたPDFが指定されました。");
		return createErrorResponse(PDF_PASSWORD_PROTECTED_ERROR_CODE, PDF_PASSWORD_PROTECTED_ERROR_MESSAGE,
				HttpStatus.BAD_REQUEST);
	}

	/**
	 * 画像からPDFを作る際に画像として読めないファイルを受け取った場合、レスポンスステータスを400にする。
	 * <p>
	 * 利用者が別のファイルを選べば通るエラーのため、どのファイルが読めなかったかをメッセージへ含める。
	 * ファイル名は利用者自身が付けたものなので、返しても情報漏洩にならない。
	 *
	 * @param ex 画像入力例外
	 * @return 画像入力エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(PdfImageInputException.class)
	protected ResponseEntity<ErrorResponse> handlePdfImageInput(PdfImageInputException ex) {
		LOGGER.warn("画像として読み込めないファイルです。fileName={}", ex.getFileName());
		return createErrorResponse(PDF_IMAGE_INPUT_ERROR_CODE,
				PDF_IMAGE_INPUT_ERROR_MESSAGE.formatted(ex.getFileName()), HttpStatus.BAD_REQUEST);
	}

	/**
	 * Office文書として読み込めないファイルを受け取った場合、レスポンスステータスを400にする。
	 * <p>
	 * 対象外の拡張子、旧形式、壊れたOOXMLがここへ来る。いずれも利用者が別のファイルを用意すれば通るため、
	 * どのファイルが読めなかったかと対応形式をメッセージへ含める。
	 *
	 * @param ex Office入力例外
	 * @return Office入力エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(OfficeInputException.class)
	protected ResponseEntity<ErrorResponse> handleOfficeInput(OfficeInputException ex) {
		LOGGER.warn("Office文書として読み込めないファイルです。fileName={}", ex.getFileName());
		return createErrorResponse(OFFICE_INPUT_ERROR_CODE, OFFICE_INPUT_ERROR_MESSAGE.formatted(ex.getFileName()),
				HttpStatus.BAD_REQUEST);
	}

	/**
	 * Office文書の処理で想定外の失敗が起きた場合、レスポンスステータスを500にする。
	 *
	 * @param ex Office処理例外
	 * @return Office処理エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(OfficeProcessingException.class)
	protected ResponseEntity<ErrorResponse> handleOfficeProcessing(OfficeProcessingException ex) {
		LOGGER.error("Office文書の処理に失敗しました。message={}", ex.getMessage());
		LOGGER.debug("Office処理失敗の詳細です。", ex);
		return createErrorResponse(OFFICE_PROCESSING_ERROR_CODE, OFFICE_PROCESSING_ERROR_MESSAGE,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 指定された解像度が上限を超えていた場合、レスポンスステータスを400にする。
	 * <p>
	 * 利用者が入力を直せるエラーのため、指定値と上限をメッセージへ含める。
	 *
	 * @param ex 解像度上限例外
	 * @return 解像度エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(PdfRenderDpiException.class)
	protected ResponseEntity<ErrorResponse> handlePdfRenderDpi(PdfRenderDpiException ex) {
		LOGGER.warn("指定された解像度が上限を超えています。requestedDpi={}, maxDpi={}", ex.getRequestedDpi(), ex.getMaxDpi());
		return createErrorResponse(PDF_RENDER_DPI_ERROR_CODE,
				PDF_RENDER_DPI_ERROR_MESSAGE.formatted(ex.getRequestedDpi(), ex.getMaxDpi()), HttpStatus.BAD_REQUEST);
	}

	/**
	 * 分割範囲がPDFの総ページ数を超えていた場合、レスポンスステータスを400にする。
	 * <p>
	 * 利用者が入力を直せるエラーのため、範囲外だった範囲と総ページ数をメッセージへ含める。
	 * ページ数は文書の内容ではないため、レスポンス・ログへ出しても情報漏洩にならない。
	 *
	 * @param ex 分割範囲例外
	 * @return 分割範囲エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(PdfSplitRangeException.class)
	protected ResponseEntity<ErrorResponse> handlePdfSplitRange(PdfSplitRangeException ex) {
		LOGGER.warn("分割範囲がPDFのページ範囲外です。range={}, totalPages={}", ex.getOutsideRangeText(), ex.getTotalPages());
		return createErrorResponse(PDF_SPLIT_RANGE_ERROR_CODE,
				PDF_SPLIT_RANGE_ERROR_MESSAGE.formatted(ex.getOutsideRangeText(), ex.getTotalPages()),
				HttpStatus.BAD_REQUEST);
	}

	/**
	 * 画像入力が不正な場合、レスポンスステータスを400にする。
	 *
	 * @param ex 画像入力例外
	 * @return 画像入力エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(ImageInputException.class)
	protected ResponseEntity<ErrorResponse> handleImageInput(ImageInputException ex) {
		LOGGER.warn("画像入力が不正です。message={}", ex.getMessage());
		return createErrorResponse(IMAGE_INPUT_ERROR_CODE, IMAGE_INPUT_ERROR_MESSAGE, HttpStatus.BAD_REQUEST);
	}

	/**
	 * 画像Markdown下書き生成に失敗した場合、レスポンスステータスを500にする。
	 * <p>
	 * フロントエンドが既存のエラー表示で扱えるよう、fieldErrors形式で返却する。APIキーや画像内容はメッセージへ含めない。
	 *
	 * @param ex 画像処理例外
	 * @return 画像処理エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(ImageProcessingException.class)
	protected ResponseEntity<ErrorResponse> handleImageProcessing(ImageProcessingException ex) {
		LOGGER.error("画像Markdown下書き生成に失敗しました。message={}", ex.getMessage());
		LOGGER.debug("画像処理例外の詳細です。", ex);
		return createErrorResponse(IMAGE_PROCESSING_ERROR_CODE, IMAGE_PROCESSING_ERROR_MESSAGE,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * 画像Markdown下書き機能が無効な場合、レスポンスステータスを503にする。
	 *
	 * @param ex 機能無効例外
	 * @return 機能無効エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	@ExceptionHandler(OcrUnavailableException.class)
	protected ResponseEntity<ErrorResponse> handleOcrUnavailable(OcrUnavailableException ex) {
		LOGGER.warn("画像Markdown下書き機能が無効です。message={}", ex.getMessage());
		return createErrorResponse(OCR_UNAVAILABLE_ERROR_CODE, OCR_UNAVAILABLE_ERROR_MESSAGE,
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	/**
	 * MarkdownからのPDF出力に失敗した場合、レスポンスステータスを500にする。
	 * <p>
	 * Markdown本文やフォントのローカルパスはメッセージへ含めない。詳細はログ側に残す。
	 *
	 * @param ex Markdown PDF出力例外
	 * @return PDF出力エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(MarkdownPdfException.class)
	protected ResponseEntity<ErrorResponse> handleMarkdownPdf(MarkdownPdfException ex) {
		LOGGER.error("MarkdownからのPDF出力に失敗しました。message={}", ex.getMessage());
		LOGGER.debug("Markdown PDF出力例外の詳細です。", ex);
		return createErrorResponse(MARKDOWN_PDF_ERROR_CODE, MARKDOWN_PDF_ERROR_MESSAGE,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * Markdown本文AI変換の入力文字数が上限を超えた場合、レスポンスステータスを400にする。
	 * <p>
	 * 外部AIの課金が発生する前に止めるコストガードで、利用者が自分で対処できるエラーのため、入力文字数と上限を
	 * メッセージへ含める。文字数は文書の内容ではないため、レスポンス・ログへ出しても情報漏洩にならない。
	 *
	 * @param ex 入力文字数上限超過例外
	 * @return 入力文字数上限超過エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(AiInputException.class)
	protected ResponseEntity<ErrorResponse> handleAiInput(AiInputException ex) {
		LOGGER.warn("Markdown本文AI変換の入力文字数が上限を超えました。characterCount={}, maxCharacters={}", ex.getCharacterCount(),
				ex.getMaxCharacters());
		return createErrorResponse(AI_INPUT_ERROR_CODE,
				AI_INPUT_ERROR_MESSAGE.formatted(ex.getCharacterCount(), ex.getMaxCharacters()), HttpStatus.BAD_REQUEST);
	}

	/**
	 * Markdown本文のAI整形・要約に失敗した場合、レスポンスステータスを500にする。
	 * <p>
	 * フロントエンドが既存のエラー表示で扱えるよう、fieldErrors形式で返却する。APIキーやMarkdown本文はメッセージへ含めない。
	 *
	 * @param ex AI処理例外
	 * @return AI処理エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(AiProcessingException.class)
	protected ResponseEntity<ErrorResponse> handleAiProcessing(AiProcessingException ex) {
		LOGGER.error("Markdown本文のAI整形・要約に失敗しました。message={}", ex.getMessage());
		LOGGER.debug("AI処理例外の詳細です。", ex);
		return createErrorResponse(AI_PROCESSING_ERROR_CODE, AI_PROCESSING_ERROR_MESSAGE,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * Markdown本文のAI整形・要約機能が無効な場合、レスポンスステータスを503にする。
	 *
	 * @param ex 機能無効例外
	 * @return 機能無効エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	@ExceptionHandler(AiUnavailableException.class)
	protected ResponseEntity<ErrorResponse> handleAiUnavailable(AiUnavailableException ex) {
		LOGGER.warn("Markdown本文のAI整形・要約機能が無効です。message={}", ex.getMessage());
		return createErrorResponse(AI_UNAVAILABLE_ERROR_CODE, AI_UNAVAILABLE_ERROR_MESSAGE,
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	/**
	 * 検索可能PDF生成機能が無効な場合、レスポンスステータスを503にする。
	 *
	 * @param ex 機能無効例外
	 * @return 機能無効エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	@ExceptionHandler(SearchablePdfUnavailableException.class)
	protected ResponseEntity<ErrorResponse> handleSearchablePdfUnavailable(SearchablePdfUnavailableException ex) {
		LOGGER.warn("検索可能PDF生成機能が無効です。message={}", ex.getMessage());
		return createErrorResponse(SEARCHABLE_PDF_UNAVAILABLE_ERROR_CODE, SEARCHABLE_PDF_UNAVAILABLE_ERROR_MESSAGE,
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	/**
	 * Webページ取り込みの入力が不正な場合、レスポンスステータスを400にする。
	 * <p>
	 * 対応していない拡張子、セレクタの書式不正、本文が取り出せない、はいずれも利用者が
	 * 入力を変えれば通る。何を直せば通るのかは原因ごとに違うため、例外が持つ説明をそのまま返す。
	 * この説明は固定文言で組み立てており、取り込んだHTMLの内容は含まない。
	 *
	 * @param ex Webページ取り込みの入力例外
	 * @return Web入力エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(WebInputException.class)
	protected ResponseEntity<ErrorResponse> handleWebInput(WebInputException ex) {
		LOGGER.warn("Webページ取り込みの入力が不正です。message={}", ex.getMessage());
		return createErrorResponse(WEB_INPUT_ERROR_CODE, ex.getDisplayMessage(), HttpStatus.BAD_REQUEST);
	}

	/**
	 * Webページ取り込みに失敗した場合、レスポンスステータスを500にする。
	 *
	 * @param ex Webページ取り込みの処理例外
	 * @return Web処理エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(WebProcessingException.class)
	protected ResponseEntity<ErrorResponse> handleWebProcessing(WebProcessingException ex) {
		LOGGER.error("Webページ取り込みに失敗しました。message={}", ex.getMessage());
		LOGGER.debug("Webページ取り込み例外の詳細です。", ex);
		return createErrorResponse(WEB_PROCESSING_ERROR_CODE, WEB_PROCESSING_ERROR_MESSAGE,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	/**
	 * URLからのWebページ取得に失敗した場合、レスポンスステータスを502にする。
	 * <p>
	 * 取得先が応答しない、タイムアウト、想定外のstatus、HTML以外の応答、サイズ超過、リダイレクト過多、
	 * 取得間隔の制限がここへ来る。原因が取得先または通信の側にあるため、アプリの障害（500）とは分ける。
	 * <p>
	 * メッセージは例外が持つ文言をそのまま返す。何が起きたかで次の一手が変わる（URLを変える、時間を置く、
	 * 別ページを選ぶ）ため。解決済みIPと取得したHTML本文は含まれない。
	 *
	 * @param ex Webページ取得例外
	 * @return Web取得エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	@ExceptionHandler(WebFetchException.class)
	protected ResponseEntity<ErrorResponse> handleWebFetch(WebFetchException ex) {
		LOGGER.warn("Webページの取得に失敗しました。message={}", ex.getMessage());
		return createErrorResponse(WEB_FETCH_ERROR_CODE, ex.getMessage(), HttpStatus.BAD_GATEWAY);
	}

	/**
	 * 接続を許さない宛先が指定された場合、レスポンスステータスを400にする。
	 * <p>
	 * レスポンスにもログにも、解決済みIPアドレスは出さない。「どのホスト名がどのIPへ解決されたか」を
	 * 返すと、この機能が内部ネットワークの構成を読み出す道具になってしまう。
	 * どの範囲だったか（ループバックかプライベートか）も返さない。同じ理由による。
	 *
	 * @param ex 宛先拒否例外
	 * @return 宛先拒否エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ExceptionHandler(WebFetchBlockedException.class)
	protected ResponseEntity<ErrorResponse> handleWebFetchBlocked(WebFetchBlockedException ex) {
		LOGGER.warn("接続が許可されていない宛先が指定されました。host={}", ex.getHost());
		return createErrorResponse(WEB_FETCH_BLOCKED_ERROR_CODE, WEB_FETCH_BLOCKED_ERROR_MESSAGE,
				HttpStatus.BAD_REQUEST);
	}

	/**
	 * URLからのWebページ取得機能が無効な場合、レスポンスステータスを503にする。
	 *
	 * @param ex 機能無効例外
	 * @return 機能無効エラーのレスポンス
	 */
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	@ExceptionHandler(WebUnavailableException.class)
	protected ResponseEntity<ErrorResponse> handleWebUnavailable(WebUnavailableException ex) {
		LOGGER.warn("URLからのWebページ取得機能が無効です。message={}", ex.getMessage());
		return createErrorResponse(WEB_UNAVAILABLE_ERROR_CODE, WEB_UNAVAILABLE_ERROR_MESSAGE,
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	/**
	 * 共通エラーレスポンスを作成する。
	 *
	 * @param errorCode  エラーコード
	 * @param message    表示するエラーメッセージ
	 * @param httpStatus HTTPステータス
	 * @return エラーレスポンス
	 */
	private ResponseEntity<ErrorResponse> createErrorResponse(String errorCode, String message, HttpStatus httpStatus) {
		ErrorResponse fieldErrorResponse = new ErrorResponse();
		fieldErrorResponse.setFieldErrors(List.of(buildFieldError(errorCode, message)));

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setCacheControl(CACHE_CONTROL_VALUE);
		return new ResponseEntity<>(fieldErrorResponse, headers, httpStatus);
	}

	/**
	 * FE向け共通形式の項目エラーを作成する。
	 *
	 * @param errorCode エラーコード
	 * @param message   エラーメッセージ
	 * @return 項目エラー
	 */
	private CustomFieldError buildFieldError(String errorCode, String message) {
		CustomFieldError fieldError = new CustomFieldError();
		fieldError.setErrorCode(errorCode);
		fieldError.setField("");
		fieldError.setMessage(message);
		return fieldError;
	}
}
