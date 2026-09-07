/**
 * 差し込みPDF行の画面状態。
 *
 * Spring MVCのModelAttributeバインディングに合わせるため、payload生成時は
 * `insertPdfForm[index].xxx` 形式のkeyへ変換する。
 *
 * @typedef {Object} InsertFileState
 * @property {File|null} fileObject 差し込みPDFファイル。未選択行は送信対象外
 * @property {{checked: boolean}} insertPageChecked 差し込みページ指定チェック状態
 * @property {{text: string}} insertPageText 差し込みページ番号入力
 * @property {number} insertOption 差し込み方法
 */

/**
 * PDFメタデータAPI用のmultipart payloadを生成する。
 *
 * @param {File} fileObject メタデータ取得対象PDF
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildMetadataPayload = (fileObject) => {
  return [{ key: "originalFile", value: fileObject }];
};

/**
 * PDFテキスト抽出API用のmultipart payloadを生成する。
 *
 * @param {File} fileObject テキスト抽出対象PDF
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildTextPayload = (fileObject) => {
  return [{ key: "originalFile", value: fileObject }];
};

/**
 * ページ単位Markdown下書きAPI用のmultipart payloadを生成する。
 *
 * @param {File} fileObject Markdown下書きの生成元PDF
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildMarkdownDraftPayload = (fileObject) => {
  return [{ key: "originalFile", value: fileObject }];
};

/**
 * PDFページ抽出API用のmultipart payloadを生成する。
 *
 * @param {File} originalFile 編集元PDF
 * @param {number[]} extractPages 抽出対象ページ番号リスト
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildExtractPayload = (originalFile, extractPages) => {
  return [
    { key: "originalFile", value: originalFile },
    {
      key: "extractPages",
      value: extractPages,
    },
  ];
};

/**
 * PDF結合API用のmultipart payloadを生成する。
 *
 * @param {InsertFileState[]} mergeFiles 結合対象PDF行リスト
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildMergePayload = (mergeFiles) => {
  return mergeFiles
    .filter((mergeData) => mergeData.fileObject)
    .map((mergeData) => ({
      key: "mergeFiles",
      value: mergeData.fileObject,
    }));
};

/**
 * PDF分割API用のmultipart payloadを生成する。
 *
 * 分割範囲が空の場合はkeyを送らない。BE側は未指定を「1ページずつ分割」として扱うため、
 * 空値を送ると「範囲指定あり」との区別が曖昧になる。
 *
 * @param {File} fileObject 分割対象PDF
 * @param {string[]} [splitRanges] `"1-5"` 形式の分割範囲リスト
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildSplitPayload = (fileObject, splitRanges) => {
  const payload = [{ key: "originalFile", value: fileObject }];
  if (Array.isArray(splitRanges) && splitRanges.length > 0) {
    payload.push({ key: "splitRanges", value: splitRanges });
  }
  return payload;
};

/**
 * ページ選択用サムネイルAPI用のmultipart payloadを生成する。
 *
 * ページ番号は送らない。1リクエストで全ページ分のサムネイルを受け取る設計のため。
 *
 * @param {File} fileObject サムネイルの生成元PDF
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildThumbnailPayload = (fileObject) => {
  return [{ key: "originalFile", value: fileObject }];
};

/**
 * ページ削除API用のmultipart payloadを生成する。
 *
 * @param {File} originalFile 編集元PDF
 * @param {number[]} deletePages 削除対象ページ番号リスト
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildDeletePayload = (originalFile, deletePages) => {
  return [
    { key: "originalFile", value: originalFile },
    {
      key: "originalDeletePages",
      value: deletePages,
    },
  ];
};

/**
 * PDF差し込みAPI用のmultipart payloadを生成する。
 * <p>
 * key名はSpringのModelAttributeバインディングに合わせる。
 *
 * @param {File} originalFile 編集元PDF
 * @param {number[]|null} deletePages 削除対象ページ番号リスト
 * @param {InsertFileState[]} insertFiles 差し込みPDF行リスト
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildInsertPayload = (originalFile, deletePages, insertFiles) => {
  const payload = [{ key: "originalFile", value: originalFile }];
  if (Array.isArray(deletePages)) {
    payload.push({
      key: "originalDeletePages",
      value: deletePages,
    });
  }
  insertFiles.forEach((insertData, index) => {
    if (!insertData.fileObject) {
      return;
    }
    payload.push({
      key: "insertPdfForm[" + index + "].insertFile",
      value: insertData.fileObject,
    });
    if (insertData.insertPageChecked.checked) {
      payload.push({
        key: "insertPdfForm[" + index + "].insertPage",
        value: Number.parseInt(insertData.insertPageText.text, 10),
      });
    }
    payload.push({
      key: "insertPdfForm[" + index + "].insertOption",
      value: insertData.insertOption,
    });
  });
  return payload;
};

export default {
  buildMetadataPayload,
  buildTextPayload,
  buildMarkdownDraftPayload,
  buildExtractPayload,
  buildMergePayload,
  buildSplitPayload,
  buildThumbnailPayload,
  buildDeletePayload,
  buildInsertPayload,
};
