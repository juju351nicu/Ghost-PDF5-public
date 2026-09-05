const PDF_OBJECT_URL_REVOKE_DELAY_MS = 60 * 1000;
const PDF_WINDOW_TARGET = "_blank";
const DEFAULT_DOWNLOAD_FILE_NAME = "download";

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
 * @param {Blob} fileBlob ダウンロード対象Blob
 * @param {Headers} responseHeaders レスポンスヘッダー
 * @param {string} defaultFileName Content-Dispositionが無い場合のファイル名
 */
const downloadBlob = (fileBlob, responseHeaders, defaultFileName) => {
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
  openPdfBlob,
  downloadBlob,
};
