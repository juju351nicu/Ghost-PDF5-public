const PDF_EXTENSION = ".pdf";

/**
 * PDFとして受け付けられるファイルか判定する。
 *
 * ファイル選択ダイアログは `accept=".pdf"` で絞れるが、ドラッグ&ドロップは何でも渡せる。
 * 入力経路によって検証の強さが変わらないよう、判定をここへ置いて両方から呼ぶ。
 *
 * MIME typeではなく拡張子で判定する。BE側の受け入れ判定（`PdfTemporaryFileStorage`）も
 * 拡張子で行っており、条件をそろえるため。
 *
 * @param {File|null} fileObject 判定するファイル
 * @returns {boolean} PDFとして受け付けられる場合true
 */
const isPdfFile = (fileObject) => {
  if (!fileObject) {
    return false;
  }
  return fileObject.name.toLowerCase().endsWith(PDF_EXTENSION);
};

/**
 * PDF以外を指定されたときのメッセージを組み立てる。
 *
 * @param {File} fileObject 指定されたファイル
 * @returns {string} 画面表示用メッセージ
 */
const buildPdfFileTypeMessage = (fileObject) => {
  return "「" + fileObject.name + "」はPDFではありません。";
};

export default {
  isPdfFile,
  buildPdfFileTypeMessage,
};
