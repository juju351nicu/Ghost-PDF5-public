/**
 * JSON成功レスポンスの共通ラッパー（ApiResult）を画面用の形へ変換する。
 *
 * BE側の `data` / `resultType` / `messageList` という構造を知るのはこのファイルだけにする。
 * エラー項目の解釈を `api-error-utils.js` に閉じているのと同じ理由で、
 * ラッパーの形が変わったときの修正箇所を1つに保つ。
 */

/**
 * 成功レスポンスのラッパーから、データとメッセージを取り出す。
 *
 * 旧形式（ラッパーなし）のレスポンスが混ざった場合でも画面が壊れないよう、
 * `data` を持たないbodyはそのままデータとして扱う。
 *
 * @param {Response} response fetchのレスポンス
 * @returns {Promise<{data: Object|Array|null, messages: {code: string, message: string}[]}>} 画面用の成功結果
 */
const readApiResult = async (response) => {
  const body = await response.json();
  if (body === null || typeof body !== "object" || !("data" in body)) {
    return { data: body, messages: [] };
  }
  return {
    data: body.data,
    messages: Array.isArray(body.messageList) ? body.messageList : [],
  };
};

export default {
  readApiResult,
};
