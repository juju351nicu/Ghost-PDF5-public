import Util from "../util.js";

/**
 * HTTP methodの定数。
 */
const METHOD = {
  GET: "GET",
  POST: "POST",
  PUT: "PUT",
  DELETE: "DELETE",
};

/**
 * JSON API向けのデフォルトリクエストヘッダー。
 */
const defaultHeader = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

/**
 * multipart/form-dataに追加する1項目。
 *
 * valueがundefinedの項目は送信しない。nullは「未指定値を明示して送る」可能性があるため、
 * 呼び出し元で送信可否を判断してから渡す。
 *
 * @typedef {Object} MultipartParam
 * @property {string} key FormDataの項目名
 * @property {*} value FormDataへ追加する値
 */

/**
 * Fetch APIへ渡すリクエスト設定。
 *
 * @typedef {Object} FetchRequestConfig
 * @property {string} requestUrl リクエストURL
 * @property {RequestInit} options fetchへ渡すoption
 */

/**
 * GET requestを送信する。
 *
 * @param {string} uri リクエストURL
 * @returns {Promise<Response>} fetchのレスポンス
 */
const getRequest = (uri) => {
  const requestConfig = createRequestConfig(uri, null, null, METHOD.GET);
  return fetcher(requestConfig);
};

/**
 * JSON body付きPOST requestを送信する。
 *
 * @param {string} uri リクエストURL
 * @param {*} requestData 送信するリクエストボディ
 * @returns {Promise<Response>} fetchのレスポンス
 */
const postRequest = (uri, requestData) => {
  const requestConfig = createRequestConfig(
    uri,
    requestData,
    null,
    METHOD.POST
  );
  return fetcher(requestConfig);
};

/**
 * JSON body付きPUT requestを送信する。
 *
 * @param {string} uri リクエストURL
 * @param {*} requestData 送信するリクエストボディ
 * @returns {Promise<Response>} fetchのレスポンス
 */
const putRequest = (uri, requestData) => {
  const requestConfig = createRequestConfig(uri, requestData, null, METHOD.PUT);
  return fetcher(requestConfig);
};

/**
 * DELETE requestを送信する。
 *
 * @param {string} uri リクエストURL
 * @returns {Promise<Response>} fetchのレスポンス
 */
const deleteRequest = (uri) => {
  const requestConfig = createRequestConfig(uri, null, null, METHOD.DELETE);
  return fetcher(requestConfig);
};

/**
 * multipart/form-dataのPOST requestを送信する。
 * <p>
 * 通常のJSON API用ヘッダーはFormData送信に不要なため削除する。
 * Content-Typeはブラウザに任せることでboundaryを自動付与させる。
 *
 * @param {string} uri リクエストURL
 * @param {MultipartParam[]} params FormDataへ追加するkey/valueの配列
 * @returns {Promise<Response>} fetchのレスポンス
 */
const multipartRequest = (uri, params) => {
  const requestConfig = createRequestConfig(uri, null, null, METHOD.POST);
  const formData = new FormData();
  if (Array.isArray(params)) {
    params.forEach((param) => {
      if (param.key !== undefined && param.value !== undefined) {
        formData.append(param.key, param.value);
      }
    });
  }
  requestConfig.options.headers.delete("Accept");
  requestConfig.options.headers.delete("Content-Type");
  requestConfig.options.body = formData;
  return fetcher(requestConfig);
};

/**
 * fetchを実行する。
 *
 * @param {FetchRequestConfig} requestConfig リクエスト送信の設定情報
 * @returns {Promise<Response>} fetchのレスポンス
 */
const fetcher = async (requestConfig) => {
  return fetch(requestConfig.requestUrl, requestConfig.options);
};

/**
 * fetchに渡すURLとoptionを組み立てる。
 *
 * PDF操作APIのCSRF相当チェックに必要な access-token ヘッダーを常に付与する。
 *
 * @param {string} uri リクエストURL
 * @param {*} requestData 送信するリクエストボディ
 * @param {Object|null} customHeader カスタムヘッダー
 * @param {string} method HTTP method
 * @returns {FetchRequestConfig} fetch用の設定情報
 */
const createRequestConfig = (uri, requestData, customHeader, method) => {
  const headers = new Headers();
  headers.append("access-token", Util.getCookieValue("token"));
  if (!Util.isEmpty(customHeader)) {
    Object.keys(customHeader).forEach((key) => {
      headers.set(key, customHeader[key]);
    });
  } else {
    Object.keys(defaultHeader).forEach((key) => {
      headers.set(key, defaultHeader[key]);
    });
  }
  if (method === METHOD.POST || method === METHOD.PUT) {
    const body = JSON.stringify(requestData);
    return {
      requestUrl: uri,
      options: { method, headers, body },
    };
  }
  return {
    requestUrl: uri,
    options: { method, headers },
  };
};

export default {
  getRequest,
  postRequest,
  putRequest,
  deleteRequest,
  multipartRequest,
};
