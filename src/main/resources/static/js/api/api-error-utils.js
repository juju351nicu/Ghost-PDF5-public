import Util from "../util.js";

const DEFAULT_PDF_ERROR_MESSAGE =
  "PDF処理に失敗しました。入力内容を確認してください。";
const UNEXPECTED_PDF_ERROR_MESSAGE =
  "PDF処理中に予期しないエラーが発生しました。";
const NETWORK_ERROR_MESSAGE =
  "サーバーに接続できません。アプリが起動しているか確認してください。";
// fetchはサーバーへ到達できなかった場合にTypeErrorを投げるが、messageはブラウザごとに異なる。
// Chrome: "Failed to fetch" / Firefox: "NetworkError when attempting to fetch resource." / Safari: "Load failed"
const NETWORK_ERROR_MESSAGE_PATTERN =
  /failed to fetch|networkerror|network request failed|load failed/i;

/**
 * BEが返すエラーコード。
 *
 * `GlobalExceptionErrorHandler` の定数と同じ文字列を保つ。
 * ずれると「利用者が直せる失敗」の判定が静かに外れ、専用表示へ回らなくなるため、
 * 一致は `FrontendProcessStateContractTest` が検証する。
 */
const ERROR_CODE = {
  MULTIPART: "multipartError",
  PDF_PASSWORD_PROTECTED: "pdfPasswordProtected",
  PDF_PASSWORD_INCORRECT: "pdfPasswordIncorrect",
  PDF_PAGE_LIMIT_EXCEEDED: "pdfPageLimitExceeded",
  PDF_SPLIT_RANGE_OUT_OF_BOUNDS: "pdfSplitRangeOutOfBounds",
  PDF_RENDER_DPI_EXCEEDED: "pdfRenderDpiExceeded",
  PDF_IMAGE_INPUT: "pdfImageInputError",
  OFFICE_INPUT: "officeInputError",
  OFFICE_PROCESSING: "officeProcessingError",
  PDF_PROCESSING: "pdfProcessingError",
  IMAGE_INPUT: "imageInputError",
  IMAGE_PROCESSING: "imageProcessingError",
  OCR_UNAVAILABLE: "ocrUnavailable",
  MARKDOWN_PDF: "markdownPdfError",
  AI_INPUT: "aiInputError",
  AI_PROCESSING: "aiProcessingError",
  AI_UNAVAILABLE: "aiUnavailable",
  SEARCHABLE_PDF_UNAVAILABLE: "searchablePdfUnavailable",
  WEB_INPUT: "webInputError",
  WEB_PROCESSING: "webProcessingError",
  WEB_FETCH: "webFetchError",
  WEB_FETCH_BLOCKED: "webFetchBlocked",
  WEB_UNAVAILABLE: "webUnavailable",
};

/**
 * 利用者が自分で対処できるエラーコード。
 *
 * 別のファイルを用意する、入力を直す、設定を有効にするといった行動で通るようになるもの。
 * ここへ挙げていないコード（{@code pdfProcessingError} などサーバー側の想定外エラー）は
 * 利用者側で打つ手が無いため、扱いを分ける。
 */
const RECOVERABLE_ERROR_CODES = [
  ERROR_CODE.MULTIPART,
  ERROR_CODE.PDF_PASSWORD_PROTECTED,
  ERROR_CODE.PDF_PASSWORD_INCORRECT,
  ERROR_CODE.PDF_PAGE_LIMIT_EXCEEDED,
  ERROR_CODE.PDF_SPLIT_RANGE_OUT_OF_BOUNDS,
  ERROR_CODE.PDF_RENDER_DPI_EXCEEDED,
  ERROR_CODE.PDF_IMAGE_INPUT,
  ERROR_CODE.OFFICE_INPUT,
  ERROR_CODE.IMAGE_INPUT,
  ERROR_CODE.OCR_UNAVAILABLE,
  ERROR_CODE.AI_INPUT,
  ERROR_CODE.AI_UNAVAILABLE,
  ERROR_CODE.SEARCHABLE_PDF_UNAVAILABLE,
  // 拡張子違い・セレクタの指定ミス・本文が取れないHTMLは、入力を変えれば通る。
  ERROR_CODE.WEB_INPUT,
  // 取得先の応答・宛先・機能無効は、URLを変える／設定を有効にすることで通る。
  ERROR_CODE.WEB_FETCH,
  ERROR_CODE.WEB_FETCH_BLOCKED,
  ERROR_CODE.WEB_UNAVAILABLE,
];

