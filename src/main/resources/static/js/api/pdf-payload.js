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
 * 変換モードが空の場合はkeyを送らない。BE側は未指定を「文字レイヤーのみの従来動作」として扱うため、
 * 空値を送ると「モード指定あり」との区別が曖昧になる。
 *
 * @param {File} fileObject Markdown下書きの生成元PDF
 * @param {string} [mode] 変換モード（`"AUTO"` / `"VISION"`）。空文字・未指定で従来動作
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildMarkdownDraftPayload = (fileObject, mode) => {
  const payload = [{ key: "originalFile", value: fileObject }];
  if (typeof mode === "string" && mode.length > 0) {
    payload.push({ key: "mode", value: mode });
  }
  return payload;
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
 * PDFページ回転API用のmultipart payloadを生成する。
 *
 * 回転ページが空の場合はkeyを送らない。BE側は未指定を「全ページを回転」として扱うため、
 * 空値を送ると「ページ指定あり」との区別が曖昧になる。
 *
 * @param {File} fileObject 回転対象PDF
 * @param {number} rotation 回転角（90 / 180 / 270）
 * @param {number[]} [rotatePages] 回転対象ページ番号リスト
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildRotatePayload = (fileObject, rotation, rotatePages) => {
  const payload = [
    { key: "originalFile", value: fileObject },
    { key: "rotation", value: rotation },
  ];
  if (Array.isArray(rotatePages) && rotatePages.length > 0) {
    payload.push({ key: "rotatePages", value: rotatePages });
  }
  return payload;
};

/**
 * PDFページ画像化API用のmultipart payloadを生成する。
 *
 * 解像度と対象ページは空の場合にkeyを送らない。BE側は解像度未指定をサーバー設定の既定値、
 * ページ未指定を「全ページ」として扱うため、空値を送ると指定ありと区別できない。
 *
 * @param {File} fileObject 画像化対象PDF
 * @param {string} format 画像形式（PNG / JPG / TIFF / BMP）
 * @param {number|string} [dpi] 解像度（DPI）
 * @param {number[]} [imagePages] 画像化対象ページ番号リスト
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildImagesPayload = (fileObject, format, dpi, imagePages) => {
  const payload = [
    { key: "originalFile", value: fileObject },
    { key: "format", value: format },
  ];
  if (dpi !== null && dpi !== undefined && String(dpi).trim() !== "") {
    payload.push({ key: "dpi", value: dpi });
  }
  if (Array.isArray(imagePages) && imagePages.length > 0) {
    payload.push({ key: "imagePages", value: imagePages });
  }
  return payload;
};

/**
 * 画像からPDF作成API用のmultipart payloadを生成する。
 *
 * 画像は選択順にpayloadへ積む。BEは受信順をそのままページ順にするため、ここでの並びが最終的なページ順になる。
 *
 * @param {File[]} imageFiles PDF化する画像
 * @param {string} pageSize ページサイズ（A4 / FIT）
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildPdfFromImagesPayload = (imageFiles, pageSize) => {
  const payload = imageFiles.map((imageFile) => ({
    key: "imageFiles",
    value: imageFile,
  }));
  payload.push({ key: "pageSize", value: pageSize });
  return payload;
};

/**
 * PDFからHTML出力API用のmultipart payloadを生成する。
 *
 * 変換モードは空の場合にkeyを送らない。BE側は未指定を「文字レイヤーのみの従来動作」として扱うため、
 * 空文字を送るとモード指定ありとして型変換に回ってしまう。
 *
 * @param {File} fileObject 変換対象PDF
 * @param {string} [mode] 変換モード（AUTO / VISION）
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildHtmlPdfPayload = (fileObject, mode) => {
  const payload = [{ key: "originalFile", value: fileObject }];
  if (mode) {
    payload.push({ key: "mode", value: mode });
  }
  return payload;
};

/**
 * HTMLからPDF作成API用のmultipart payloadを生成する。
 *
 * @param {File} fileObject PDF化するHTMLファイル
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildPdfFromHtmlPayload = (fileObject) => {
  return [{ key: "htmlFile", value: fileObject }];
};

/**
 * Office文書変換API用のmultipart payloadを生成する。
 *
 * Markdown化とPDF化で同じ形を送る。形式はBEがファイルの拡張子から判定するため、FEからは指定しない。
 *
 * @param {File} fileObject 変換するOffice文書
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildOfficePayload = (fileObject) => {
  return [{ key: "officeFile", value: fileObject }];
};

/**
 * PDFからOffice文書出力API用のmultipart payloadを生成する。
 *
 * @param {File} fileObject 変換対象PDF
 * @param {string} format 出力形式（DOCX / XLSX / PPTX）
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildOfficeFromPdfPayload = (fileObject, format) => {
  return [
    { key: "originalFile", value: fileObject },
    { key: "format", value: format },
  ];
};

/**
 * EPUBからPDF作成API用のmultipart payloadを生成する。
 *
 * @param {File} fileObject PDF化するEPUBファイル
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildPdfFromEpubPayload = (fileObject) => {
  return [{ key: "epubFile", value: fileObject }];
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

/**
 * multipart payloadへPDFのパスワードを足したものを返す。
 *
 * payloadを組み立てる各methodへパスワードを配らず、送信直前にここでまとめて足す。
 * パスワードは「PDFを開けなかったので入力してもらう」という後から決まる値で、
 * payloadを組み立てる時点では分かっていないため。
 *
 * 未入力の場合はkeyを送らない。空文字を送ると、BE側が「パスワード指定あり」と解釈して
 * 保護されていないPDFまで復号処理へ回ってしまう。
 *
 * @param {{key: string, value: unknown}[]} payload 元のmultipart payload
 * @param {string} password PDFを開くためのパスワード
 * @returns {{key: string, value: unknown}[]} パスワードを足したmultipart payload
 */
const withPassword = (payload, password) => {
  if (!password) {
    return payload;
  }
  return payload.concat({ key: "password", value: password });
};

export default {
  withPassword,
  buildMetadataPayload,
  buildTextPayload,
  buildMarkdownDraftPayload,
  buildExtractPayload,
  buildMergePayload,
  buildSplitPayload,
  buildRotatePayload,
  buildImagesPayload,
  buildPdfFromImagesPayload,
  buildHtmlPdfPayload,
  buildPdfFromHtmlPayload,
  buildOfficePayload,
  buildOfficeFromPdfPayload,
  buildPdfFromEpubPayload,
  buildThumbnailPayload,
  buildDeletePayload,
  buildInsertPayload,
};
