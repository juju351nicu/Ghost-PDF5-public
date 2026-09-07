/**
 * チェックボックス入力の画面状態。
 *
 * disabledは既存templateとの互換性を維持するため、booleanまたは`disabled`文字列を許容する。
 *
 * @typedef {Object} CheckboxState
 * @property {boolean} checked チェック状態
 * @property {boolean|string} disabled 入力可否。既存HTML互換のため`disabled`文字列も利用する
 * @property {string} message 入力欄に紐づくメッセージ
 */

/**
 * テキスト入力の画面状態。
 *
 * @typedef {Object} TextInputState
 * @property {string} text 入力値
 * @property {boolean|string} disabled 入力可否。既存HTML互換のため`disabled`文字列も利用する
 * @property {string} message 入力欄に紐づくメッセージ
 */

/**
 * 編集元PDFの画面状態。
 *
 * @typedef {Object} OriginalFileState
 * @property {string} fileName 画面に表示する編集元PDF名
 * @property {string} fileSize 画面表示用ファイルサイズ
 * @property {string} fileType 画面表示用ファイル種別
 * @property {File|null} fileObject 選択された編集元PDF
 * @property {string} previewUrl 選択された編集元PDFのプレビュー用Object URL
 * @property {CheckboxState} delPagesChecked 削除ページ指定チェック状態
 * @property {TextInputState} delPagesText 削除ページ入力状態
 * @property {boolean} fileFlag 既存画面互換のファイル選択フラグ
 */

/**
 * PDFメタデータの画面状態。
 *
 * @typedef {Object} PdfMetadataState
 * @property {boolean} loaded メタデータ取得済みか
 * @property {string} fileName ファイル名
 * @property {number|null} fileSize ファイルサイズ
 * @property {number|null} pageCount ページ数
 * @property {boolean|null} encrypted 暗号化有無
 */

/**
 * 差し込みPDF行の画面状態。
 *
 * @typedef {Object} InsertFileState
 * @property {number} fileNo 差し込みPDF行番号
 * @property {string} fileName 画面に表示する差し込みPDF名
 * @property {string} fileSize 画面表示用ファイルサイズ
 * @property {string} fileType 画面表示用ファイル種別
 * @property {File|null} fileObject 選択された差し込みPDF
 * @property {CheckboxState} insertPageChecked 差し込みページ指定チェック状態
 * @property {TextInputState} insertPageText 差し込みページ入力状態
 * @property {number} insertOption 差し込み方法
 * @property {{text: string, checked: boolean}} insertPrev 既存画面互換の先頭差し込み表示状態
 * @property {{text: string, checked: boolean}} insertNext 既存画面互換の末尾差し込み表示状態
 * @property {boolean} fileFlag 既存画面互換のファイル選択フラグ
 */

/**
 * 編集元PDFの初期画面状態を生成する。
 *
 * @returns {OriginalFileState} 編集元PDFの画面状態
 */
const createOriginalFileState = () => ({
  fileName: "編集元ファイル.pdf",
  fileSize: "",
  fileType: "",
  fileObject: null,
  previewUrl: "",
  delPagesChecked: { checked: false, disabled: "disabled", message: "" },
  delPagesText: { text: "", disabled: false, message: "" },
  splitRangesText: { text: "", message: "" },
  fileFlag: false,
});

/**
 * ページ選択用サムネイルの初期画面状態を生成する。
 *
 * pagesはBEから受け取ったサムネイル、selectedPageNumbersは利用者が選択した1始まりページ番号。
 *
 * @returns {{pages: Object[], selectedPageNumbers: number[], message: string}} サムネイルの画面状態
 */
const createThumbnailState = () => ({
  pages: [],
  selectedPageNumbers: [],
  message: "",
});

/**
 * 差し込みPDF行の初期画面状態を生成する。
 *
 * @param {number} fileNo 差し込みPDF行番号
 * @param {string} fileName 画面に表示するファイル名
 * @param {number} insertOption 差し込み方法
 * @returns {InsertFileState} 差し込みPDF行の画面状態
 */
const createInsertFileState = (fileNo, fileName, insertOption) => ({
  fileNo,
  fileName,
  fileSize: "",
  fileType: "",
  fileObject: null,
  insertPageChecked: {
    checked: false,
    disabled: "disabled",
    message: "",
  },
  insertPageText: { text: "", disabled: false, message: "" },
  insertOption,
  insertPrev: { text: "先頭ページに差し込む", checked: false },
  insertNext: { text: "最終ページに差し込む", checked: false },
  fileFlag: false,
});

/**
 * PDFメタデータの初期画面状態を生成する。
 *
 * @returns {PdfMetadataState} PDFメタデータの画面状態
 */
const createPdfMetadataState = () => ({
  loaded: false,
  fileName: "",
  fileSize: null,
  pageCount: null,
  encrypted: null,
});

/**
 * APIレスポンスからPDFメタデータの画面状態を生成する。
 *
 * @param {{fileName: string, fileSize: number, pageCount: number, encrypted: boolean}} metadata APIレスポンス
 * @returns {PdfMetadataState} PDFメタデータの画面状態
 */
const createLoadedPdfMetadataState = (metadata) => ({
  loaded: true,
  fileName: metadata.fileName,
  fileSize: metadata.fileSize,
  pageCount: metadata.pageCount,
  encrypted: metadata.encrypted,
});

/**
 * 差し込みPDF行の初期リストを生成する。
 *
 * @returns {InsertFileState[]} 差し込みPDF行リスト
 */
const createInitialInsertFiles = () => [
  createInsertFileState(1, "差し込み用ファイル1.pdf", 2),
  createInsertFileState(2, "差し込み用ファイル2.pdf", 2),
];

/**
 * 差し込み方法プルダウンの選択肢を生成する。
 *
 * @returns {{id: number, name: string}[]} 差し込み方法の選択肢
 */
const createInsertOptionItems = () => [
  {
    id: 1,
    name: "ページの後に差し込む",
  },
  {
    id: 2,
    name: "ページと差し替える",
  },
  {
    id: 3,
    name: "最後のページに差し込む",
  },
];

/**
 * 差し込みPDF行の次の行番号を計算する。
 *
 * @param {{fileNo: number}[]} insertFiles 現在の差し込みPDF行リスト
 * @param {number} currentNewNo 現在保持している新規行番号
 * @returns {number} 次に利用する行番号
 */
const calculateNextInsertFileNo = (insertFiles, currentNewNo) => {
  if (insertFiles.length === 0) {
    return currentNewNo;
  }
  const maxFileNo = Math.max(...insertFiles.map((insertFile) => insertFile.fileNo));
  return Math.max(currentNewNo, maxFileNo + 1);
};

/**
 * 画像Markdown下書きの初期状態を生成する。
 *
 * @returns {{fileObject: File|null, fileName: string, previewUrl: string}} 画像下書きの画面状態
 */
const createImageDraftState = () => ({
  fileObject: null,
  fileName: "",
  previewUrl: "",
});

export default {
  createImageDraftState,
  createOriginalFileState,
  createThumbnailState,
  createPdfMetadataState,
  createLoadedPdfMetadataState,
  createInsertFileState,
  createInitialInsertFiles,
  createInsertOptionItems,
  calculateNextInsertFileNo,
};
