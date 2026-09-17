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
 * @property {string} markdownDraftMode Markdown下書きの変換モード。空文字は従来動作（文字レイヤーのみ）
 * @property {string} searchablePdfMode 検索可能PDF生成の変換モード（AUTO / FORCE_OCR）
 * @property {number} rotation ページ回転角（90 / 180 / 270）
 * @property {string} imageFormat ページ画像化の出力形式（PNG / JPG / TIFF / BMP）
 * @property {string} imageDpi ページ画像化の解像度。空文字はサーバー設定の既定値を使う
 * @property {string} officeFormat PDFから出力するOffice形式（DOCX / XLSX / PPTX）
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
  // 既定は従来動作。これまでFEはmodeを送っていなかったため、初期値を変えると既存の挙動が変わる。
  markdownDraftMode: "",
  // 既定はAUTO。文字レイヤーが無いページだけをOCR対象にする、最も安全側の動作にする。
  searchablePdfMode: "AUTO",
  // 縦向きの資料を横向きに直す用途が最も多いため、既定は時計回り90度にする。
  rotation: 90,
  // 文字と線画がにじまないPNGを既定にする。写真主体ならJPGへ切り替える。
  imageFormat: "PNG",
  // 空文字はサーバー設定の既定値を使う。FEに既定dpiを持たせるとBEの設定変更が効かなくなる。
  imageDpi: "",
  // 文字を直したい用途が最も多いため、既定はWordにする。
  officeFormat: "DOCX",
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
 * Markdown下書きの変換モードプルダウンの選択肢を生成する。
 *
 * idは `mode` としてBEへ送る値で、空文字は「送らない」＝従来動作を表す。
 *
 * @returns {{id: string, name: string}[]} 変換モードの選択肢
 */
const createMarkdownDraftModeItems = () => [
  {
    id: "",
    name: "従来動作（文字レイヤーのみ）",
  },
  {
    id: "AUTO",
    name: "AUTO（文字が無いページだけAI変換）",
  },
  {
    id: "VISION",
    name: "VISION（全ページAI変換・費用発生）",
  },
];

/**
 * 検索可能PDF生成の変換モードプルダウンの選択肢を生成する。
 *
 * idはBEの `SearchablePdfMode` のコード値と一致させる。
 *
 * @returns {{id: string, name: string}[]} 変換モードの選択肢
 */
const createSearchablePdfModeItems = () => [
  {
    id: "AUTO",
    name: "AUTO（文字レイヤーが無いページだけOCR）",
  },
  {
    id: "FORCE_OCR",
    name: "FORCE_OCR（全ページをOCR）",
  },
];

/**
 * ページ回転角プルダウンの選択肢を生成する。
 *
 * idは `rotation` としてBEへ送る値で、BEの `PdfRotation` のコード値と一致させる。
 * 0（回転なし）は選択肢に置かない。回転しないなら回転APIを呼ばないため。
 *
 * @returns {{id: number, name: string}[]} 回転角の選択肢
 */
const createRotationItems = () => [
  {
    id: 90,
    name: "時計回りに90度",
  },
  {
    id: 180,
    name: "180度",
  },
  {
    id: 270,
    name: "反時計回りに90度",
  },
];

/**
 * ページ画像化の出力形式プルダウンの選択肢を生成する。
 *
 * idは `format` としてBEへ送る値で、BEの `PdfImageFormat` のコード値と一致させる。
 *
 * @returns {{id: string, name: string}[]} 画像形式の選択肢
 */
const createImageFormatItems = () => [
  {
    id: "PNG",
    name: "PNG（可逆・文字がにじまない）",
  },
  {
    id: "JPG",
    name: "JPG（非可逆・写真向き）",
  },
  {
    id: "TIFF",
    name: "TIFF（可逆・印刷向き）",
  },
  {
    id: "BMP",
    name: "BMP（無圧縮）",
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

/**
 * PDFから出力するOffice形式プルダウンの選択肢を生成する。
 *
 * idは `format` としてBEへ送る値で、BEの `OfficeDocumentType` のコード値と一致させる。
 *
 * @returns {{id: string, name: string}[]} Office形式の選択肢
 */
const createOfficeFormatItems = () => [
  {
    id: "DOCX",
    name: "Word（文字は編集可・レイアウト不可）",
  },
  {
    id: "XLSX",
    name: "Excel（1ページ1シート・表は復元しない）",
  },
  {
    id: "PPTX",
    name: "PowerPoint（見た目は保持・文字は選択不可）",
  },
];

/**
 * EPUBからPDFを作るカードの初期画面状態を生成する。
 *
 * @returns {{fileObject: File|null, fileName: string}} EPUBカードの画面状態
 */
const createEpubState = () => ({
  fileObject: null,
  fileName: "",
});

/**
 * Office文書変換カードの初期画面状態を生成する。
 *
 * @returns {{fileObject: File|null, fileName: string}} Office文書カードの画面状態
 */
const createOfficeState = () => ({
  fileObject: null,
  fileName: "",
});

/**
 * HTMLからPDFを作るカードの初期画面状態を生成する。
 *
 * @returns {{fileObject: File|null, fileName: string}} HTML PDFカードの画面状態
 */
const createHtmlPdfState = () => ({
  fileObject: null,
  fileName: "",
});

/**
 * 画像からPDFを作るカードの初期画面状態を生成する。
 *
 * @returns {{files: File[], pageSize: string}} 画像PDFカードの画面状態
 */
const createImagesPdfState = () => ({
  files: [],
  // 印刷や共有に回すことが多いため、既定はA4に収める。原寸を保ちたい場合だけFITへ切り替える。
  pageSize: "A4",
});

/**
 * 画像からPDFを作る際のページサイズプルダウンの選択肢を生成する。
 *
 * idは `pageSize` としてBEへ送る値で、BEの `PdfImagePageSize` のコード値と一致させる。
 *
 * @returns {{id: string, name: string}[]} ページサイズの選択肢
 */
const createImagePageSizeItems = () => [
  {
    id: "A4",
    name: "A4に収める（縦横比は保持）",
  },
  {
    id: "FIT",
    name: "画像サイズに合わせる（余白なし）",
  },
];

export default {
  createImageDraftState,
  createImagesPdfState,
  createHtmlPdfState,
  createOfficeState,
  createEpubState,
  createOfficeFormatItems,
  createImagePageSizeItems,
  createOriginalFileState,
  createThumbnailState,
  createPdfMetadataState,
  createLoadedPdfMetadataState,
  createInsertFileState,
  createInitialInsertFiles,
  createInsertOptionItems,
  createMarkdownDraftModeItems,
  createSearchablePdfModeItems,
  createRotationItems,
  createImageFormatItems,
  calculateNextInsertFileNo,
};
