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
  const responseBody = await response.json().catch(() => null);
  if (!Util.isEmpty(responseBody?.fieldErrors)) {
    return responseBody.fieldErrors.map((fieldError) => fieldError.message);
  }
  return [fallbackMessage];
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
  extractErrorMessages,
  buildUnexpectedErrorMessage,
};
