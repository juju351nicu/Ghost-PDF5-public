import FetchClient from "./fetch-client.js";
import ApiErrorUtils from "./api-error-utils.js";
import ApiResultUtils from "./api-result-utils.js";

const IMAGE_ERROR_MESSAGE =
  "画像の文字起こしに失敗しました。入力内容を確認してください。";
const UNEXPECTED_IMAGE_ERROR_MESSAGE =
  "画像の文字起こし中に予期しないエラーが発生しました。";

/**
 * 画像Markdown下書きAPIへmultipart requestを送り、成功時はJSONレスポンスを返す。
 *
 * @param {string} url 画像Markdown下書きAPIのURL
 * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
 * @returns {Promise<{imageDraftResponse: Object|null, messages: Object[], errorMessages: string[]}>} 文字起こし結果
 */
const requestImageMarkdownDraft = async (url, payload) => {
  const response = await FetchClient.multipartRequest(url, payload);
  if (!response.ok) {
    return {
      imageDraftResponse: null,
      messages: [],
      errorMessages: await ApiErrorUtils.extractErrorMessages(
        response,
        IMAGE_ERROR_MESSAGE
      ),
    };
  }
  const apiResult = await ApiResultUtils.readApiResult(response);
  return {
    imageDraftResponse: apiResult.data,
    messages: apiResult.messages,
    errorMessages: [],
  };
};

export default {
  requestImageMarkdownDraft,
  buildUnexpectedErrorMessage(error) {
    return ApiErrorUtils.buildUnexpectedErrorMessage(
      error,
      UNEXPECTED_IMAGE_ERROR_MESSAGE
    );
  },
};
