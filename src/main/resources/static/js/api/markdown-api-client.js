import CONST from "../const.js";
import FetchClient from "./fetch-client.js";
import ApiErrorUtils from "./api-error-utils.js";
import ApiResultUtils from "./api-result-utils.js";
import FileResponseHandler from "./file-response-handler.js";

const MARKDOWN_ERROR_MESSAGE =
  "Markdown処理に失敗しました。入力内容を確認してください。";
const UNEXPECTED_MARKDOWN_ERROR_MESSAGE =
  "Markdown処理中に予期しないエラーが発生しました。";
const PDF_MEDIA_TYPE = "application/pdf";
const CSV_MEDIA_TYPE = "text/csv";

/**
 * Markdown APIへ送信するfileName queryを組み立てる。
 *
 * @param {string} fileName 対象Markdownファイル名
 * @returns {string} query string
 */
const buildFileNameQuery = (fileName) => {
  return "?fileName=" + encodeURIComponent(fileName);
};

/**
 * JSONレスポンスAPIを実行し、画面用の共通結果へ変換する。
 *
 * 成功時のmessagesは現時点では画面へ出さないが、後続フェーズで通知を表示できるよう受け取っておく。
 *
 * @param {Function} request APIリクエスト実行関数
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} API結果
 */
const requestJson = async (request) => {
  const response = await request();
  if (!response.ok) {
    return {
      data: null,
      messages: [],
      ...(await toErrorResult(response)),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    data: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
    errorCodes: [],
  };
};

/**
 * エラーレスポンスを、画面が扱う共通のエラー内容へ変換する。
 *
 * @param {Response} response APIのエラーレスポンス
 * @returns {Promise<{errorMessages: string[], errorCodes: string[]}>} エラー内容
 */
const toErrorResult = async (response) => {
  const errorDetail = await ApiErrorUtils.extractErrorDetail(
    response,
    MARKDOWN_ERROR_MESSAGE
  );
  return {
    errorMessages: errorDetail.messages,
    errorCodes: errorDetail.errorCodes,
  };
};

/**
 * MarkdownからPDFを生成し、Blobとレスポンスヘッダーを返す。
 *
 * レスポンスはJSONではなくPDFバイナリのため、共通ラッパーを読む requestJson は使わない。
 * 失敗時のbodyは共通エラー形式のJSONなので、エラーメッセージの取り出しだけ共通処理へ乗せる。
 *
 * @param {{fileName: string, content: string}} payload PDF出力リクエスト
 * @returns {Promise<{fileBlob: Blob|null, headers: Headers|null, errorMessages: string[], errorCodes: string[]}>} PDF生成結果
 */
const requestMarkdownPdf = async (payload) => {
  const response = await FetchClient.postRequestForFile(
    CONST.REST_PATH.MARKDOWN_PDF,
    payload,
    PDF_MEDIA_TYPE
  );
  if (!response.ok) {
    return {
      fileBlob: null,
      headers: null,
      ...(await toErrorResult(response)),
    };
  }
  return {
    fileBlob: await response.blob(),
    headers: response.headers,
    errorMessages: [],
    errorCodes: [],
  };
};

/**
 * 保存済みMarkdown一覧を取得する。
 *
 * @returns {Promise<{data: Object[]|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 一覧取得結果
 */
/**
 * 保存済みMarkdown一覧のCSVをダウンロードする。
 *
 * @param {string} url CSV出力APIのURL
 * @param {string} defaultFileName 既定のダウンロードファイル名
 * @param {FileSystemFileHandle|null} saveTarget 利用者が選んだ保存先
 * @returns {Promise<{errorMessages: string[], errorCodes: string[]}>} ダウンロード結果
 */
const downloadMarkdownFilesCsv = async (url, defaultFileName, saveTarget) => {
  const response = await FetchClient.getRequestForFile(url, CSV_MEDIA_TYPE);
  if (!response.ok) {
    return toErrorResult(response);
  }
  const csvBlob = await response.blob();
  // 書き込み失敗を成功扱いにしないため、awaitして例外を呼び出し元へ伝える。
  await FileResponseHandler.downloadBlob(
    csvBlob,
    response.headers,
    defaultFileName,
    saveTarget
  );
  return { errorMessages: [], errorCodes: [] };
};

const listMarkdownFiles = () => {
  return requestJson(() =>
    FetchClient.getRequest(CONST.REST_PATH.MARKDOWN_FILES)
  );
};

/**
 * 保存済みMarkdown本文を取得する。
 *
 * @param {string} fileName 対象Markdownファイル名
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 本文取得結果
 */
const getMarkdownFile = (fileName) => {
  return requestJson(() =>
    FetchClient.getRequest(
      CONST.REST_PATH.MARKDOWN_FILE + buildFileNameQuery(fileName)
    )
  );
};

/**
 * 保存済みMarkdownのHTMLプレビューを取得する。
 *
 * @param {string} fileName 対象Markdownファイル名
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} プレビュー取得結果
 */
const previewMarkdownFile = (fileName) => {
  return requestJson(() =>
    FetchClient.getRequest(
      CONST.REST_PATH.MARKDOWN_PREVIEW + buildFileNameQuery(fileName)
    )
  );
};

/**
 * 入力中Markdown本文のHTMLプレビューを取得する。
 *
 * @param {string} content Markdown本文
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} プレビュー取得結果
 */
const previewMarkdownContent = (content) => {
  return requestJson(() =>
    FetchClient.postRequest(CONST.REST_PATH.MARKDOWN_PREVIEW, { content })
  );
};

/**
 * 入力中Markdown本文をAIで整形・要約する。
 *
 * 結果は既存のMarkdown編集欄を上書きしない。呼び出し元が確認用の別領域へ表示し、
 * 利用者が採用するかどうかを選べるようにする。
 *
 * @param {string} content 変換対象のMarkdown本文
 * @param {string} task 変換タスク。SUMMARIZEまたはREFINE
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 変換結果
 */
const transformMarkdownAi = (content, task) => {
  return requestJson(() =>
    FetchClient.postRequest(CONST.REST_PATH.MARKDOWN_AI_TRANSFORM, {
      content,
      task,
    })
  );
};

/**
 * HTMLファイルからMarkdown下書きを起こす。
 *
 * multipart送信のため、JSON APIの requestJson ではなく multipartRequest を使う。
 * 結果はMarkdown編集欄へ反映する前提で、サーバー側では保存されない。
 *
 * @param {File} htmlFile 取り込むHTMLファイル
 * @param {string} selector 本文を絞り込むCSSセレクタ。空なら送信しない
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 取り込み結果
 */
const requestHtmlMarkdownDraft = async (htmlFile, selector) => {
  const params = [{ key: "htmlFile", value: htmlFile }];
  if (selector) {
    params.push({ key: "selector", value: selector });
  }
  const response = await FetchClient.multipartRequest(
    CONST.REST_PATH.MARKDOWN_DRAFT_HTML,
    params
  );
  if (!response.ok) {
    return {
      data: null,
      messages: [],
      ...(await toErrorResult(response)),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    data: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
    errorCodes: [],
  };
};

/**
 * 指定されたURLのWebページからMarkdown下書きを起こす。
 *
 * サーバー側の取得機能が無効な場合は503が返るため、呼び出し元は共通のエラー表示で扱う。
 *
 * @param {string} url 取得先URL
 * @param {string} selector 本文を絞り込むCSSセレクタ。空なら送信しない
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 取り込み結果
 */
const requestUrlMarkdownDraft = (url, selector) => {
  const payload = selector ? { url, selector } : { url };
  return requestJson(() =>
    FetchClient.postRequest(CONST.REST_PATH.MARKDOWN_DRAFT_URL, payload)
  );
};

/**
 * Markdown本文を新規保存する。
 *
 * @param {string} fileName 保存Markdownファイル名
 * @param {string} content Markdown本文
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 保存結果
 */
const saveMarkdown = (fileName, content) => {
  return requestJson(() =>
    FetchClient.postRequest(CONST.REST_PATH.SAVE_MARKDOWN, {
      fileName,
      content,
    })
  );
};

/**
 * 保存済みMarkdown本文を更新する。
 *
 * @param {string} fileName 更新対象Markdownファイル名
 * @param {string} content Markdown本文
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 更新結果
 */
const updateMarkdownFile = (fileName, content) => {
  return requestJson(() =>
    FetchClient.putRequest(
      CONST.REST_PATH.MARKDOWN_FILE + buildFileNameQuery(fileName),
      { content }
    )
  );
};

/**
 * 保存済みMarkdownファイルを削除する。
 *
 * @param {string} fileName 削除対象Markdownファイル名
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[], errorCodes: string[]}>} 削除結果
 */
const deleteMarkdownFile = (fileName) => {
  return requestJson(() =>
    FetchClient.deleteRequest(
      CONST.REST_PATH.MARKDOWN_FILE + buildFileNameQuery(fileName)
    )
  );
};

export default {
  requestMarkdownPdf,
  downloadMarkdownFilesCsv,
  listMarkdownFiles,
  getMarkdownFile,
  previewMarkdownFile,
  previewMarkdownContent,
  transformMarkdownAi,
  requestHtmlMarkdownDraft,
  requestUrlMarkdownDraft,
  saveMarkdown,
  updateMarkdownFile,
  deleteMarkdownFile,
  buildUnexpectedErrorMessage(error) {
    return ApiErrorUtils.buildUnexpectedErrorMessage(
      error,
      UNEXPECTED_MARKDOWN_ERROR_MESSAGE
    );
  },
};
