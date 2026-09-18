import ApiErrorUtils from "../api/api-error-utils.js";

/**
 * 同梱しているpdf.jsのバージョン。
 *
 * `static/vendor/pdfjs/README.md` の記載と同じ値を保つ。本体とworkerでバージョンがずれると
 * pdf.jsは起動時に例外を投げるため、ずれを `FrontendThumbnailContractTest` が検知する。
 */
const PDFJS_VERSION = "6.3.289";

const VENDOR_BASE_PATH = "/vendor/pdfjs/";
const PDFJS_MODULE_PATH = VENDOR_BASE_PATH + "build/pdf.min.mjs";
const PDFJS_WORKER_PATH = VENDOR_BASE_PATH + "build/pdf.worker.min.mjs";
// cmapsとstandard_fontsを渡さないと、日本語PDFの文字が欠けたり別のフォントへ置き換わったまま描画される。
// 末尾のスラッシュはpdf.jsがそのまま連結するため必須。
const PDFJS_CMAP_PATH = VENDOR_BASE_PATH + "cmaps/";
const PDFJS_STANDARD_FONT_PATH = VENDOR_BASE_PATH + "standard_fonts/";

/**
 * サムネイルを作るページ数の上限。
 *
 * 描画はブラウザ内で完結するため通信量の制約は無いが、ページ数だけcanvasを作るため
 * 極端に長いPDFでタブが固まるのを防ぐ。
 */
const MAX_PAGE_COUNT = 100;

/**
 * サムネイルの描画倍率。
 *
 * PDFの座標系は72dpi基準のため、40dpi相当は 40/72。A4で約331x468pxになり、
 * ページ選択に必要な見分けはつく。
 */
const RENDER_SCALE = 40 / 72;

// 画面へ出す文言はBEの `GlobalExceptionErrorHandler` と同じにする。同じ状況なのに
// 「サムネイルのときだけ言い回しが違う」状態を作らないため。一致は契約テストで固定する。
const PASSWORD_PROTECTED_MESSAGE =
  "このPDFはパスワードで保護されています。PDFを開くパスワードを入力してください。";
const PASSWORD_INCORRECT_MESSAGE =
  "パスワードが違うためPDFを開けません。もう一度入力してください。";
const PAGE_LIMIT_MESSAGE =
  "ページ数が多いためサムネイルを表示できません。ページ数が" +
  MAX_PAGE_COUNT +
  "以下のPDFを指定してください。";
const RENDER_FAILED_MESSAGE =
  "PDFを読み取れないため、サムネイルを表示できません。";

// pdf.jsがパスワード要求で投げる例外の区別。`PasswordResponses` の値と同じ。
const NEED_PASSWORD = 1;

// pdf.jsは458KBある。読み込みを画面表示時ではなくPDF選択時まで遅らせ、
// PDFを触らない利用（Markdownメモなど）の初期表示を重くしない。
let pdfjsLibPromise = null;

/**
 * pdf.js本体を1度だけ読み込み、worker・CMap・代替フォントの配信元を設定する。
 *
 * @returns {Promise<Object>} pdf.jsのモジュール
 */
const loadPdfjsLib = () => {
  if (pdfjsLibPromise === null) {
    pdfjsLibPromise = import(PDFJS_MODULE_PATH).then((pdfjsLib) => {
      pdfjsLib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER_PATH;
      return pdfjsLib;
    });
  }
  return pdfjsLibPromise;
};

/**
 * 画面側でBEと同じ扱いにできるエラーを作る。
 *
 * `errorCode` を載せることで、`failProcess` の振り分け（パスワード入力欄を出す・
 * 直せる失敗として出す）をBEのエラーと同じ経路で通せる。
 *
 * @param {string} errorCode `ApiErrorUtils.ERROR_CODE` のいずれか
 * @param {string} message 画面へ出す文言
 * @returns {Error} errorCode付きのエラー
 */
const buildThumbnailError = (errorCode, message) => {
  const error = new Error(message);
  error.errorCode = errorCode;
  return error;
};

