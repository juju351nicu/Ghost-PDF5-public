import CONST from "../const.js";
import FetchClient from "./fetch-client.js";
import ApiErrorUtils from "./api-error-utils.js";
import ApiResultUtils from "./api-result-utils.js";

const MARKDOWN_ERROR_MESSAGE =
  "Markdown処理に失敗しました。入力内容を確認してください。";
const UNEXPECTED_MARKDOWN_ERROR_MESSAGE =
  "Markdown処理中に予期しないエラーが発生しました。";

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
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[]}>} API結果
 */
const requestJson = async (request) => {
  const response = await request();
  if (!response.ok) {
    return {
      data: null,
      messages: [],
      errorMessages: await ApiErrorUtils.extractErrorMessages(
        response,
        MARKDOWN_ERROR_MESSAGE
      ),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    data: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
  };
};

/**
 * 保存済みMarkdown一覧を取得する。
 *
 * @returns {Promise<{data: Object[]|null, messages: Object[], errorMessages: string[]}>} 一覧取得結果
 */
const listMarkdownFiles = () => {
  return requestJson(() =>
    FetchClient.getRequest(CONST.REST_PATH.MARKDOWN_FILES)
  );
};

/**
 * 保存済みMarkdown本文を取得する。
 *
 * @param {string} fileName 対象Markdownファイル名
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[]}>} 本文取得結果
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
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[]}>} プレビュー取得結果
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
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[]}>} プレビュー取得結果
 */
const previewMarkdownContent = (content) => {
  return requestJson(() =>
    FetchClient.postRequest(CONST.REST_PATH.MARKDOWN_PREVIEW, { content })
  );
};

/**
 * Markdown本文を新規保存する。
 *
 * @param {string} fileName 保存Markdownファイル名
 * @param {string} content Markdown本文
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[]}>} 保存結果
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
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[]}>} 更新結果
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
 * @returns {Promise<{data: Object|null, messages: Object[], errorMessages: string[]}>} 削除結果
 */
const deleteMarkdownFile = (fileName) => {
  return requestJson(() =>
    FetchClient.deleteRequest(
      CONST.REST_PATH.MARKDOWN_FILE + buildFileNameQuery(fileName)
    )
  );
};

export default {
  listMarkdownFiles,
  getMarkdownFile,
  previewMarkdownFile,
  previewMarkdownContent,
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