/**
 * APIのエラーレスポンスから画面表示用メッセージを抽出する。
 * <p>
 * 既存のBE共通エラー形式である {@code fieldErrors} を優先し、
 * JSON以外や想定外形式の場合はfallback messageを返す。
 *
 * @param {Response} response APIのエラーレスポンス
 * @param {string} [fallbackMessage] 既定のエラーメッセージ
 * @returns {Promise<string[]>} 画面表示用エラーメッセージ
 */
const extractErrorMessages = async (
  response,
  fallbackMessage = DEFAULT_PDF_ERROR_MESSAGE
) => {
  const errorDetail = await extractErrorDetail(response, fallbackMessage);
  return errorDetail.messages;
};

/**
 * APIのエラーレスポンスからメッセージとエラーコードを取り出す。
 *
 * レスポンスボディは1度しか読めないため、メッセージとコードを別々に取りに行かず
 * ここで同時に取り出す。
 *
 * @param {Response} response APIのエラーレスポンス
 * @param {string} [fallbackMessage] 既定のエラーメッセージ
 * @returns {Promise<{messages: string[], errorCodes: string[]}>} エラーメッセージとエラーコード
 */
const extractErrorDetail = async (
  response,
  fallbackMessage = DEFAULT_PDF_ERROR_MESSAGE
) => {
  const responseBody = await response.json().catch(() => null);
  if (Util.isEmpty(responseBody?.fieldErrors)) {
    return { messages: [fallbackMessage], errorCodes: [] };
  }
  return {
    messages: responseBody.fieldErrors.map((fieldError) => fieldError.message),
    errorCodes: responseBody.fieldErrors
      .map((fieldError) => fieldError.errorCode)
      .filter((errorCode) => !Util.isEmpty(errorCode)),
  };
};

/**
 * エラーコードのいずれかが「利用者が自分で対処できる失敗」か判定する。
 *
 * @param {string[]} errorCodes APIが返したエラーコード
 * @returns {boolean} 対処できる失敗が含まれる場合true
 */
const isRecoverableError = (errorCodes) => {
  if (Util.isEmpty(errorCodes)) {
    return false;
  }
  return errorCodes.some((errorCode) =>
    RECOVERABLE_ERROR_CODES.includes(errorCode)
  );
};

/**
 * 想定外エラーを画面表示用メッセージへ変換する。
 *
 * 例外の `message` は英語の内部表現（例: "Failed to fetch"）が入るため画面へは出さない。
 * 以前は `error.message` を優先していたため、日本語のfallbackがほぼ到達せず、
 * 利用者向けモーダルに生の例外メッセージが表示されていた。
 *
 * ただしサーバーへ到達できなかった場合だけは、利用者自身が対処できる（アプリの起動忘れなど）ため、
 * 専用メッセージへ振り分ける。
 *
 * @param {Error} error 想定外エラー
 * @param {string} [fallbackMessage] 既定の想定外エラーメッセージ
 * @returns {string} 画面表示用メッセージ
 */
const buildUnexpectedErrorMessage = (
  error,
  fallbackMessage = UNEXPECTED_PDF_ERROR_MESSAGE
) => {
  if (isNetworkError(error)) {
    return NETWORK_ERROR_MESSAGE;
  }
  return fallbackMessage;
};

/**
 * パスワードを入力すれば通るエラーか判定する。
 *
 * 「パスワードが必要」と「入力されたパスワードが違う」の両方を対象にする。画面はどちらでも
 * 同じ入力欄を出し、BEが返したメッセージで違いを伝える。
 *
 * @param {string[]} errorCodes APIが返したエラーコード
 * @returns {boolean} パスワード入力で通る可能性がある場合true
 */
const isPasswordError = (errorCodes) => {
  if (Util.isEmpty(errorCodes)) {
    return false;
  }
  return errorCodes.some(
    (errorCode) =>
      errorCode === ERROR_CODE.PDF_PASSWORD_PROTECTED ||
      errorCode === ERROR_CODE.PDF_PASSWORD_INCORRECT
  );
};

/**
 * fetchがサーバーへ到達できなかったエラーか判定する。
 *
 * 型だけで判定すると通常のコーディングミス（`TypeError`）も到達失敗扱いになるため、
 * 型とメッセージの両方で絞る。メッセージ自体は画面へ出さない。
 *
 * @param {*} error 判定対象のエラー
 * @returns {boolean} サーバーへ到達できなかった場合true
 */
const isNetworkError = (error) => {
  if (!(error instanceof TypeError)) {
    return false;
  }
  return NETWORK_ERROR_MESSAGE_PATTERN.test(error.message || "");
};

export default {
  ERROR_CODE,
  extractErrorMessages,
  extractErrorDetail,
  isRecoverableError,
  isPasswordError,
  buildUnexpectedErrorMessage,
};