/**
 * pdf.jsの例外を、画面が扱えるエラーへ読み替える。
 *
 * @param {*} error pdf.jsが投げた例外
 * @returns {Error} errorCode付きのエラー
 */
const toThumbnailError = (error) => {
  if (error?.name !== "PasswordException") {
    return buildThumbnailError(
      ApiErrorUtils.ERROR_CODE.PDF_PROCESSING,
      RENDER_FAILED_MESSAGE
    );
  }
  return error.code === NEED_PASSWORD
    ? buildThumbnailError(
        ApiErrorUtils.ERROR_CODE.PDF_PASSWORD_PROTECTED,
        PASSWORD_PROTECTED_MESSAGE
      )
    : buildThumbnailError(
        ApiErrorUtils.ERROR_CODE.PDF_PASSWORD_INCORRECT,
        PASSWORD_INCORRECT_MESSAGE
      );
};

/**
 * 1ページをcanvasへ描き、data URIとして取り出す。
 *
 * @param {Object} pdfPage pdf.jsのページ
 * @param {number} pageNumber 1始まりのページ番号
 * @returns {Promise<{pageNumber: number, dataUri: string, width: number, height: number}>} サムネイル1件
 */
const renderPage = async (pdfPage, pageNumber) => {
  const viewport = pdfPage.getViewport({ scale: RENDER_SCALE });
  const canvas = document.createElement("canvas");
  // 小数のままcanvasサイズにすると端が欠けるため、切り上げた整数で確保する。
  canvas.width = Math.ceil(viewport.width);
  canvas.height = Math.ceil(viewport.height);
  await pdfPage.render({ canvasContext: canvas.getContext("2d"), viewport })
    .promise;
  return {
    pageNumber,
    dataUri: canvas.toDataURL("image/png"),
    width: canvas.width,
    height: canvas.height,
  };
};

/**
 * ローカルのPDFをブラウザ内で描画し、ページ選択用サムネイルを作る。
 *
 * PDFはアップロードせず、選択されたFileをそのまま読む。サーバーへ送らないため、
 * パスワード付きPDFでもパスワードがブラウザの外へ出ない。
 *
 * @param {File} fileObject 選択された編集元PDF
 * @param {string} password PDFを開くパスワード。不要な場合は空文字
 * @returns {Promise<{pageCount: number, pages: Object[]}>} サムネイル一覧
 * @throws {Error} 読み取りに失敗した場合。`errorCode` に画面振り分け用のコードを持つ
 */
const renderThumbnails = async (fileObject, password) => {
  const pdfjsLib = await loadPdfjsLib();
  const fileBytes = await fileObject.arrayBuffer();
  const loadingTask = pdfjsLib.getDocument({
    data: fileBytes,
    password: password,
    cMapUrl: PDFJS_CMAP_PATH,
    cMapPacked: true,
    standardFontDataUrl: PDFJS_STANDARD_FONT_PATH,
  });
  try {
    let pdfDocument = null;
    try {
      pdfDocument = await loadingTask.promise;
    } catch (error) {
      throw toThumbnailError(error);
    }
    const pageCount = pdfDocument.numPages;
    if (pageCount > MAX_PAGE_COUNT) {
      throw buildThumbnailError(
        ApiErrorUtils.ERROR_CODE.PDF_PAGE_LIMIT_EXCEEDED,
        PAGE_LIMIT_MESSAGE
      );
    }
    const pages = [];
    // 全ページを同時に描くとページ数分のcanvasを一度に抱えるため、1ページずつ順番に描く。
    for (let pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
      const pdfPage = await pdfDocument.getPage(pageNumber);
      pages.push(await renderPage(pdfPage, pageNumber));
      pdfPage.cleanup();
    }
    return { pageCount, pages };
  } finally {
    // workerが抱えるPDFの解析結果を解放する。解放しないと選び直すたびにメモリが積み上がる。
    // 解放はdocumentではなくloadingTask側のAPI。パスワード違いで開けなかった場合も解放する。
    await loadingTask.destroy();
  }
};

export default {
  PDFJS_VERSION,
  MAX_PAGE_COUNT,
  renderThumbnails,
};
