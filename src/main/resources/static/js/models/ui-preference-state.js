import Util from "../util.js";

const ACTIVE_TAB_STORAGE_KEY = "ghostPdf5.activeTab";
const VALID_TAB_NAMES = ["home", "edit", "convert", "ocr", "memo"];
const MARKDOWN_DRAFT_STORAGE_KEY = "ghostPdf5.markdownDraft";
const OPERATION_SETTINGS_STORAGE_KEY = "ghostPdf5.operationSettings";
const RECENT_PDF_FILES_STORAGE_KEY = "ghostPdf5.recentPdfFiles";
// 古い履歴まで並べると、探すつもりの直近数件が埋もれる。
const RECENT_PDF_FILES_MAX_COUNT = 5;
// 自分専用ツールとして使う前提で、毎回選び直させない項目だけをここへ置く。
// 値の意味は各項目の生成元（pdf-form-state.js）のJSDocを参照。
const DEFAULT_OPERATION_SETTINGS = {
  markdownDraftMode: "",
  searchablePdfMode: "AUTO",
  imageFormat: "PNG",
  imageDpi: "",
  officeFormat: "DOCX",
  imagesPageSize: "A4",
  markdownFileName: "design-note.md",
};

/**
 * 画面をまたいで覚えておきたい表示設定・下書きを、ブラウザ内保存へ出し入れする。
 *
 * pdf-app.jsはパスワードなど機密の画面状態も保持するため、ブラウザ内保存を扱う処理は
 * この専用モジュールへ切り出す。誤って機密状態まで書き込んでしまう経路を増やさないため。
 */

/**
 * 前回開いていた機能タブ名を読み出す。
 *
 * 初回訪問（未保存）は、よく使う操作への導線をまとめた"home"タブから始める。
 *
 * @returns {string} 前回のタブ名。未保存または不正な値の場合は既定タブ"home"
 */
const loadActiveTab = () => {
  const storedTab = Util.getLocalStorage(ACTIVE_TAB_STORAGE_KEY);
  return VALID_TAB_NAMES.includes(storedTab) ? storedTab : "home";
};

/**
 * 開いている機能タブ名を保存する。
 *
 * @param {string} tabName 現在の機能タブ名
 */
const saveActiveTab = (tabName) => {
  Util.setLocalStorage(ACTIVE_TAB_STORAGE_KEY, tabName);
};

/**
 * 保存されていない編集中Markdownの下書きを読み出す。
 *
 * @returns {{fileName: string, content: string}|null} 下書き。存在しない・壊れている場合はnull
 */
const loadMarkdownDraft = () => {
  const rawDraft = Util.getLocalStorage(MARKDOWN_DRAFT_STORAGE_KEY);
  if (Util.isEmpty(rawDraft)) {
    return null;
  }
  try {
    const draft = JSON.parse(rawDraft);
    return Util.isEmpty(draft.content) ? null : draft;
  } catch (error) {
    return null;
  }
};

/**
 * 編集中Markdownの下書きを保存する。内容が空の場合は既存の下書きを削除する。
 *
 * @param {string} fileName 下書きのファイル名
 * @param {string} content 下書きの本文
 */
const saveMarkdownDraft = (fileName, content) => {
  if (Util.isEmpty(content)) {
    clearMarkdownDraft();
    return;
  }
  Util.setLocalStorage(
    MARKDOWN_DRAFT_STORAGE_KEY,
    JSON.stringify({ fileName: fileName, content: content })
  );
};

/**
 * 保存されている編集中Markdownの下書きを削除する。
 */
const clearMarkdownDraft = () => {
  Util.getLocalStorageObject()?.removeItem(MARKDOWN_DRAFT_STORAGE_KEY);
};

/**
 * 前回選んだ変換モード・出力形式などの設定を読み出す。
 *
 * 未保存の項目は既定値で補い、保存済みJSONが壊れている場合も既定値へ戻す。
 *
 * @returns {Object} 変換モード・出力形式などの設定
 */
const loadOperationSettings = () => {
  const rawSettings = Util.getLocalStorage(OPERATION_SETTINGS_STORAGE_KEY);
  if (Util.isEmpty(rawSettings)) {
    return { ...DEFAULT_OPERATION_SETTINGS };
  }
  try {
    return { ...DEFAULT_OPERATION_SETTINGS, ...JSON.parse(rawSettings) };
  } catch (error) {
    return { ...DEFAULT_OPERATION_SETTINGS };
  }
};

/**
 * 変換モード・出力形式などの設定を1項目だけ更新して保存する。
 *
 * @param {string} key DEFAULT_OPERATION_SETTINGSのキー
 * @param {*} value 保存する値
 */
const saveOperationSetting = (key, value) => {
  const settings = loadOperationSettings();
  settings[key] = value;
  Util.setLocalStorage(
    OPERATION_SETTINGS_STORAGE_KEY,
    JSON.stringify(settings)
  );
};

/**
 * 最近読み込んだPDFの履歴を読み出す。
 *
 * @returns {{fileName: string, fileSize: number, lastUsedTime: string}[]} 新しい順の履歴。未保存・破損時は空配列
 */
const loadRecentPdfFiles = () => {
  const rawFiles = Util.getLocalStorage(RECENT_PDF_FILES_STORAGE_KEY);
  if (Util.isEmpty(rawFiles)) {
    return [];
  }
  try {
    const recentFiles = JSON.parse(rawFiles);
    return Array.isArray(recentFiles) ? recentFiles : [];
  } catch (error) {
    return [];
  }
};

/**
 * 読み込んだPDFを履歴の先頭へ記録する。
 *
 * ブラウザはセキュリティ上Fileにパスを持たせないため、履歴からクリックで開き直すことはできない。
 * 「さっき触っていたのはどれか」を思い出すための記録に用途を限定し、名前・サイズ・利用日時だけを残す。
 * 中身は保存しない。
 *
 * @param {string} fileName 読み込んだPDFのファイル名
 * @param {number} fileSize 読み込んだPDFのファイルサイズ（byte）
 * @returns {{fileName: string, fileSize: number, lastUsedTime: string}[]} 更新後の履歴
 */
const addRecentPdfFile = (fileName, fileSize) => {
  const currentFiles = loadRecentPdfFiles().filter(
    (recentFile) => recentFile.fileName !== fileName
  );
  const recentFiles = [
    {
      fileName: fileName,
      fileSize: fileSize,
      lastUsedTime: new Date().toISOString(),
    },
    ...currentFiles,
  ].slice(0, RECENT_PDF_FILES_MAX_COUNT);
  Util.setLocalStorage(
    RECENT_PDF_FILES_STORAGE_KEY,
    JSON.stringify(recentFiles)
  );
  return recentFiles;
};

export default {
  loadActiveTab,
  saveActiveTab,
  loadMarkdownDraft,
  saveMarkdownDraft,
  clearMarkdownDraft,
  loadOperationSettings,
  saveOperationSetting,
  loadRecentPdfFiles,
  addRecentPdfFile,
};
