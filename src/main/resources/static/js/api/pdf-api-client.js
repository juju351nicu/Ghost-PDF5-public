import FetchClient from "./fetch-client.js";
import FileResponseHandler from "./file-response-handler.js";
import ApiErrorUtils from "./api-error-utils.js";
import ApiResultUtils from "./api-result-utils.js";

const PDF_ERROR_MESSAGE =
  "PDF処理に失敗しました。入力内容を確認してください。";
const UNEXPECTED_PDF_ERROR_MESSAGE =
  "PDF処理中に予期しないエラーが発生しました。";

/**
 * PDF操作APIへmultipart requestを送り、成功時は返却PDFを別タブで開く。
 *
 * @param {string} url PDF操作APIのURL
 * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
 * @returns {Promise<{errorMessages: string[], errorCodes: string[]}>} エラー内容。成功時は空配列
 */
const requestPdfAndOpen = async (url, payload) => {
  const response = await FetchClient.multipartRequest(url, payload);
  if (!response.ok) {
    return toErrorResult(response);
  }
  const pdfBlob = await response.blob();
  FileResponseHandler.openPdfBlob(pdfBlob);
  return { errorMessages: [], errorCodes: [] };
};

/**
 * PDF操作APIへmultipart requestを送り、成功時は返却ファイルをダウンロードする。
 *
 * 保存先の選択（`FileResponseHandler.requestSaveTarget`）はこの関数では行わない。
 * File System Access APIはtransient activationを要求し、fetch完了後では失効しているため、
 * 呼び出し元がクリック直後に取得したものを受け取る。
 *
 * @param {string} url PDF操作APIのURL
 * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
 * @param {string} defaultFileName Content-Dispositionが無い場合のファイル名
 * @param {FileSystemFileHandle} [saveTarget] 利用者が選んだ保存先
 * @returns {Promise<{errorMessages: string[], errorCodes: string[]}>} エラー内容。成功時は空配列
 */
const requestFileAndDownload = async (
  url,
  payload,
  defaultFileName,
  saveTarget
) => {
  const response = await FetchClient.multipartRequest(url, payload);
  if (!response.ok) {
    return toErrorResult(response);
  }
  const fileBlob = await response.blob();
  // 書き込み失敗を成功扱いにしないため、awaitして例外を呼び出し元へ伝える。
  await FileResponseHandler.downloadBlob(
    fileBlob,
    response.headers,
    defaultFileName,
    saveTarget
  );
  return { errorMessages: [], errorCodes: [] };
};

/**
 * PDFメタデータAPIへmultipart requestを送り、成功時はJSONレスポンスを返す。
 *
 * @param {string} url PDFメタデータAPIのURL
 * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
 * @returns {Promise<{metadata: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} PDFメタデータ取得結果
 */
const requestPdfMetadata = async (url, payload) => {
  const response = await FetchClient.multipartRequest(url, payload);
  if (!response.ok) {
    return {
      metadata: null,
      messages: [],
      ...(await toErrorResult(response)),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    metadata: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
    errorCodes: [],
  };
};

/**
 * PDFテキスト抽出APIへmultipart requestを送り、成功時はJSONレスポンスを返す。
 *
 * @param {string} url PDFテキスト抽出APIのURL
 * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
 * @returns {Promise<{textResponse: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} PDFテキスト抽出結果
 */
const requestPdfText = async (url, payload) => {
  const response = await FetchClient.multipartRequest(url, payload);
  if (!response.ok) {
    return {
      textResponse: null,
      messages: [],
      ...(await toErrorResult(response)),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    textResponse: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
    errorCodes: [],
  };
};

/**
 * ページ単位Markdown下書きAPIへmultipart requestを送り、JSONレスポンスを返す。
 *
 * @param {string} url Markdown下書きAPIのURL
 * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
 * @returns {Promise<{markdownDraftResponse: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} Markdown下書き生成結果
 */
const requestPdfMarkdownDraft = async (url, payload) => {
  const response = await FetchClient.multipartRequest(url, payload);
  if (!response.ok) {
    return {
      markdownDraftResponse: null,
      messages: [],
      ...(await toErrorResult(response)),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    markdownDraftResponse: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
    errorCodes: [],
  };
};

/**
 * ページ選択用サムネイルAPIへmultipart requestを送り、成功時はJSONレスポンスを返す。
 *
 * @param {string} url サムネイルAPIのURL
 * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
 * @returns {Promise<{thumbnailResponse: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} サムネイル取得結果
 */
const requestPdfThumbnails = async (url, payload) => {
  const response = await FetchClient.multipartRequest(url, payload);
  if (!response.ok) {
    return {
      thumbnailResponse: null,
      messages: [],
      ...(await toErrorResult(response)),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    thumbnailResponse: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
    errorCodes: [],
  };
};

/**
 * エラーレスポンスを、画面が扱う共通のエラー内容へ変換する。
 *
 * メッセージとエラーコードを別々に取り出そうとするとレスポンスボディを2度読むことになるため、
 * 取り出し口をこの1箇所へまとめる。
 *
 * @param {Response} response APIのエラーレスポンス
 * @returns {Promise<{errorMessages: string[], errorCodes: string[]}>} エラー内容
 */
const toErrorResult = async (response) => {
  const errorDetail = await ApiErrorUtils.extractErrorDetail(
    response,
    PDF_ERROR_MESSAGE
  );
  return {
    errorMessages: errorDetail.messages,
    errorCodes: errorDetail.errorCodes,
  };
};

export default {
  requestPdfAndOpen,
  requestFileAndDownload,
  requestPdfMetadata,
  requestPdfText,
  requestPdfMarkdownDraft,
  requestPdfThumbnails,
  buildUnexpectedErrorMessage(error) {
    return ApiErrorUtils.buildUnexpectedErrorMessage(
      error,
      UNEXPECTED_PDF_ERROR_MESSAGE
    );
  },
};
