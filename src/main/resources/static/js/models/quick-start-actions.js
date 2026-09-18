/**
 * ホームへ落としたファイルの種別と、そこから選べる操作候補を決める。
 *
 * 「先に機能タブを探してから、そのタブでファイルを選ぶ」順序だと、目的のカードに着くまで
 * タブを行き来することになる。先にファイルを渡し、拡張子から行き先の候補を出す順序へ変える。
 *
 * 判定はMIME typeではなく拡張子で行う。ドロップされたファイルのMIME typeはOSとブラウザで
 * 揺れるうえ、`FileTypeValidator` とBEの受け入れ判定も拡張子で行っており、条件をそろえるため。
 */

/**
 * 受け取ったファイルの種別。
 */
const FILE_KIND = {
  PDF: "PDF",
  IMAGE: "IMAGE",
  MARKDOWN: "MARKDOWN",
  OFFICE: "OFFICE",
  HTML: "HTML",
  EPUB: "EPUB",
  UNKNOWN: "UNKNOWN",
};

/**
 * 操作候補の識別子。
 *
 * 押された候補をpdf-app.js側で既存のファイル選択処理へ振り分けるために使う。
 */
const QUICK_ACTION = {
  PDF_EDIT: "PDF_EDIT",
  PDF_MERGE: "PDF_MERGE",
  IMAGE_OCR: "IMAGE_OCR",
  IMAGE_TO_PDF: "IMAGE_TO_PDF",
  MARKDOWN_OPEN: "MARKDOWN_OPEN",
  OFFICE_CONVERT: "OFFICE_CONVERT",
  HTML_TO_PDF: "HTML_TO_PDF",
  HTML_TO_MARKDOWN: "HTML_TO_MARKDOWN",
  EPUB_TO_PDF: "EPUB_TO_PDF",
};

// 拡張子から種別を引く表。受け付ける拡張子は各カードの `accept` と同じ範囲にそろえる。
const KIND_EXTENSIONS = [
  { kind: FILE_KIND.PDF, extensions: [".pdf"] },
  {
    kind: FILE_KIND.IMAGE,
    extensions: [".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".tif", ".tiff"],
  },
  { kind: FILE_KIND.MARKDOWN, extensions: [".md", ".markdown"] },
  { kind: FILE_KIND.OFFICE, extensions: [".docx", ".xlsx", ".pptx"] },
  { kind: FILE_KIND.HTML, extensions: [".html", ".htm"] },
  { kind: FILE_KIND.EPUB, extensions: [".epub"] },
];

// 種別ごとの操作候補。同じカードへ着く操作は1つにまとめ、行き先が変わるものだけを並べる。
const KIND_ACTION_ITEMS = {
  [FILE_KIND.PDF]: [
    { id: QUICK_ACTION.PDF_EDIT, name: "PDF編集で開く" },
    { id: QUICK_ACTION.PDF_MERGE, name: "結合に追加する" },
  ],
  [FILE_KIND.IMAGE]: [
    { id: QUICK_ACTION.IMAGE_OCR, name: "画像OCRにかける" },
    { id: QUICK_ACTION.IMAGE_TO_PDF, name: "PDFにまとめる" },
  ],
  [FILE_KIND.MARKDOWN]: [
    { id: QUICK_ACTION.MARKDOWN_OPEN, name: "Markdownメモで開く" },
  ],
  [FILE_KIND.OFFICE]: [
    { id: QUICK_ACTION.OFFICE_CONVERT, name: "Office変換で開く" },
  ],
  [FILE_KIND.HTML]: [
    { id: QUICK_ACTION.HTML_TO_PDF, name: "HTMLからPDFにする" },
    { id: QUICK_ACTION.HTML_TO_MARKDOWN, name: "WebページからMarkdownにする" },
  ],
  [FILE_KIND.EPUB]: [
    { id: QUICK_ACTION.EPUB_TO_PDF, name: "EPUBからPDFにする" },
  ],
};

/**
 * ホームのファイル受け取り欄の初期状態を生成する。
 *
 * @returns {{fileObject: File|null, fileName: string, kind: string, actionItems: {id: string, name: string}[]}}
 *          ホームのファイル受け取り欄の画面状態
 */
const createQuickStartState = () => ({
  fileObject: null,
  fileName: "",
  kind: FILE_KIND.UNKNOWN,
  actionItems: [],
});

/**
 * ファイル名の拡張子から種別を判定する。
 *
 * @param {string} fileName 判定するファイル名
 * @returns {string} {@link FILE_KIND} のいずれか。該当しない場合はUNKNOWN
 */
const detectFileKind = (fileName) => {
  const lowerFileName = String(fileName || "").toLowerCase();
  const matched = KIND_EXTENSIONS.find((kindExtension) =>
    kindExtension.extensions.some((extension) =>
      lowerFileName.endsWith(extension)
    )
  );
  return matched ? matched.kind : FILE_KIND.UNKNOWN;
};

/**
 * 種別に対する操作候補を生成する。
 *
 * @param {string} kind {@link FILE_KIND} のいずれか
 * @returns {{id: string, name: string}[]} 操作候補。該当がない場合は空配列
 */
const createActionItems = (kind) => {
  return (KIND_ACTION_ITEMS[kind] || []).map((actionItem) => ({
    ...actionItem,
  }));
};

/**
 * 受け取ったファイルから、ホームのファイル受け取り欄の画面状態を生成する。
 *
 * 操作候補が無い種別も状態としては受け取る。画面側で「何もできない」ことを伝えるため、
 * 黙って捨てない。
 *
 * @param {File} fileObject 受け取ったファイル
 * @returns {{fileObject: File, fileName: string, kind: string, actionItems: {id: string, name: string}[]}}
 *          ホームのファイル受け取り欄の画面状態
 */
const buildQuickStartState = (fileObject) => {
  const kind = detectFileKind(fileObject.name);
  return {
    fileObject: fileObject,
    fileName: fileObject.name,
    kind: kind,
    actionItems: createActionItems(kind),
  };
};

export default {
  FILE_KIND,
  QUICK_ACTION,
  createQuickStartState,
  detectFileKind,
  createActionItems,
  buildQuickStartState,
};
