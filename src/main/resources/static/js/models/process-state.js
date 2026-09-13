/**
 * 1件の操作を実行し終えるまでの画面状態。
 *
 * booleanのisProcessingだけでは「処理中」「利用者が直せる失敗」「終わった」を区別できず、
 * ボタンが押せなくなること以外を利用者へ伝えられなかった。状態を明示して持つ。
 *
 * 状態は「次に取れる行動が1つ以上あるもの」だけを置く。行動が同じ状態は分けない。
 * 送信中と処理中を分けていないのはこのため。進捗率を取っていない現状では、
 * 2つを分けても画面の出しわけも次の行動も変わらない。
 */
const PROCESS_STATE = {
  /** 初期状態。パネルを表示しない。 */
  IDLE: "IDLE",

  /** 送信からBE処理完了まで。操作を止め、何をしているかと経過時間を出す。 */
  PROCESSING: "PROCESSING",

  /** 成功。結果と「やり直す」を出す。 */
  DONE: "DONE",

  /** 利用者が自分で直せる失敗（サイズ超過など）。直し方を出す。 */
  NEEDS_ACTION: "NEEDS_ACTION",

  /** パスワードで保護されたPDFで、パスワード待ち。入力欄を出し、同じ操作をやり直す。 */
  PASSWORD_REQUIRED: "PASSWORD_REQUIRED",

  /** 利用者が直せない失敗。内容を出して初期状態へ戻す導線だけ用意する。 */
  ERROR: "ERROR",
};

/**
 * 利用者の操作を止めるべき状態。
 */
const BUSY_STATES = [PROCESS_STATE.PROCESSING];

/**
 * 何をしている最中かを利用者へ出す文言。
 *
 * 操作ごとに所要時間の桁が違う（メタデータ取得は一瞬、VISIONのMarkdown下書きは分単位）ため、
 * 「処理中」だけにせず、どのAPIを待っているかが分かる文言を出す。
 */
const PROCESS_LABEL = {
  METADATA: "PDFの基本情報を読み取っています",
  TEXT: "PDFからテキストを抽出しています",
  MARKDOWN_DRAFT: "PDFをMarkdownへ変換しています",
  IMAGE_DRAFT: "画像を文字起こししています",
  THUMBNAILS: "ページのサムネイルを作成しています",
  PDF_EDIT: "PDFを編集しています",
  FILE_DOWNLOAD: "出力ファイルを生成しています",
  MARKDOWN_FILE: "Markdownファイルを操作しています",
  MARKDOWN_PDF: "MarkdownからPDFを生成しています",
  OFFICE_MARKDOWN: "Office文書をMarkdownへ変換しています",
};

/**
 * 処理状態パネルの表示内容。
 *
 * @typedef {Object} ProcessPanelState
 * @property {string} state PROCESS_STATE のいずれか
 * @property {string} title 状態の見出し
 * @property {string} detail 補足説明。空文字なら出さない
 * @property {string} hint 次に取れる行動の説明。空文字なら出さない
 * @property {number} elapsedSeconds PROCESSINGの経過秒
 * @property {string} password PASSWORD_REQUIREDで入力中のパスワード
 */

/**
 * 処理状態パネルの初期状態を作成する。
 *
 * @returns {ProcessPanelState} 初期状態
 */
const createProcessPanelState = () => {
  return {
    state: PROCESS_STATE.IDLE,
    title: "",
    detail: "",
    hint: "",
    elapsedSeconds: 0,
    password: "",
  };
};

/**
 * 指定した状態が利用者の操作を止める状態か判定する。
 *
 * @param {string} state 判定する状態
 * @returns {boolean} 操作を止める状態の場合true
 */
const isBusyState = (state) => {
  return BUSY_STATES.includes(state);
};

export default {
  PROCESS_STATE,
  PROCESS_LABEL,
  createProcessPanelState,
  isBusyState,
};
