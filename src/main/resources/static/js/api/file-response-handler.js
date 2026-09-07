const PDF_OBJECT_URL_REVOKE_DELAY_MS = 60 * 1000;
const PDF_WINDOW_TARGET = "_blank";
const DEFAULT_DOWNLOAD_FILE_NAME = "download";
const PICKER_CANCEL_ERROR_NAME = "AbortError";

/**
 * 保存ダイアログに渡す拡張子フィルタ。
 *
 * File System Access APIの型定義をこのファイルの外へ出さないため、ここで定義して公開する。
 */
const FILE_TYPES = {
  ZIP: [{ description: "ZIP", accept: { "application/zip": [".zip"] } }],
};

/**
 * 保存先を利用者に選ばせる。
 *
 * File System Access APIはChrome / Edgeのみ対応で、Firefox / Safari / モバイルは未対応。
 * 未対応ブラウザでは何もせず、呼び出し側が従来のBlobダウンロードへ倒せるようにする。
 * <p>
 * このAPIはtransient activation（利用者操作の直後）を要求し、Chromeでは数秒で失効する。
 * そのためAPI呼び出しの前、クリック直後に呼ぶこと。fetch完了後では失効している可能性がある。
 *
 * @param {string} suggestedName 既定のファイル名
 * @param {{description: string, accept: Object}[]} types 拡張子フィルタ
 * @returns {Promise<{supported: boolean, cancelled: boolean, handle: FileSystemFileHandle|null}>} 保存先の取得結果
 */
const requestSaveTarget = async (suggestedName, types) => {
  if (typeof window.showSaveFilePicker !== "function") {
    return { supported: false, cancelled: false, handle: null };
  }
  try {
    const handle = await window.showSaveFilePicker({ suggestedName, types });
    return { supported: true, cancelled: false, handle };
  } catch (error) {
    // キャンセルはAbortErrorとして飛んでくる。APIエラーではないため、エラー表示へは回さない。
    if (error?.name === PICKER_CANCEL_ERROR_NAME) {
      return { supported: true, cancelled: true, handle: null };
    }
    throw error;
  }
};

/**
 * PDF Blobを別タブで開き、作成したObject URLを一定時間後に解放する。
 *
 * @param {Blob} pdfBlob PDF操作APIから返却されたPDF Blob
 */
const openPdfBlob = (pdfBlob) => {
  const fileUrl = URL.createObjectURL(pdfBlob);
  // 別タブから元画面を操作されないよう、noopenerを明示する。
  window.open(fileUrl, PDF_WINDOW_TARGET, "noopener");
  setTimeout(
    () => URL.revokeObjectURL(fileUrl),
    PDF_OBJECT_URL_REVOKE_DELAY_MS
  );
};

/**
 * Blobをファイルとしてダウンロードし、作成したObject URLを一定時間後に解放する。
 *
 * 保存先(saveTarget)が指定された場合は、Object URLと `<a>` 要素を作らずhandleへ直接書き込む。
 * 書き込みの失敗は握りつぶさず例外として投げ、呼び出し側でエラーメッセージへ変換できるようにする。
 * 省略時は従来どおりブラウザのダウンロード機能へ渡す。
 *
 * @param {Blob} fileBlob ダウンロード対象Blob
 * @param {Headers} responseHeaders レスポンスヘッダー
 * @param {string} defaultFileName Content-Dispositionが無い場合のファイル名
 * @param {FileSystemFileHandle} [saveTarget] 利用者が選んだ保存先
 * @returns {Promise<void>} 保存処理の完了Promise
 */
const downloadBlob = async (
  fileBlob,
  responseHeaders,
  defaultFileName,
  saveTarget
) => {
  if (saveTarget) {
    const writable = await saveTarget.createWritable();
    await writable.write(fileBlob);
    await writable.close();
    return;
  }
  const fileUrl = URL.createObjectURL(fileBlob);
  const linkElement = document.createElement("a");
  linkElement.href = fileUrl;
  linkElement.download =
    extractDownloadFileName(responseHeaders) ||
    defaultFileName ||
    DEFAULT_DOWNLOAD_FILE_NAME;
  document.body.appendChild(linkElement);
  linkElement.click();
  linkElement.remove();
  setTimeout(
    () => URL.revokeObjectURL(fileUrl),
    PDF_OBJECT_URL_REVOKE_DELAY_MS
  );
};

/**
 * Content-Dispositionからダウンロードファイル名を抽出する。
 *
 * @param {Headers} responseHeaders レスポンスヘッダー
 * @returns {string} ファイル名。取得できない場合は空文字
 */
const extractDownloadFileName = (responseHeaders) => {
  const contentDisposition = responseHeaders.get("Content-Disposition");
  if (!contentDisposition) {
    return "";
  }
  const encodedFileName = contentDisposition.match(
    /filename\*=UTF-8''([^;]+)/i
  );
  if (encodedFileName) {
    return decodeURIComponent(encodedFileName[1].replace(/"/g, ""));
  }
  const fileName = contentDisposition.match(/filename="?([^";]+)"?/i);
  return fileName ? fileName[1] : "";
};

export default {
  FILE_TYPES,
  openPdfBlob,
  requestSaveTarget,
  downloadBlob,
};
