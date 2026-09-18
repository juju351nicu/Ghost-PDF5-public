import CONST from "../const.js";

const BYTES_PER_MEGABYTE = 1024 * 1024;

/**
 * 共通のアップロード上限を超えていないか判定する。
 *
 * BE側は上限値以上を413で拒否するため、判定条件もBEと同じ「上限未満なら許容」にそろえる。
 * 上限はPDF・Office・HTMLで共通のため、種別ごとに判定を書き分けない。
 *
 * @param {File|null} fileObject 選択されたファイル
 * @returns {boolean} 上限内、またはファイル未選択の場合true
 */
const isWithinUploadSizeLimit = (fileObject) => {
  if (!fileObject) {
    return true;
  }
  return fileObject.size < CONST.FILE_SIZE.MAX_PDF_BYTES;
};

/**
 * PDFのアップロード上限を超えていないか判定する。
 *
 * @param {File|null} fileObject 選択されたファイル
 * @returns {boolean} 上限内、またはファイル未選択の場合true
 */
const isWithinPdfSizeLimit = (fileObject) => {
  return isWithinUploadSizeLimit(fileObject);
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
    formatExceededMegabytes(fileObject.size) +
    "MB です。アップロードできるPDFは1ファイル " +
    formatLimitMegabytes(CONST.FILE_SIZE.MAX_PDF_BYTES) +
    "MB 未満です。"
  );
};

/**
 * 上限を超えたときに利用者が取れる行動を組み立てる。
 *
 * 以前は「ページを分割してから指定してください」とだけ出していたが、この画面の分割も
 * 同じ上限を通るため、超過したPDFはここでは分割できない。実行できない指示を出さない。
 *
 * @returns {string} 画面表示用の次の行動
 */
const buildPdfSizeLimitHint = () => {
  return (
    "この画面の分割・抽出も同じ上限を通るため、超過したPDFをここで小さくすることはできません。" +
    "PDFの作成元でページを分けたファイルを指定してください。"
  );
};

/**
 * 種別を問わない上限超過メッセージを組み立てる。
 *
 * PDF以外（Markdownの読み込みなど）でも上限の説明が要るため、PDF専用の文言とは別に用意する。
 *
 * @param {File} fileObject 選択されたファイル
 * @returns {string} 画面表示用メッセージ
 */
const buildUploadSizeLimitMessage = (fileObject) => {
  return (
    "「" +
    fileObject.name +
    "」は " +
    formatExceededMegabytes(fileObject.size) +
    "MB です。この画面で扱えるファイルは1件 " +
    formatLimitMegabytes(CONST.FILE_SIZE.MAX_PDF_BYTES) +
    "MB 未満です。"
  );
};

/**
 * 超過したファイルサイズをMB表記へ整形する。
 *
 * 上限をわずかに超えた場合に上限と同じ表記になると矛盾して見えるため、小数第1位へ切り上げる。
 *
 * @param {number} bytes byte数
 * @returns {string} 小数第1位まで切り上げたMB表記
 */
const formatExceededMegabytes = (bytes) => {
  return (Math.ceil((bytes / BYTES_PER_MEGABYTE) * 10) / 10).toFixed(1);
};

/**
 * 上限サイズをMB表記へ整形する。
 *
 * @param {number} bytes byte数
 * @returns {string} 小数を含まないMB表記
 */
const formatLimitMegabytes = (bytes) => {
  return String(Math.floor(bytes / BYTES_PER_MEGABYTE));
};

export default {
  isWithinUploadSizeLimit,
  isWithinPdfSizeLimit,
  buildUploadSizeLimitMessage,
  buildPdfSizeLimitMessage,
  buildPdfSizeLimitHint,
};
