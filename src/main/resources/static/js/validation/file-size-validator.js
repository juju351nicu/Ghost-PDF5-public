import CONST from "../const.js";

const BYTES_PER_MEGABYTE = 1024 * 1024;

/**
 * PDFのアップロード上限を超えていないか判定する。
 *
 * BE側は上限値以上を413で拒否するため、判定条件もBEと同じ「上限未満なら許容」にそろえる。
 *
 * @param {File|null} fileObject 選択されたファイル
 * @returns {boolean} 上限内、またはファイル未選択の場合true
 */
const isWithinPdfSizeLimit = (fileObject) => {
  if (!fileObject) {
    return true;
  }
  return fileObject.size < CONST.FILE_SIZE.MAX_PDF_BYTES;
};

/**
 * PDFのアップロード上限超過メッセージを組み立てる。
 *
 * 実サイズと上限の両方を出す。どちらか一方だと、どこまで小さくすればよいか分からないため。
 *
 * @param {File} fileObject 選択されたファイル
 * @returns {string} 画面表示用メッセージ
 */
const buildPdfSizeLimitMessage = (fileObject) => {
  return (
    "「" +
    fileObject.name +
    "」は " +
    formatMegabytes(fileObject.size) +
    "MB です。アップロードできるPDFは1ファイル " +
    formatMegabytes(CONST.FILE_SIZE.MAX_PDF_BYTES) +
    "MB 未満です。ページを分割してから指定してください。"
  );
};

/**
 * byte数をMB表記へ整形する。
 *
 * @param {number} bytes byte数
 * @returns {string} 小数第1位までのMB表記
 */
const formatMegabytes = (bytes) => {
  return (bytes / BYTES_PER_MEGABYTE).toFixed(1);
};

export default {
  isWithinPdfSizeLimit,
  buildPdfSizeLimitMessage,
};
