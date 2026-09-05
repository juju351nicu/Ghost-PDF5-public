import Util from "../util.js";

const DEFAULT_PDF_ERROR_MESSAGE =
  "PDF処理に失敗しました。入力内容を確認してください。";
const UNEXPECTED_PDF_ERROR_MESSAGE =
  "PDF処理中に予期しないエラーが発生しました。";

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
 * @param {Error} error 想定外エラー
 * @param {string} [fallbackMessage] 既定の想定外エラーメッセージ
 * @returns {string} 画面表示用メッセージ
 */
const buildUnexpectedErrorMessage = (
  error,
  fallbackMessage = UNEXPECTED_PDF_ERROR_MESSAGE
) => {
  return error?.message || fallbackMessage;
};

export default {
  extractErrorMessages,
  buildUnexpectedErrorMessage,
};
