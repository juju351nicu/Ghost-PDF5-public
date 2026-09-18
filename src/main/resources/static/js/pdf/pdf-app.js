import Util from "../util.js";
import CONST from "../const.js";
import Modal from "../components/modal.js";
import ApiMessageList from "../components/api-message-list.js";
import TheHeader from "../components/theheader.js";
import TheFooter from "../components/thefooter.js";
import HomePanel from "../components/home-panel.js";
import OriginalPdfForm from "../components/original-pdf-form.js";
import InsertPdfRow from "../components/insert-pdf-row.js";
import ImageOcrForm from "../components/image-ocr-form.js";
import WebMarkdownForm from "../components/web-markdown-form.js";
import ImagesToPdfForm from "../components/images-to-pdf-form.js";
import HtmlToPdfForm from "../components/html-to-pdf-form.js";
import OfficeForm from "../components/office-form.js";
import EpubToPdfForm from "../components/epub-to-pdf-form.js";
import ProcessPanel from "../components/process-panel.js";
import PdfApiClient from "../api/pdf-api-client.js";
import FileResponseHandler from "../api/file-response-handler.js";
import ImageApiClient from "../api/image-api-client.js";
import MarkdownApiClient from "../api/markdown-api-client.js";
import PdfPayload from "../api/pdf-payload.js";
import ImagePayload from "../api/image-payload.js";
import PdfFormState from "../models/pdf-form-state.js";
import ProcessState from "../models/process-state.js";
import UiPreferenceState from "../models/ui-preference-state.js";
import QuickStartActions from "../models/quick-start-actions.js";
import PageNumberValidator from "../validation/page-number-validator.js";
import FileSizeValidator from "../validation/file-size-validator.js";
import FileTypeValidator from "../validation/file-type-validator.js";
import ApiErrorUtils from "../api/api-error-utils.js";
import PdfThumbnailRenderer from "./pdf-thumbnail-renderer.js";

const draggable = window["vuedraggable"];
const SPLIT_PDF_FILE_NAME = "split.zip";
const IMAGES_PDF_FILE_NAME = "images.zip";
const HTML_PDF_FILE_NAME = "document.html";
const EPUB_PDF_FILE_NAME = "document.epub";
const MARKDOWN_FILES_CSV_FILE_NAME = "markdown-files.csv";
// 入力の都度ブラウザ内保存へ書き込むとI/Oが増えるため、入力が止まってからまとめて書き込む。
const MARKDOWN_DRAFT_SAVE_DELAY_MS = 3000;

/**
 * 編集元PDFの初期状態に、前回選んだ変換モード・出力形式などを適用して生成する。
 *
 * 初めて開いた利用者はPdfFormState側の既定値のまま、前回の記憶があれば上書きする。
 * 全クリア時も同じ状態を作り直すため、生成そのものをここへまとめる。
 *
 * @returns {Object} 前回設定を反映した編集元PDFの画面状態
 */
const buildOriginalFileState = () => {
  const settings = UiPreferenceState.loadOperationSettings();
  const originalFileState = PdfFormState.createOriginalFileState();
  originalFileState.markdownDraftMode = settings.markdownDraftMode;
  originalFileState.searchablePdfMode = settings.searchablePdfMode;
  originalFileState.imageFormat = settings.imageFormat;
  originalFileState.imageDpi = settings.imageDpi;
  originalFileState.officeFormat = settings.officeFormat;
  return originalFileState;
};

/**
 * 画像からPDFを作るカードの初期状態に、前回選んだ用紙サイズを適用して生成する。
 *
 * @returns {Object} 前回設定を反映した画像PDFカードの画面状態
 */
const buildImagesPdfState = () => {
  const settings = UiPreferenceState.loadOperationSettings();
  const imagesPdfState = PdfFormState.createImagesPdfState();
  imagesPdfState.pageSize = settings.imagesPageSize;
  return imagesPdfState;
};

/**
 * PDF編集画面のVue app定義。
 *
 * WebJar Vueを利用している現行構成を維持しつつ、起動処理はmain.jsへ分離する。
 * 将来 npm / Vite へ移行する際は、このapp定義をそのままimport対象にする。
 */
const pdfApp = {
  components: {
    draggable: draggable,
    modal: Modal,
    "the-header": TheHeader,
    "the-footer": TheFooter,
    "home-panel": HomePanel,
    "original-pdf-form": OriginalPdfForm,
    "insert-pdf-row": InsertPdfRow,
    "image-ocr-form": ImageOcrForm,
    "web-markdown-form": WebMarkdownForm,
    "images-to-pdf-form": ImagesToPdfForm,
    "html-to-pdf-form": HtmlToPdfForm,
    "office-form": OfficeForm,
    "epub-to-pdf-form": EpubToPdfForm,
    "process-panel": ProcessPanel,
    "api-message-list": ApiMessageList,
  },
  data() {
    return {
      // 機能タブの選択状態。タブを切り替えても各カードのデータ（選択ファイルや入力内容）は
      // このコンポーネントの状態としてそのまま残るため、切り替え時に読み直す必要はない。
      // 前回開いていたタブを復元し、毎回「OCR・Markdown化」タブを探す手間を省く。
      activeTab: UiPreferenceState.loadActiveTab(),
      // ホームで先に受け取ったファイルと、その拡張子から出した操作候補。
      quickStart: QuickStartActions.createQuickStartState(),
      // 読み込んだPDFの名前・サイズ・利用日時だけの履歴。中身は保存しない。
      recentPdfFiles: UiPreferenceState.loadRecentPdfFiles(),
      originalFile: buildOriginalFileState(),
      pdfMetadata: PdfFormState.createPdfMetadataState(),
      pdfThumbnails: PdfFormState.createThumbnailState(),
      insertFiles: PdfFormState.createInitialInsertFiles(),
      // ホームの「PDFを結合」からPDF編集タブへ移動したとき、折りたたみパネルを開いた状態にするための状態。
      // ネイティブ<details>の開閉と2-way bindingするため、@toggleでも書き戻す。
      insertMergePanelOpen: false,
      newNo: 3,
      insertPagePulldown: PdfFormState.createInsertOptionItems(),
      isShowModal: false,
      errorMessages: [],
      apiMessages: [],
      processPanel: ProcessState.createProcessPanelState(),
      elapsedTimerId: null,
      pdfPassword: "",
      pendingPasswordRetry: null,
      imageDraft: PdfFormState.createImageDraftState(),
      webMarkdown: PdfFormState.createWebMarkdownState(),
      webMarkdownModeItems: PdfFormState.createWebMarkdownModeItems(),
      imagesPdf: buildImagesPdfState(),
      htmlPdf: PdfFormState.createHtmlPdfState(),
      officeDocument: PdfFormState.createOfficeState(),
      epubDocument: PdfFormState.createEpubState(),
      markdownFileName: UiPreferenceState.loadOperationSettings().markdownFileName,
      markdownContent: "",
      markdownFiles: [],
      isMarkdownFileListLoaded: false,
      markdownPreviewHtml: "",
      markdownMessage: "",
      aiTransformResult: null,
      markdownDraftSaveTimerId: null,
    };
  },
  mounted() {
    Util.detectBrowserName();
    Util.canUseLocalStorage();
    this.restoreMarkdownDraftFromLocalStorage();
    // watch(activeTab)は値の変化にしか反応しないため、開いた状態で起動した場合はここで読みに行く。
    if (this.activeTab === "home") {
      this.requestListMarkdownFiles();
    }
    // ドロップ領域の外へ落としたPDFは、既定動作のままだとブラウザがそのファイルを開いて
    // 画面を離れてしまい、入力中の内容が失われる。window側で既定動作だけを止める。
    window.addEventListener("dragover", this.preventWindowFileDrop);
    window.addEventListener("drop", this.preventWindowFileDrop);
    window.addEventListener("keydown", this.handleMarkdownShortcut);
  },
  beforeUnmount() {
    window.removeEventListener("dragover", this.preventWindowFileDrop);
    window.removeEventListener("drop", this.preventWindowFileDrop);
    window.removeEventListener("keydown", this.handleMarkdownShortcut);
    this.stopElapsedTimer();
    if (this.markdownDraftSaveTimerId !== null) {
      window.clearTimeout(this.markdownDraftSaveTimerId);
    }
  },
  computed: {
    /**
     * 利用者の操作を止めるべき状態か判定する。
     *
     * 各コンポーネントへ渡している `is-processing` の契約を保つため、
     * 状態機械へ移行した後もこの名前のまま公開する。
     *
     * @returns {boolean} 処理中の場合はtrue
     */
    isProcessing() {
      return ProcessState.isBusyState(this.processPanel.state);
    },
    /**
     * Markdown編集欄に本文が入っているか判定する。
     *
     * 保存・プレビュー・PDF出力・コピー・AI変換は、本文が空だと実行しても意味がない。
     * 押した後に「対象がありません」と返すより、押せない状態を先に見せる。
     *
     * @returns {boolean} 本文が入っている場合はtrue
     */
    hasMarkdownContent() {
      return !Util.isEmpty(this.markdownContent);
    },
    /**
     * Markdownファイル名が入力されているか判定する。
     *
     * 読込・更新・削除は保存済みファイルを名前で特定するため、空欄では実行できない。
     *
     * @returns {boolean} ファイル名が入力されている場合はtrue
     */
    hasMarkdownFileName() {
      return !Util.isEmpty(this.markdownFileName.trim());
    },
    /**
     * PDF差込みを実行できるか判定する。
     *
     * 差し込み先の編集元PDFと、差し込む側のPDFが1件以上そろっている必要がある。
     *
     * @returns {boolean} 差込みを実行できる場合はtrue
     */
    canInsertPdf() {
      return (
        !Util.isEmpty(this.originalFile.fileObject) &&
        this.selectedInsertFileCount >= 1
      );
    },
    /**
     * PDF結合を実行できるか判定する。
     *
     * 結合は差し込み行のPDFだけを対象とするため、2件以上選ばれている必要がある。
     *
     * @returns {boolean} 結合を実行できる場合はtrue
     */
    canMergePdf() {
      return this.selectedInsertFileCount >= 2;
    },
    /**
     * 差し込み行でPDFが選択されている件数。
     *
     * @returns {number} PDFが選択されている差し込み行の件数
     */
    selectedInsertFileCount() {
      return this.insertFiles.filter(
        (insertData) => !Util.isEmpty(insertData.fileObject)
      ).length;
    },
    /**
     * ホームタブへ出す、最近保存したMarkdownの上位5件。
     *
     * Markdownメモタブの一覧は既存仕様どおりファイル名順のまま保ち、ホーム向けにここだけ
     * 更新日時の新しい順へ並べ替える。更新日時が空の項目は最後に回す。
     *
     * @returns {Object[]} 更新日時の新しい順に並べたMarkdownファイル情報。最大5件
     */
    recentMarkdownFiles() {
      return this.markdownFiles
        .slice()
        .sort((leftFile, rightFile) =>
          (rightFile.lastModifiedTime || "").localeCompare(
            leftFile.lastModifiedTime || ""
          )
        )
        .slice(0, 5);
    },
  },
  watch: {
    originalFile: {
      handler(newValue, _oldValue) {
        this.originalFile.delPagesText.text = Util.toHalfWidth(
          newValue.delPagesText.text
        );
      },
      deep: true,
    },
    activeTab(newValue) {
      UiPreferenceState.saveActiveTab(newValue);
      // ホームの「最近保存したMarkdown」は、一覧を取得済みでなければ開いたときに1度だけ読みに行く。
      if (newValue === "home" && !this.isMarkdownFileListLoaded) {
        this.requestListMarkdownFiles();
      }
    },
    markdownContent(_newValue, _oldValue) {
      this.clearMarkdownPreview();
      this.scheduleMarkdownDraftSave();
    },
    markdownFileName(newValue) {
      this.scheduleMarkdownDraftSave();
      UiPreferenceState.saveOperationSetting("markdownFileName", newValue);
    },
    // 変換モード・出力形式などは、次にPDFを選び直しても毎回選ばせないよう選択のたびに覚える。
    "originalFile.markdownDraftMode"(newValue) {
      UiPreferenceState.saveOperationSetting("markdownDraftMode", newValue);
    },
    "originalFile.searchablePdfMode"(newValue) {
      UiPreferenceState.saveOperationSetting("searchablePdfMode", newValue);
    },
    "originalFile.imageFormat"(newValue) {
      UiPreferenceState.saveOperationSetting("imageFormat", newValue);
    },
    "originalFile.imageDpi"(newValue) {
      UiPreferenceState.saveOperationSetting("imageDpi", newValue);
    },
    "originalFile.officeFormat"(newValue) {
      UiPreferenceState.saveOperationSetting("officeFormat", newValue);
    },
    "imagesPdf.pageSize"(newValue) {
      UiPreferenceState.saveOperationSetting("imagesPageSize", newValue);
    },
  },
  methods: {
    /**
     * 処理中状態へ移し、何を待っているかを画面へ出す。
     *
     * @param {string} label 実行中の処理を説明する文言
     */
    beginProcess(label) {
      this.processPanel = {
        state: ProcessState.PROCESS_STATE.PROCESSING,
        title: label,
        detail: "",
        hint: "",
        elapsedSeconds: 0,
      };
      this.startElapsedTimer();
    },
    /**
     * 完了状態へ移し、結果とやり直す導線を出す。
     *
     * 結果の表示場所を別に持つ操作（Markdown欄へ反映するなど）は完了パネルを出さず、
     * `resetProcess` で初期状態へ戻す。同じ文言を2箇所へ出さないため。
     *
     * @param {string} message 完了内容を説明する文言
     */
    finishProcess(message) {
      this.stopElapsedTimer();
      this.processPanel = {
        state: ProcessState.PROCESS_STATE.DONE,
        title: message,
        detail: "",
        hint: "",
        elapsedSeconds: 0,
      };
    },
    /**
     * 利用者が自分で直せる失敗として、直し方とともに表示する。
     *
     * @param {string} title 何が起きたか
     * @param {string} hint 次に取れる行動
     */
    blockProcess(title, hint) {
      this.stopElapsedTimer();
      this.processPanel = {
        state: ProcessState.PROCESS_STATE.NEEDS_ACTION,
        title: title,
        detail: "",
        hint: hint,
        elapsedSeconds: 0,
      };
    },
    /**
     * APIエラーを、利用者が直せるかどうかで振り分ける。
     *
     * 直せる失敗（サイズ超過、パスワード保護、ページ上限超過など）は、次の行動を添えて
     * パネルへ出す。直せない失敗は従来どおりモーダルで通知する。
     *
     * @param {string[]} errorMessages APIが返したエラーメッセージ
     * @param {string[]} errorCodes APIが返したエラーコード
     */
    failProcess(errorMessages, errorCodes) {
      this.stopElapsedTimer();
      if (ApiErrorUtils.isPasswordError(errorCodes)) {
        this.processPanel = {
          state: ProcessState.PROCESS_STATE.PASSWORD_REQUIRED,
          title: errorMessages[0],
          detail: "",
          hint: "入力したパスワードは、このPDFを開くためだけに使い、保存しません。",
          elapsedSeconds: 0,
          password: "",
        };
        return;
      }
      if (ApiErrorUtils.isRecoverableError(errorCodes)) {
        this.processPanel = {
          state: ProcessState.PROCESS_STATE.NEEDS_ACTION,
          title: errorMessages[0],
          detail: errorMessages.slice(1).join(" "),
          hint: "",
          elapsedSeconds: 0,
        };
        return;
      }
      this.processPanel = ProcessState.createProcessPanelState();
      this.errorMessages = errorMessages;
      this.showMessageModal();
    },
    /**
     * 想定外エラーを従来どおりモーダルで通知し、パネルを初期状態へ戻す。
     *
     * @param {string} message 画面表示用メッセージ
     */
    failUnexpectedProcess(message) {
      this.stopElapsedTimer();
      this.processPanel = ProcessState.createProcessPanelState();
      this.errorMessages = [message];
      this.showMessageModal();
    },
    /**
     * 処理状態パネルを初期状態へ戻す。
     */
    resetProcess() {
      this.stopElapsedTimer();
      this.processPanel = ProcessState.createProcessPanelState();
    },
    /**
     * 処理中のまま取り残された場合に初期状態へ戻す。
     *
     * 成功・失敗の分岐で状態を決めた後の保険として `finally` から呼ぶ。
     * 分岐の中で例外が起きても、操作できないまま固まらないようにする。
     */
    endProcessIfBusy() {
      if (this.isProcessing) {
        this.resetProcess();
      }
    },
    /**
     * 経過秒の計測を開始する。
     *
     * VISIONのMarkdown下書きはページ数分の外部API呼び出しで分単位かかる。数字が動いていれば
     * 止まっていないことが分かり、再読み込みによる呼び出しの無駄打ちを防げる。
     */
    startElapsedTimer() {
      this.stopElapsedTimer();
      this.elapsedTimerId = window.setInterval(() => {
        this.processPanel.elapsedSeconds = this.processPanel.elapsedSeconds + 1;
      }, 1000);
    },
    /**
     * 経過秒の計測を止める。
     */
    stopElapsedTimer() {
      if (this.elapsedTimerId === null) {
        return;
      }
      window.clearInterval(this.elapsedTimerId);
      this.elapsedTimerId = null;
    },
    /**
     * ドロップ領域の外へ落とされたファイルの既定動作を止める。
     *
     * @param {DragEvent} event ドラッグイベント
     */
    preventWindowFileDrop(event) {
      event.preventDefault();
    },
    /**
     * 入力されたパスワードで、直前と同じ操作をやり直す。
     *
     * パスワードは画面状態として保持し、以降の操作にも自動で付ける。同じPDFを操作するたびに
     * 入力し直さずに済ませるため。ファイルを選び直したときと全クリア時に破棄する。
     */
    submitPdfPassword() {
      const retry = this.pendingPasswordRetry;
      if (Util.isEmpty(this.processPanel.password) || Util.isEmpty(retry)) {
        return;
      }
      this.pdfPassword = this.processPanel.password;
      this.resetProcess();
      retry();
    },
    /**
     * 保持しているPDFのパスワードを破棄する。
     */
    clearPdfPassword() {
      this.pdfPassword = "";
      this.pendingPasswordRetry = null;
    },
    /**
     * 完了パネルから次のファイルへ進む。
     */
    startOverProcess() {
      this.resetProcess();
      this.clearAll();
    },
    /**
     * 成功レスポンスの通知メッセージを画面へ反映する。
     *
     * メッセージの内容と件数はBEが決めるため、画面側は受け取ったものをそのまま渡す。
     *
     * @param {{code: string, message: string}[]} messages 成功レスポンスの通知メッセージ
     */
    applyApiMessages(messages) {
      this.apiMessages = Util.isEmpty(messages) ? [] : messages;
    },
    /**
     * 成功レスポンスの通知メッセージを消す。
     *
     * 直前の操作の通知が次の操作の結果として残らないよう、エラーメッセージの初期化と同じ位置で呼ぶ。
     */
    clearApiMessages() {
      this.apiMessages = [];
    },
    /**
     * エラーメッセージモーダルを表示する。
     */
    showMessageModal() {
      this.isShowModal = true;
    },
    /**
     * エラーメッセージモーダルを非表示にする。
     */
    hideMessageModal() {
      this.isShowModal = false;
    },
    /**
     * 差し込みPDF行の並び替え完了時に呼び出される。
     *
     * 現時点ではv-modelの配列順がそのまま送信順になるため、追加処理は行わない。
     */
    handleInsertFileOrderChange() {},
    /**
     * 差し込みPDFの入力行を追加する。
     */
    addInsertFileRow() {
      const nextFileNo = PdfFormState.calculateNextInsertFileNo(
        this.insertFiles,
        this.newNo
      );
      this.newNo = nextFileNo;
      if (nextFileNo > 10) {
        this.errorMessages = ["表示件数は、10件まで！"];
        this.showMessageModal();
        return;
      }
      this.insertFiles.push(
        PdfFormState.createInsertFileState(
          this.newNo,
          "追加PDFは" + this.newNo,
          3
        )
      );
    },
    /**
     * 指定した差し込みPDF行を削除する。
     *
     * @param {number} fileNo 差し込みPDF行番号
     */
    removeInsertFileRow(fileNo) {
      const index = this.findInsertFileIndex(fileNo);
      if (index === -1) {
        return;
      }
      this.insertFiles.splice(index, 1);
    },
    /**
     * 削除ページ入力をAPI送信用のページ番号リストに変換する。
     *
     * @param {string} pageText 削除ページ入力。例: "1, 3-5"
     * @returns {number[]} 重複を除いたページ番号リスト
     */
    parseDeletePages(pageText) {
      const result = PageNumberValidator.parseDeletePagesText(pageText);
      this.originalFile.delPagesText.message = result.message;
      return result.pages;
    },
    /**
     * サムネイルの選択状態と、ページ指定入力欄への反映を初期化する。
     */
    clearThumbnailSelection() {
      this.pdfThumbnails.selectedPageNumbers = [];
      this.applySelectedPagesToPageInput();
    },
    /**
     * サムネイル1ページ分の選択・解除を切り替える。
     *
     * @param {number} pageNumber 1始まりのページ番号
     */
    toggleThumbnailPage(pageNumber) {
      const selectedPageNumbers = this.pdfThumbnails.selectedPageNumbers;
      this.pdfThumbnails.selectedPageNumbers = selectedPageNumbers.includes(
        pageNumber
      )
        ? selectedPageNumbers.filter(
            (selectedPage) => selectedPage !== pageNumber
          )
        : selectedPageNumbers.concat(pageNumber);
      this.applySelectedPagesToPageInput();
    },
    /**
     * 選択したページを既存の「ページ指定」入力欄へ反映する。
     *
     * 入力欄は残したまま値だけを書き換える。手入力の操作を壊さず、選択結果をそのまま
     * 「抽出する」「削除する」へ渡せるようにするため。
     * 抽出・削除はページ指定チェックがONのときだけ動くため、選択があるかどうかにチェックを合わせる。
     */
    applySelectedPagesToPageInput() {
      const pagesText = PageNumberValidator.buildPagesText(
        this.pdfThumbnails.selectedPageNumbers
      );
      this.originalFile.delPagesText.text = pagesText;
      this.originalFile.delPagesText.message = "";
      this.originalFile.delPagesChecked.checked = !Util.isEmpty(pagesText);
    },
    /**
     * 分割範囲入力を解析し、エラーメッセージを画面状態へ反映する。
     *
     * @param {string} rangesText 分割範囲入力
     * @returns {string[]} 分割範囲リスト。空配列は1ページずつ分割を表す
     */
    parseSplitRanges(rangesText) {
      const result = PageNumberValidator.parseSplitRangesText(rangesText);
      this.originalFile.splitRangesText.message = result.message;
      return result.ranges;
    },
    /**
     * 差し込みPDF行番号から配列indexを取得する。
     *
     * @param {number} fileNo 差し込みPDF行番号
     * @returns {number} 見つかった配列index。存在しない場合は-1
     */
    findInsertFileIndex(fileNo) {
      const fileNoIndex = this.insertFiles.findIndex(
        (fileData) => fileData.fileNo === fileNo
      );
      return fileNoIndex;
    },
    /**
     * 編集元PDFと差し込みPDF行を初期状態へ戻す。
     */
    clearAll() {
      // 画面状態を作り直す前に、プレビュー用Object URLを解放する。
      this.clearOriginalPdfPreview();
      this.clearPdfPassword();
      this.originalFile = buildOriginalFileState();
      this.pdfMetadata = PdfFormState.createPdfMetadataState();
      this.pdfThumbnails = PdfFormState.createThumbnailState();
      // 既存仕様に合わせ、全クリア後は削除ページ入力欄をdisabled扱いに戻す。
      this.originalFile.delPagesText.disabled = "disabled";
      for (let index = 0; index < this.insertFiles.length; index++) {
        const fileNo = this.insertFiles[index].fileNo;
        this.clearInsertFileRow(fileNo);
      }
    },
    /**
     * 指定した差し込みPDF行のファイル情報と入力値を初期化する。
     *
     * @param {number} fileNo 初期化対象の差し込みPDF行番号
     */
    clearInsertFileRow(fileNo) {
      const index = this.findInsertFileIndex(fileNo);
      if (index === -1) {
        return;
      }
      this.insertFiles.splice(
        index,
        1,
        PdfFormState.createInsertFileState(fileNo, "", 1)
      );
    },
    /**
     * PDFファイル選択時に画面状態を更新し、選択PDFをプレビュー表示する。
     *
     * @param {Event} event ファイル選択イベント
     * @param {number} fileNo 差し込みPDF行番号。編集元PDFの場合は-1
     */
    handlePdfFileChange(event, fileNo) {
      const fileObject = event.target.files[0];
      if (Util.isEmpty(fileObject)) {
        return;
      }
      if (!this.applyPdfFile(fileObject, fileNo)) {
        // 受け付けなかったファイル名が選択欄に残ると、保持中のPDFと表示が食い違うため戻す。
        event.target.value = "";
      }
    },
    /**
     * ドロップされたPDFを、ファイル選択と同じ経路で画面状態へ反映する。
     *
     * 検証を通す経路をファイル選択と共通にする。ドロップ経路だけ検証が緩い状態を作らないため。
     *
     * @param {File[]} files ドロップされたファイル
     * @param {number} fileNo 差し込みPDF行番号。編集元PDFの場合は-1
     */
    handlePdfFilesDropped(files, fileNo) {
      const fileObject = files[0];
      if (Util.isEmpty(fileObject)) {
        return;
      }
      this.applyPdfFile(fileObject, fileNo);
    },
    /**
     * PDFを検証し、受け付けられる場合だけ画面状態へ反映する。
     *
     * @param {File} fileObject 受け取ったファイル
     * @param {number} fileNo 差し込みPDF行番号。編集元PDFの場合は-1
     * @returns {boolean} 受け付けた場合はtrue
     */
    applyPdfFile(fileObject, fileNo) {
      if (!FileTypeValidator.isPdfFile(fileObject)) {
        this.blockProcess(
          FileTypeValidator.buildPdfFileTypeMessage(fileObject),
          "拡張子が .pdf のファイルを指定してください。"
        );
        return false;
      }
      if (!FileSizeValidator.isWithinPdfSizeLimit(fileObject)) {
        // 上限超過はサーバーへ送らずここで止める。送るとTomcatが上限検知時に接続を切るため、
        // ブラウザには413ではなく理由の分からないネットワークエラーだけが残る。
        this.blockProcess(
          FileSizeValidator.buildPdfSizeLimitMessage(fileObject),
          FileSizeValidator.buildPdfSizeLimitHint()
        );
        return false;
      }
      this.resetProcess();
      // 別のPDFには前のパスワードが通らない。持ち越すと「違う」とだけ言われて理由が分からなくなる。
      this.clearPdfPassword();
      this.recentPdfFiles = UiPreferenceState.addRecentPdfFile(
        fileObject.name,
        fileObject.size
      );
      const index = this.findInsertFileIndex(fileNo);
      if (index !== -1) {
        this.insertFiles[index].fileObject = fileObject;
        this.insertFiles[index].fileName = fileObject.name;
        // 差し込みPDFはカード内に表示枠を持たないため、従来どおり別タブで開く。
        FileResponseHandler.openPdfBlob(fileObject);
        return true;
      }
      this.originalFile.fileObject = fileObject;
      this.originalFile.fileName = fileObject.name;
      this.pdfMetadata = PdfFormState.createPdfMetadataState();
      this.updateOriginalPdfPreview(fileObject);
      // 前のPDFのサムネイルと選択が残ると、表示とこれから操作する対象が食い違う。
      this.pdfThumbnails = PdfFormState.createThumbnailState();
      // ローカル描画なので選び直しのたびに走らせても通信は発生しない。結果を待たずに選択処理を終える。
      this.renderPdfThumbnails({ promptPassword: false });
      return true;
    },
    /**
     * 編集元PDFのプレビュー用Object URLを差し替える。
     *
     * ローカルのFileをそのまま表示するため、プレビューのためにPDFをアップロードしない。
     * 以前のURLはメモリを掴み続けるため、差し替え前に必ず解放する。
     *
     * @param {File} fileObject 選択された編集元PDF
     */
    updateOriginalPdfPreview(fileObject) {
      this.clearOriginalPdfPreview();
      this.originalFile.previewUrl = URL.createObjectURL(fileObject);
    },
    /**
     * 編集元PDFのプレビュー用Object URLを解放する。
     */
    clearOriginalPdfPreview() {
      if (!Util.isEmpty(this.originalFile.previewUrl)) {
        URL.revokeObjectURL(this.originalFile.previewUrl);
        this.originalFile.previewUrl = "";
      }
    },
    /**
     * 選択中の編集元PDFを別タブで開く。
     *
     * カード内の表示枠では小さいページを確認しづらいため、大きく見る導線を用意する。
     */
    openOriginalPdfInNewTab() {
      if (Util.isEmpty(this.originalFile.fileObject)) {
        return;
      }
      FileResponseHandler.openPdfBlob(this.originalFile.fileObject);
    },
    /**
     * 編集元PDFから指定ページを削除し、生成されたPDFを別タブで開く。
     */
    requestDeletePdf() {
      const originalFileData = this.originalFile;
      // ファイル選択されていなかった場合
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      // チェックボックスがチェックされていなかった場合
      if (!originalFileData.delPagesChecked.checked) {
        this.originalFile.delPagesText.message =
          "チェックボックスがチェックされておりません。";
        return;
      }
      this.originalFile.delPagesText.message = "";
      const deletePages = this.parseDeletePages(
        originalFileData.delPagesText.text
      );
      if (!Util.isEmpty(this.originalFile.delPagesText.message)) {
        return;
      }
      const payload = PdfPayload.buildDeletePayload(
        originalFileData.fileObject,
        deletePages
      );
      this.requestPdfAndOpen(CONST.REST_PATH.DELETE_PDF, payload);
    },
    /**
     * 編集元PDFから指定ページを抽出し、生成されたPDFを別タブで開く。
     */
    requestExtractPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      if (!originalFileData.delPagesChecked.checked) {
        this.originalFile.delPagesText.message =
          "チェックボックスがチェックされておりません。";
        return;
      }
      this.originalFile.delPagesText.message = "";
      const extractPages = this.parseDeletePages(
        originalFileData.delPagesText.text
      );
      if (!Util.isEmpty(this.originalFile.delPagesText.message)) {
        return;
      }
      const payload = PdfPayload.buildExtractPayload(
        originalFileData.fileObject,
        extractPages
      );
      this.requestPdfAndOpen(CONST.REST_PATH.EXTRACT_PDF, payload);
    },
    /**
     * 編集元PDFの指定ページを回転し、結果PDFを別タブで開く。
     *
     * ページ指定のチェックが外れている場合はページ番号を送らず、BE側の「全ページ回転」に任せる。
     * 抽出・削除と違い、回転は未指定を「対象なし」ではなく「全ページ」と解釈するため。
     */
    requestRotatePdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      this.originalFile.delPagesText.message = "";
      const rotatePages = originalFileData.delPagesChecked.checked
        ? this.parseDeletePages(originalFileData.delPagesText.text)
        : [];
      if (!Util.isEmpty(this.originalFile.delPagesText.message)) {
        return;
      }
      const payload = PdfPayload.buildRotatePayload(
        originalFileData.fileObject,
        originalFileData.rotation,
        rotatePages
      );
      this.requestPdfAndOpen(CONST.REST_PATH.ROTATE_PDF, payload);
    },
    /**
     * 編集元PDFから検索可能PDF（OCRサンドイッチ）を生成し、結果PDFを別タブで開く。
     */
    requestSearchablePdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message = "ファイル選択されておりません。";
        return;
      }
      const payload = PdfPayload.buildSearchablePdfPayload(
        originalFileData.fileObject,
        originalFileData.searchablePdfMode
      );
      this.requestPdfAndOpen(CONST.REST_PATH.SEARCHABLE_PDF, payload);
    },
    /**
     * 編集元PDFの基本情報を取得し、画面へ表示する。
     *
     * @returns {Promise<void>} PDFメタデータ取得処理の完了Promise
     */
    requestMetadataPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return Promise.resolve();
      }
      return this.requestPdfMetadata(
        PdfPayload.buildMetadataPayload(originalFileData.fileObject)
      );
    },
    /**
     * 編集元PDFからテキストを抽出し、Markdown編集欄へ反映する。
     *
     * @returns {Promise<void>} PDFテキスト抽出処理の完了Promise
     */
    requestTextPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return Promise.resolve();
      }
      return this.requestPdfText(
        PdfPayload.buildTextPayload(originalFileData.fileObject)
      );
    },
    /**
     * 編集元PDFからページ単位Markdown下書きを生成し、編集欄へ反映する。
     *
     * @returns {Promise<void>} Markdown下書き生成処理の完了Promise
     */
    requestMarkdownDraftPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return Promise.resolve();
      }
      return this.requestPdfMarkdownDraft(
        PdfPayload.buildMarkdownDraftPayload(
          originalFileData.fileObject,
          originalFileData.markdownDraftMode
        )
      );
    },
    /**
     * 画像OCRカードで選択・貼り付けされた画像を画面状態へ反映し、プレビューを更新する。
     *
     * @param {File} file 選択またはクリップボードから受け取った画像
     */
    handleImageSelected(file) {
      if (Util.isEmpty(file)) {
        return;
      }
      this.revokeImagePreview();
      this.imageDraft.fileObject = file;
      this.imageDraft.fileName = file.name;
      this.imageDraft.previewUrl = URL.createObjectURL(file);
    },
    /**
     * 画像プレビュー用に作成したObject URLを解放する。
     */
    revokeImagePreview() {
      if (!Util.isEmpty(this.imageDraft.previewUrl)) {
        URL.revokeObjectURL(this.imageDraft.previewUrl);
        this.imageDraft.previewUrl = "";
      }
    },
    /**
     * 画像OCRカードの選択状態を初期化する。
     */
    clearImageDraft() {
      this.revokeImagePreview();
      this.imageDraft = PdfFormState.createImageDraftState();
    },
    /**
     * 編集元PDFをHTMLへ変換し、生成されたHTMLをダウンロードする。
     *
     * Markdown下書きと同じ変換モードを使う。画面の下書きモード選択をそのまま送るため、
     * 「下書きで確認した内容がそのままHTMLになる」ことを利用者が予測できる。
     *
     * @returns {Promise<void>} HTML出力処理の完了Promise
     */
    async requestHtmlPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      this.originalFile.delPagesText.message = "";
      const saveTarget = await this.requestSaveTarget(
        HTML_PDF_FILE_NAME,
        FileResponseHandler.FILE_TYPES.HTML
      );
      if (saveTarget.cancelled) {
        return;
      }
      const payload = PdfPayload.buildHtmlPdfPayload(
        originalFileData.fileObject,
        originalFileData.markdownDraftMode
      );
      await this.requestFileAndDownload(
        CONST.REST_PATH.HTML_PDF,
        payload,
        HTML_PDF_FILE_NAME,
        saveTarget.handle
      );
    },
    /**
     * 編集元PDFをOffice文書へ変換し、生成されたファイルをダウンロードする。
     *
     * 画像化・分割と同じく、対応ブラウザでは先に保存先を選ばせる。File System Access APIは
     * 利用者操作の直後しか使えないため、API呼び出しの前に呼ぶ必要がある。
     *
     * @returns {Promise<void>} Office出力処理の完了Promise
     */
    async requestOfficeFromPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      this.originalFile.delPagesText.message = "";
      const fileName = this.buildOfficeFileName(originalFileData.officeFormat);
      const saveTarget = await this.requestSaveTarget(
        fileName,
        FileResponseHandler.FILE_TYPES.OFFICE
      );
      if (saveTarget.cancelled) {
        return;
      }
      const payload = PdfPayload.buildOfficeFromPdfPayload(
        originalFileData.fileObject,
        originalFileData.officeFormat
      );
      await this.requestFileAndDownload(
        CONST.REST_PATH.OFFICE_FROM_PDF,
        payload,
        fileName,
        saveTarget.handle
      );
    },
    /**
     * Office出力の既定ダウンロードファイル名を組み立てる。
     *
     * サーバーは元PDF名から名前を決めるが、保存ダイアログはAPI呼び出しの前に出すため、
     * その時点ではサーバーの決めた名前が分からない。ここでは拡張子だけを合わせた既定名を出す。
     *
     * @param {string} officeFormat 出力形式（DOCX / XLSX / PPTX）
     * @returns {string} 既定のダウンロードファイル名
     */
    buildOfficeFileName(officeFormat) {
      return "document." + officeFormat.toLowerCase();
    },
    /**
     * 保存済みMarkdownの一覧をCSVでダウンロードする。
     *
     * 他のダウンロードと同じく、対応ブラウザでは先に保存先を選ばせる。
     *
     * @returns {Promise<void>} CSV出力処理の完了Promise
     */
    async requestMarkdownFilesCsv() {
      const saveTarget = await this.requestSaveTarget(
        MARKDOWN_FILES_CSV_FILE_NAME,
        FileResponseHandler.FILE_TYPES.CSV
      );
      if (saveTarget.cancelled) {
        return;
      }
      if (this.isProcessing) {
        return;
      }
      this.beginProcess(ProcessState.PROCESS_LABEL.FILE_DOWNLOAD);
      this.errorMessages = [];
      this.clearApiMessages();
      try {
        const result = await MarkdownApiClient.downloadMarkdownFilesCsv(
          CONST.REST_PATH.MARKDOWN_FILES_CSV,
          MARKDOWN_FILES_CSV_FILE_NAME,
          saveTarget.handle
        );
        if (!Util.isEmpty(result.errorMessages)) {
          this.failProcess(result.errorMessages, result.errorCodes);
          return;
        }
        this.finishProcess(
          MARKDOWN_FILES_CSV_FILE_NAME + " をダウンロードしました。"
        );
      } catch (error) {
        this.failUnexpectedProcess(
          MarkdownApiClient.buildUnexpectedErrorMessage(error)
        );
      } finally {
        this.endProcessIfBusy();
      }
    },
    /**
     * 編集元PDFをEPUBへ変換し、生成されたEPUBをダウンロードする。
     *
     * HTML出力と同じく、画面の下書きモード選択をそのまま送る。
     *
     * @returns {Promise<void>} EPUB出力処理の完了Promise
     */
    async requestEpubPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      this.originalFile.delPagesText.message = "";
      const saveTarget = await this.requestSaveTarget(
        EPUB_PDF_FILE_NAME,
        FileResponseHandler.FILE_TYPES.EPUB
      );
      if (saveTarget.cancelled) {
        return;
      }
      const payload = PdfPayload.buildHtmlPdfPayload(
        originalFileData.fileObject,
        originalFileData.markdownDraftMode
      );
      await this.requestFileAndDownload(
        CONST.REST_PATH.EPUB_PDF,
        payload,
        EPUB_PDF_FILE_NAME,
        saveTarget.handle
      );
    },
    /**
     * EPUBカードで選択されたファイルを保持する。
     *
     * @param {File} file 選択またはドロップされたEPUB
     */
    handleEpubSelected(file) {
      this.epubDocument.fileObject = file;
      this.epubDocument.fileName = file.name;
    },
    /**
     * EPUBカードの選択状態を初期化する。
     */
    clearEpub() {
      this.epubDocument = PdfFormState.createEpubState();
    },
    /**
     * 選択したEPUBをPDFへ変換し、結果を別タブで開く。
     */
    requestPdfFromEpub() {
      if (Util.isEmpty(this.epubDocument.fileObject)) {
        this.errorMessages = ["EPUBが選択されておりません。"];
        this.showMessageModal();
        return;
      }
      this.requestPdfAndOpen(
        CONST.REST_PATH.PDF_FROM_EPUB,
        PdfPayload.buildPdfFromEpubPayload(this.epubDocument.fileObject)
      );
    },
    /**
     * Office文書カードで選択されたファイルを保持する。
     *
     * @param {File} file 選択またはドロップされたOffice文書
     */
    handleOfficeSelected(file) {
      this.officeDocument.fileObject = file;
      this.officeDocument.fileName = file.name;
    },
    /**
     * Office文書カードの選択状態を初期化する。
     */
    clearOffice() {
      this.officeDocument = PdfFormState.createOfficeState();
    },
    /**
     * 選択したOffice文書からMarkdownを起こし、Markdown編集欄へ反映する。
     *
     * PDFのMarkdown下書きと同じく、結果は編集欄へ入れて人間が直せる形で残す。
     *
     * @returns {Promise<void>} Markdown生成処理の完了Promise
     */
    requestOfficeMarkdown() {
      if (Util.isEmpty(this.officeDocument.fileObject)) {
        this.markdownMessage = "Office文書が選択されておりません。";
        return Promise.resolve();
      }
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.beginProcess(ProcessState.PROCESS_LABEL.OFFICE_MARKDOWN);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      return PdfApiClient.requestOfficeMarkdown(
        CONST.REST_PATH.MARKDOWN_DRAFT_OFFICE,
        PdfPayload.buildOfficePayload(this.officeDocument.fileObject)
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          const officeResponse = result.officeMarkdownResponse;
          this.markdownFileName = this.buildMarkdownFileNameFromPdf(
            officeResponse.fileName
          );
          this.markdownContent = officeResponse.markdown || "";
          this.clearMarkdownPreview();
          this.markdownMessage =
            officeResponse.fileName + " をMarkdown欄へ反映しました。";
          this.goToMarkdownMemo();
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            PdfApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * 選択したOffice文書をPDFへ変換し、結果を別タブで開く。
     */
    requestPdfFromOffice() {
      if (Util.isEmpty(this.officeDocument.fileObject)) {
        this.errorMessages = ["Office文書が選択されておりません。"];
        this.showMessageModal();
        return;
      }
      this.requestPdfAndOpen(
        CONST.REST_PATH.PDF_FROM_OFFICE,
        PdfPayload.buildOfficePayload(this.officeDocument.fileObject)
      );
    },
    /**
     * HTML PDFカードで選択されたHTMLを保持する。
     *
     * @param {File} file 選択またはドロップされたHTML
     */
    handleHtmlPdfSelected(file) {
      this.htmlPdf.fileObject = file;
      this.htmlPdf.fileName = file.name;
    },
    /**
     * HTML PDFカードの選択状態を初期化する。
     */
    clearHtmlPdf() {
      this.htmlPdf = PdfFormState.createHtmlPdfState();
    },
    /**
     * 選択したHTMLをPDFへ変換し、結果を別タブで開く。
     */
    requestPdfFromHtml() {
      if (Util.isEmpty(this.htmlPdf.fileObject)) {
        this.errorMessages = ["HTMLが選択されておりません。"];
        this.showMessageModal();
        return;
      }
      this.requestPdfAndOpen(
        CONST.REST_PATH.PDF_FROM_HTML,
        PdfPayload.buildPdfFromHtmlPayload(this.htmlPdf.fileObject)
      );
    },
    /**
     * 画像PDFカードで選択された画像を保持する。
     *
     * 追加選択できるよう、既存の選択へ後ろから足す。選択のたびに置き換えると、
     * フォルダをまたいで画像を集める操作ができなくなる。
     *
     * @param {File[]} files 選択またはドロップされた画像
     */
    handleImagesPdfSelected(files) {
      this.imagesPdf.files = this.imagesPdf.files.concat(files);
    },
    /**
     * 画像PDFカードの選択状態を初期化する。
     */
    clearImagesPdf() {
      this.imagesPdf = buildImagesPdfState();
    },
    /**
     * 選択した画像を1つのPDFへまとめ、結果を別タブで開く。
     */
    requestPdfFromImages() {
      if (this.imagesPdf.files.length === 0) {
        this.errorMessages = ["画像が選択されておりません。"];
        this.showMessageModal();
        return;
      }
      const payload = PdfPayload.buildPdfFromImagesPayload(
        this.imagesPdf.files,
        this.imagesPdf.pageSize
      );
      this.requestPdfAndOpen(CONST.REST_PATH.PDF_FROM_IMAGES, payload);
    },
    /**
     * Web取り込みカードで選択されたHTMLを保持する。
     *
     * @param {File} file 選択またはドロップされたHTML
     */
    handleWebHtmlSelected(file) {
      this.webMarkdown.fileObject = file;
      this.webMarkdown.fileName = file.name;
    },
    /**
     * Web取り込みカードの絞り込みセレクタを保持する。
     *
     * @param {string} selector 入力されたCSSセレクタ
     */
    updateWebSelector(selector) {
      this.webMarkdown.selector = selector;
    },
    /**
     * Web取り込みカードの取得先URLを保持する。
     *
     * @param {string} url 入力されたURL
     */
    updateWebUrl(url) {
      this.webMarkdown.url = url;
    },
    /**
     * Web取り込みカードの出力モードを保持する。
     *
     * @param {string} mode 選択された出力モード
     */
    updateWebMode(mode) {
      this.webMarkdown.mode = mode;
    },
    /**
     * Web取り込みカードの選択状態を初期化する。
     */
    clearWebMarkdown() {
      this.webMarkdown = PdfFormState.createWebMarkdownState();
    },
    /**
     * 選択したHTMLからMarkdown下書きを起こし、Markdown編集欄へ反映する。
     *
     * @returns {Promise<void>} 取り込み処理の完了Promise
     */
    requestWebMarkdownDraft() {
      if (Util.isEmpty(this.webMarkdown.fileObject)) {
        this.markdownMessage = "HTMLファイルが選択されておりません。";
        return Promise.resolve();
      }
      const sourceName = this.webMarkdown.fileName;
      return this.requestWebMarkdown(
        () =>
          MarkdownApiClient.requestHtmlMarkdownDraft(
            this.webMarkdown.fileObject,
            this.webMarkdown.selector,
            this.webMarkdown.mode
          ),
        sourceName
      );
    },
    /**
     * 入力したURLのWebページからMarkdown下書きを起こし、Markdown編集欄へ反映する。
     *
     * サーバー側の取得機能が無効なら503が返り、共通のエラー表示で「機能が無効」と分かる。
     * 画面側で有効・無効を推測して出し分けない。設定はサーバーが持つもので、画面が持つと二重管理になる。
     *
     * @returns {Promise<void>} 取り込み処理の完了Promise
     */
    requestWebMarkdownUrlDraft() {
      if (Util.isEmpty(this.webMarkdown.url)) {
        this.markdownMessage = "URLが入力されておりません。";
        return Promise.resolve();
      }
      const sourceName = this.webMarkdown.url;
      return this.requestWebMarkdown(
        () =>
          MarkdownApiClient.requestUrlMarkdownDraft(
            this.webMarkdown.url,
            this.webMarkdown.selector,
            this.webMarkdown.mode
          ),
        sourceName
      );
    },
    /**
     * Webページ取り込みAPIを実行し、成功時はMarkdown編集欄へ反映する。
     *
     * PDF・画像・Office文書からの下書きと同じく、結果は編集欄へ入れて人間が直せる形で残す。
     * 自動保存はしない。取り込んだ内容をそのまま保存すると、中身を確認する前に
     * 他者のページの複製が手元へ残ることになる。
     *
     * @param {Function} request 取り込みAPIを実行する関数
     * @param {string} sourceName 取得元の表示名。ファイル名またはURL
     * @returns {Promise<void>} 取り込み処理の完了Promise
     */
    requestWebMarkdown(request, sourceName) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.beginProcess(ProcessState.PROCESS_LABEL.WEB_MARKDOWN);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      return request()
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          this.markdownFileName = this.buildMarkdownFileNameFromWebSource(
            result.data.title,
            sourceName
          );
          this.markdownContent = result.data.markdown || "";
          this.clearMarkdownPreview();
          this.markdownMessage =
            sourceName + " の取り込み結果をMarkdown欄へ反映しました。";
          this.goToMarkdownMemo();
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            MarkdownApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * Webページ取り込み結果のMarkdown保存候補名を組み立てる。
     *
     * URLから取り込んだ場合、URL文字列はそのままではファイル名にできない。
     * ページタイトルを優先し、取れない場合だけ取得元の名前から作る。
     *
     * @param {string} title 取り込んだページのタイトル
     * @param {string} sourceName 取得元の表示名。ファイル名またはURL
     * @returns {string} Markdown保存候補名
     */
    buildMarkdownFileNameFromWebSource(title, sourceName) {
      if (!Util.isEmpty(title)) {
        return this.buildMarkdownFileNameFromPdf(
          this.toFileNameSafeText(title) + ".html"
        );
      }
      return this.buildMarkdownFileNameFromPdf(
        this.toFileNameSafeText(sourceName)
      );
    },
    /**
     * ファイル名に使えない文字を、サーバの保存時と同じ規則で置き換える。
     *
     * ページタイトルやURLには `|` `:` `/` が普通に含まれる。そのまま候補名にすると、
     * 保存時にサーバ側（PathUtils.sanitizeFileName）が置き換えるため、画面に出したファイル名と
     * 実際に保存される名前が食い違う。置き換えの規則をサーバと同じにして、見えている名前で保存されるようにする。
     *
     * @param {string} source ファイル名の元にする文字列
     * @returns {string} ファイル名に使える形へ直した文字列
     */
    toFileNameSafeText(source) {
      return source.replace(/[\\/:*?"<>|\u0000-\u001F]+/g, "_");
    },
    /**
     * 選択画像から文字起こしを実行し、結果をMarkdown編集欄へ反映する。
     *
     * @returns {Promise<void>} 文字起こし処理の完了Promise
     */
    requestImageDraft() {
      if (Util.isEmpty(this.imageDraft.fileObject)) {
        this.markdownMessage = "画像が選択されておりません。";
        return Promise.resolve();
      }
      return this.requestImageMarkdownDraft(
        ImagePayload.buildImageDraftPayload(this.imageDraft.fileObject)
      );
    },
    /**
     * 画像Markdown下書きAPIを実行し、成功時は既存Markdown編集欄へ反映する。
     *
     * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
     * @returns {Promise<void>} 文字起こし処理の完了Promise
     */
    requestImageMarkdownDraft(payload) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.beginProcess(ProcessState.PROCESS_LABEL.IMAGE_DRAFT);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      return ImageApiClient.requestImageMarkdownDraft(
        CONST.REST_PATH.MARKDOWN_DRAFT_IMAGE,
        payload
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          const draftResponse = result.imageDraftResponse;
          this.markdownFileName = this.buildMarkdownFileNameFromPdf(
            draftResponse.fileName
          );
          this.markdownContent = draftResponse.markdown || "";
          this.clearMarkdownPreview();
          this.markdownMessage =
            draftResponse.fileName + " の文字起こしをMarkdown欄へ反映しました。";
          this.goToMarkdownMemo();
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            ImageApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * 編集元PDFを分割し、生成されたZIPをダウンロードする。
     *
     * 分割範囲が入力されていれば範囲ごとに、空欄なら従来どおり1ページずつ分割する。
     * 範囲の形式が不正な場合は、保存先の選択もAPI呼び出しも行わない。
     *
     * 対応ブラウザ（Chrome / Edge）では先に保存先を選ばせる。File System Access APIは
     * 利用者操作の直後しか使えないため、API呼び出しの前に呼ぶ必要がある。
     * 保存先を決めてから処理を始めるので、キャンセル時はサーバー処理も発生しない。
     *
     * @returns {Promise<void>} 分割処理の完了Promise
     */
    async requestSplitPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      const splitRanges = this.parseSplitRanges(
        originalFileData.splitRangesText.text
      );
      if (!Util.isEmpty(this.originalFile.splitRangesText.message)) {
        return;
      }
      // 保存ダイアログ表示中はブラウザ側がモーダルで操作を止めるため、isProcessingは立てない。
      // ここで立てるとキャンセル時に解除漏れの経路が増える。
      const saveTarget = await this.requestSaveTarget(
        SPLIT_PDF_FILE_NAME,
        FileResponseHandler.FILE_TYPES.ZIP
      );
      if (saveTarget.cancelled) {
        return;
      }
      const payload = PdfPayload.buildSplitPayload(
        originalFileData.fileObject,
        splitRanges
      );
      await this.requestFileAndDownload(
        CONST.REST_PATH.SPLIT_PDF,
        payload,
        SPLIT_PDF_FILE_NAME,
        saveTarget.handle
      );
    },
    /**
     * 編集元PDFのページを画像化し、生成されたZIPをダウンロードする。
     *
     * ページ指定のチェックが外れている場合はページ番号を送らず、BE側の「全ページ画像化」に任せる。
     *
     * 分割と同じく、対応ブラウザでは先に保存先を選ばせる。File System Access APIは
     * 利用者操作の直後しか使えないため、API呼び出しの前に呼ぶ必要がある。
     *
     * @returns {Promise<void>} 画像化処理の完了Promise
     */
    async requestImagesPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      this.originalFile.delPagesText.message = "";
      const imagePages = originalFileData.delPagesChecked.checked
        ? this.parseDeletePages(originalFileData.delPagesText.text)
        : [];
      if (!Util.isEmpty(this.originalFile.delPagesText.message)) {
        return;
      }
      const saveTarget = await this.requestSaveTarget(
        IMAGES_PDF_FILE_NAME,
        FileResponseHandler.FILE_TYPES.ZIP
      );
      if (saveTarget.cancelled) {
        return;
      }
      const payload = PdfPayload.buildImagesPayload(
        originalFileData.fileObject,
        originalFileData.imageFormat,
        originalFileData.imageDpi,
        imagePages
      );
      await this.requestFileAndDownload(
        CONST.REST_PATH.IMAGES_PDF,
        payload,
        IMAGES_PDF_FILE_NAME,
        saveTarget.handle
      );
    },
    /**
     * 保存先の選択を要求し、失敗時はエラーモーダルへ回す。
     *
     * 未対応ブラウザとキャンセルを区別する。未対応の場合はhandleがnullのまま進み、
     * 従来どおりブラウザのダウンロード機能で保存される。
     *
     * @param {string} suggestedName 既定のファイル名
     * @param {{description: string, accept: Object}[]} types 拡張子フィルタ
     * @returns {Promise<{cancelled: boolean, handle: FileSystemFileHandle|null}>} 保存先の選択結果
     */
    async requestSaveTarget(suggestedName, types) {
      try {
        const saveTarget = await FileResponseHandler.requestSaveTarget(
          suggestedName,
          types
        );
        return { cancelled: saveTarget.cancelled, handle: saveTarget.handle };
      } catch (error) {
        this.errorMessages = [PdfApiClient.buildUnexpectedErrorMessage(error)];
        this.showMessageModal();
        return { cancelled: true, handle: null };
      }
    },
    /**
     * 編集元PDFに差し込みPDFを結合・差し替えし、生成されたPDFを別タブで開く。
     */
    requestInsertPdf() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.originalFile.delPagesText.message =
          "ファイル選択されておりません。";
        return;
      }
      let deletePages = null;
      if (originalFileData.delPagesChecked.checked) {
        deletePages = this.parseDeletePages(originalFileData.delPagesText.text);
        if (!Util.isEmpty(this.originalFile.delPagesText.message)) {
          return;
        }
      }

      if (!this.validateInsertFilePages()) {
        return;
      }
      const payload = PdfPayload.buildInsertPayload(
        originalFileData.fileObject,
        deletePages,
        this.insertFiles
      );
      this.requestPdfAndOpen(CONST.REST_PATH.INSERT_PDF, payload);
    },
    /**
     * 差し込みPDF行に選択されたPDFを表示順に結合し、生成されたPDFを別タブで開く。
     */
    requestMergePdf() {
      const mergeFiles = this.insertFiles.filter(
        (insertData) => insertData.fileObject
      );
      if (mergeFiles.length < 2) {
        this.errorMessages = ["結合するPDFを2件以上選択してください。"];
        this.showMessageModal();
        return;
      }
      const payload = PdfPayload.buildMergePayload(mergeFiles);
      this.requestPdfAndOpen(CONST.REST_PATH.MERGE_PDF, payload);
    },
    /**
     * Markdownファイル名入力を確認する。
     *
     * @returns {boolean} 入力済みの場合はtrue
     */
    validateMarkdownFileName() {
      const normalizedFileName = this.markdownFileName.trim();
      if (!Util.isEmpty(normalizedFileName)) {
        this.markdownFileName = normalizedFileName;
        return true;
      }
      this.errorMessages = ["Markdownファイル名を入力してください。"];
      this.showMessageModal();
      return false;
    },
    /**
     * 画面表示用のMarkdownファイルメタ情報を組み立てる。
     *
     * @param {Object} fileInfo Markdownファイル情報
     * @returns {string} 画面表示用メタ情報
     */
    formatMarkdownFileInfo(fileInfo) {
      const metadataItems = [
        fileInfo.lineCount + "行",
        fileInfo.byteSize.toLocaleString() + " bytes",
      ];
      if (!Util.isEmpty(fileInfo.lastModifiedTime)) {
        metadataItems.push("更新 " + fileInfo.lastModifiedTime);
      }
      return metadataItems.join(" / ");
    },
    /**
     * Markdown一覧で選択したファイルの本文を読み込む。
     *
     * @param {string} fileName 選択したMarkdownファイル名
     * @returns {Promise<void>} 本文取得処理の完了Promise
     */
    selectMarkdownFile(fileName) {
      this.markdownFileName = fileName;
      this.clearMarkdownPreview();
      return this.requestLoadMarkdownFile();
    },
    /**
     * Markdownプレビューを未表示状態に戻す。
     */
    clearMarkdownPreview() {
      this.markdownPreviewHtml = "";
    },
    /**
     * ホームで受け取ったファイルの種別を判定し、操作候補を出す。
     *
     * ここでは種別の判定だけを行い、サイズ・拡張子の検証は移動先と同じ既存経路（applyPdfFileなど）へ任せる。
     * ホームだけ検証が緩い、あるいは二重に出る状態を作らないため。
     *
     * @param {File} fileObject 受け取ったファイル
     */
    handleQuickFileSelected(fileObject) {
      this.quickStart = QuickStartActions.buildQuickStartState(fileObject);
    },
    /**
     * ホームで受け取ったファイルを取り消す。
     */
    clearQuickStart() {
      this.quickStart = QuickStartActions.createQuickStartState();
    },
    /**
     * ホームで選ばれた操作候補に応じて、担当タブへファイルを渡して移動する。
     *
     * 移動するだけで実行はしない。変換モードの選択や費用の発生する操作が、
     * ホームでのワンクリックで走ってしまうのを避けるため。
     *
     * @param {string} actionId 操作候補の識別子
     * @returns {Promise<void>} 反映処理の完了Promise
     */
    async handleQuickAction(actionId) {
      const fileObject = this.quickStart.fileObject;
      if (Util.isEmpty(fileObject)) {
        return;
      }
      const quickActions = QuickStartActions.QUICK_ACTION;
      if (actionId === quickActions.PDF_EDIT) {
        this.applyQuickStartTab(this.applyPdfFile(fileObject, -1), "edit");
        return;
      }
      if (actionId === quickActions.PDF_MERGE) {
        this.addQuickStartPdfToMerge(fileObject);
        return;
      }
      if (actionId === quickActions.IMAGE_OCR) {
        this.handleImageSelected(fileObject);
        this.applyQuickStartTab(true, "ocr");
        return;
      }
      if (actionId === quickActions.IMAGE_TO_PDF) {
        this.handleImagesPdfSelected([fileObject]);
        this.applyQuickStartTab(true, "convert");
        return;
      }
      if (actionId === quickActions.MARKDOWN_OPEN) {
        await this.openMarkdownFromLocalFile(fileObject);
        return;
      }
      if (actionId === quickActions.OFFICE_CONVERT) {
        this.handleOfficeSelected(fileObject);
        this.applyQuickStartTab(true, "convert");
        return;
      }
      if (actionId === quickActions.HTML_TO_PDF) {
        this.handleHtmlPdfSelected(fileObject);
        this.applyQuickStartTab(true, "convert");
        return;
      }
      if (actionId === quickActions.HTML_TO_MARKDOWN) {
        this.handleWebHtmlSelected(fileObject);
        this.applyQuickStartTab(true, "ocr");
        return;
      }
      if (actionId === quickActions.EPUB_TO_PDF) {
        this.handleEpubSelected(fileObject);
        this.applyQuickStartTab(true, "convert");
      }
    },
    /**
     * 受け渡しに成功した場合だけタブを移動し、ホームの受け取り欄を空にする。
     *
     * 失敗（サイズ超過など）でタブを移動すると、移動先にファイルが無い理由が分からなくなる。
     * ホームに留めて、そこへ出ているエラーを読める状態にする。
     *
     * @param {boolean} accepted 移動先がファイルを受け付けたか
     * @param {string} tabName 移動先タブ名
     */
    applyQuickStartTab(accepted, tabName) {
      if (!accepted) {
        return;
      }
      this.clearQuickStart();
      this.activeTab = tabName;
    },
    /**
     * ホームで受け取ったPDFを、空いている差し込み行へ追加して結合パネルを開く。
     *
     * 空き行が無い場合は行を追加してから入れる。10件上限に達した場合はaddInsertFileRowが
     * その旨を出して行を増やさないため、ここでは追加できなかったこととして扱う。
     *
     * @param {File} fileObject 受け取ったPDF
     */
    addQuickStartPdfToMerge(fileObject) {
      let emptyRow = this.insertFiles.find((insertData) =>
        Util.isEmpty(insertData.fileObject)
      );
      if (Util.isEmpty(emptyRow)) {
        const beforeCount = this.insertFiles.length;
        this.addInsertFileRow();
        if (this.insertFiles.length === beforeCount) {
          return;
        }
        emptyRow = this.insertFiles[this.insertFiles.length - 1];
      }
      if (!this.applyPdfFile(fileObject, emptyRow.fileNo)) {
        return;
      }
      this.clearQuickStart();
      this.activeTab = "edit";
      this.insertMergePanelOpen = true;
    },
    /**
     * ホームで受け取ったMarkdownファイルを読み込み、Markdownメモの編集欄へ入れる。
     *
     * 保存済みMarkdownの読込と違いサーバーを経由しないため、上限判定だけはここで行う。
     * 大きなファイルをそのまま読むと、ブラウザ側が固まって理由が分からなくなる。
     *
     * @param {File} fileObject 受け取ったMarkdownファイル
     * @returns {Promise<void>} 読み込み処理の完了Promise
     */
    async openMarkdownFromLocalFile(fileObject) {
      if (!FileSizeValidator.isWithinUploadSizeLimit(fileObject)) {
        this.blockProcess(
          FileSizeValidator.buildUploadSizeLimitMessage(fileObject),
          "小さいファイルを指定してください。"
        );
        return;
      }
      try {
        const content = await fileObject.text();
        this.markdownFileName = fileObject.name;
        this.markdownContent = content;
        this.clearMarkdownPreview();
        this.markdownMessage = fileObject.name + " を読み込みました。";
        this.clearQuickStart();
        this.activeTab = "memo";
      } catch (error) {
        this.failUnexpectedProcess(
          "「" + fileObject.name + "」を読み込めませんでした。"
        );
      }
    },
    /**
     * ホームの「よく使う操作」から、目的の機能タブへ移動する。
     *
     * "merge"はPDF編集タブ内の折りたたみ済みPDF差し込み・結合パネルを開いた状態で移動する特別値。
     * 実際のファイル選択・実行は移動先のタブに残す。
     *
     * @param {string} tabName 移動先タブ名、または"merge"
     */
    handleHomeNavigate(tabName) {
      if (tabName === "merge") {
        this.activeTab = "edit";
        this.insertMergePanelOpen = true;
        return;
      }
      this.activeTab = tabName;
    },
    /**
     * ホームの「最近保存したMarkdown」から選んだファイルを開き、Markdownメモタブへ移動する。
     *
     * @param {string} fileName 開くMarkdownファイル名
     * @returns {Promise<void>} 本文取得処理の完了Promise
     */
    openRecentMarkdownFile(fileName) {
      this.activeTab = "memo";
      return this.selectMarkdownFile(fileName);
    },
    /**
     * Markdownメモタブへ切り替える。
     *
     * PDF/画像/Office文書からのMarkdown生成は、生成した時点でmarkdownContentへ反映済み。
     * 結果をその場で確認・保存できるよう、生成直後はメモタブへ自動で移動する。
     */
    goToMarkdownMemo() {
      this.activeTab = "memo";
    },
    /**
     * Markdown編集欄の下書きをブラウザ内保存へ書き込むタイマーを積み直す。
     *
     * 入力の都度書き込むと編集のたびにストレージI/Oが走るため、入力が3秒止まってから書き込む。
     */
    scheduleMarkdownDraftSave() {
      if (this.markdownDraftSaveTimerId !== null) {
        window.clearTimeout(this.markdownDraftSaveTimerId);
      }
      this.markdownDraftSaveTimerId = window.setTimeout(() => {
        UiPreferenceState.saveMarkdownDraft(
          this.markdownFileName,
          this.markdownContent
        );
        this.markdownDraftSaveTimerId = null;
      }, MARKDOWN_DRAFT_SAVE_DELAY_MS);
    },
    /**
     * ブラウザ内に退避していた未保存Markdownの下書きを復元する。
     *
     * 画面を開いた直後、編集欄が空の場合だけ復元する。既存の入力内容を上書きしないようにするため。
     */
    restoreMarkdownDraftFromLocalStorage() {
      if (!Util.isEmpty(this.markdownContent)) {
        return;
      }
      const draft = UiPreferenceState.loadMarkdownDraft();
      if (Util.isEmpty(draft)) {
        return;
      }
      this.markdownFileName = draft.fileName || this.markdownFileName;
      this.markdownContent = draft.content;
      this.markdownMessage = "前回の未保存Markdownを復元しました。";
    },
    /**
     * Markdownメモタブでのキーボードショートカットを処理する。
     *
     * Ctrl+S（Macはcmd+S）は保存、Ctrl+Enterはプレビューを実行する。
     * ブラウザ既定の保存ダイアログは、Markdownメモを保存する操作として代わりに奪う。
     *
     * @param {KeyboardEvent} event キーボードイベント
     */
    handleMarkdownShortcut(event) {
      if (this.activeTab !== "memo") {
        return;
      }
      if (!event.ctrlKey && !event.metaKey) {
        return;
      }
      if (event.key === "s" || event.key === "S") {
        event.preventDefault();
        this.requestSaveMarkdown();
        return;
      }
      if (event.key === "Enter") {
        event.preventDefault();
        this.requestPreviewMarkdownContent();
      }
    },
    /**
     * Markdown編集欄の内容をクリップボードにコピーする。
     *
     * 手動で範囲選択しなくても、文字起こし結果をそのまま他のツールへ貼り付けられるようにする。
     *
     * @returns {Promise<void>} コピー処理の完了Promise
     */
    copyMarkdownContent() {
      if (Util.isEmpty(this.markdownContent)) {
        this.markdownMessage = "コピーする内容がありません。";
        return Promise.resolve();
      }
      if (Util.isEmpty(navigator.clipboard)) {
        this.errorMessages = ["この環境ではクリップボードコピーを利用できません。"];
        this.showMessageModal();
        return Promise.resolve();
      }
      return navigator.clipboard
        .writeText(this.markdownContent)
        .then(() => {
          this.markdownMessage = "Markdownをクリップボードにコピーしました。";
        })
        .catch(() => {
          this.errorMessages = ["クリップボードへのコピーに失敗しました。"];
          this.showMessageModal();
        });
    },
    /**
     * 保存/更新後のMarkdownファイル情報を一覧へ反映する。
     *
     * @param {Object} fileInfo Markdownファイル情報
     */
    upsertMarkdownFileInfo(fileInfo) {
      const files = this.markdownFiles.filter(
        (existingFile) => existingFile.fileName !== fileInfo.fileName
      );
      files.push(fileInfo);
      this.isMarkdownFileListLoaded = true;
      this.markdownFiles = files.sort((leftFile, rightFile) =>
        leftFile.fileName
          .toLowerCase()
          .localeCompare(rightFile.fileName.toLowerCase())
      );
    },
    /**
     * PDFファイル名からMarkdown保存候補名を生成する。
     *
     * @param {string} fileName PDFファイル名
     * @returns {string} Markdown保存候補名
     */
    buildMarkdownFileNameFromPdf(fileName) {
      const currentMarkdownFileName = this.markdownFileName.trim();
      const fallbackFileName = Util.isEmpty(currentMarkdownFileName)
        ? "design-note.md"
        : currentMarkdownFileName;
      if (Util.isEmpty(fileName)) {
        return fallbackFileName;
      }
      const baseName = fileName.replace(/\.[^/.]+$/, "");
      if (Util.isEmpty(baseName)) {
        return fallbackFileName;
      }
      return baseName + ".md";
    },
    /**
     * 保存済みMarkdown一覧を取得する。
     *
     * @returns {Promise<void>} 一覧取得処理の完了Promise
     */
    requestListMarkdownFiles() {
      return this.requestMarkdownApi(
        () => MarkdownApiClient.listMarkdownFiles(),
        (files) => {
          this.markdownFiles = files;
          this.isMarkdownFileListLoaded = true;
          this.markdownMessage = "Markdown一覧を取得しました。";
        }
      );
    },
    /**
     * Markdown本文を新規保存する。
     *
     * @returns {Promise<void>} 保存処理の完了Promise
     */
    requestSaveMarkdown() {
      if (!this.validateMarkdownFileName()) {
        return Promise.resolve();
      }
      return this.requestMarkdownApi(
        () =>
          MarkdownApiClient.saveMarkdown(
            this.markdownFileName,
            this.markdownContent
          ),
        (fileInfo) => {
          this.markdownFileName = fileInfo.fileName;
          this.upsertMarkdownFileInfo(fileInfo);
          this.markdownMessage = fileInfo.fileName + " を保存しました。";
        }
      );
    },
    /**
     * 保存済みMarkdown本文を読み込む。
     *
     * @returns {Promise<void>} 本文取得処理の完了Promise
     */
    requestLoadMarkdownFile() {
      if (!this.validateMarkdownFileName()) {
        return Promise.resolve();
      }
      return this.requestMarkdownApi(
        () => MarkdownApiClient.getMarkdownFile(this.markdownFileName),
        (document) => {
          this.markdownFileName = document.fileName;
          this.markdownContent = document.content;
          this.clearMarkdownPreview();
          this.markdownMessage = document.fileName + " を読み込みました。";
        }
      );
    },
    /**
     * 入力中Markdown本文のHTMLプレビューを取得する。
     *
     * @returns {Promise<void>} プレビュー取得処理の完了Promise
     */
    requestPreviewMarkdownContent() {
      return this.requestMarkdownApi(
        () => MarkdownApiClient.previewMarkdownContent(this.markdownContent),
        (preview) => {
          this.markdownPreviewHtml = preview.html;
          this.markdownMessage = "入力中Markdownをプレビューしました。";
        }
      );
    },
    /**
     * 入力中Markdown本文をAIで整形する。
     *
     * @returns {Promise<void>} AI整形処理の完了Promise
     */
    requestMarkdownAiRefine() {
      return this.requestMarkdownAiTransform("REFINE");
    },
    /**
     * 入力中Markdown本文をAIで要約する。
     *
     * @returns {Promise<void>} AI要約処理の完了Promise
     */
    requestMarkdownAiSummarize() {
      return this.requestMarkdownAiTransform("SUMMARIZE");
    },
    /**
     * Markdown本文AI整形・要約APIを実行する。
     *
     * 結果は既存のMarkdown編集欄を上書きせず、確認用の別領域へ表示する。原文の意味を変えていないかを
     * 機械的に保証できないため、採用するかどうかは利用者が applyAiTransformResult で選ぶ。
     *
     * @param {string} task 変換タスク。SUMMARIZEまたはREFINE
     * @returns {Promise<void>} 変換処理の完了Promise
     */
    requestMarkdownAiTransform(task) {
      if (Util.isEmpty(this.markdownContent)) {
        this.markdownMessage = "変換対象のMarkdown本文がありません。";
        return Promise.resolve();
      }
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.beginProcess(ProcessState.PROCESS_LABEL.AI_TRANSFORM);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      this.aiTransformResult = null;
      return MarkdownApiClient.transformMarkdownAi(this.markdownContent, task)
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          this.aiTransformResult = result.data;
          this.markdownMessage =
            "AI変換結果を確認欄へ表示しました。内容を確認してから採用してください。";
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            MarkdownApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * AI変換結果をMarkdown編集欄へ採用する。
     *
     * 自動反映はせず、利用者がこの操作を行った場合だけ既存の編集欄を上書きする。
     */
    applyAiTransformResult() {
      if (Util.isEmpty(this.aiTransformResult)) {
        return;
      }
      this.markdownContent = this.aiTransformResult.markdown || "";
      this.clearMarkdownPreview();
      this.markdownMessage = "AI変換結果をMarkdown編集欄へ反映しました。";
      this.aiTransformResult = null;
    },
    /**
     * AI変換結果の確認欄を破棄する。
     */
    clearAiTransformResult() {
      this.aiTransformResult = null;
    },
    /**
     * 入力中Markdown本文からPDFを生成し、ダウンロードする。
     *
     * 保存は伴わないため、保存済みかどうかに関わらず編集中の内容をそのまま出力する。
     * レスポンスはJSONではなくPDFバイナリなので、共通のrequestMarkdownApiではなくここで直接扱う。
     *
     * @returns {Promise<void>} PDF出力処理の完了Promise
     */
    requestMarkdownPdf() {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.beginProcess(ProcessState.PROCESS_LABEL.MARKDOWN_PDF);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      return MarkdownApiClient.requestMarkdownPdf({
        fileName: this.markdownFileName,
        content: this.markdownContent,
      })
        .then(async (result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          // 保存名はBEのContent-Dispositionに合わせる。拡張子の正規化はBE側に寄せている。
          await FileResponseHandler.downloadBlob(
            result.fileBlob,
            result.headers,
            "document.pdf"
          );
          this.markdownMessage = "MarkdownをPDFで出力しました。";
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            MarkdownApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * 保存済みMarkdown本文を更新する。
     *
     * @returns {Promise<void>} 更新処理の完了Promise
     */
    requestUpdateMarkdownFile() {
      if (!this.validateMarkdownFileName()) {
        return Promise.resolve();
      }
      return this.requestMarkdownApi(
        () =>
          MarkdownApiClient.updateMarkdownFile(
            this.markdownFileName,
            this.markdownContent
          ),
        (fileInfo) => {
          this.markdownFileName = fileInfo.fileName;
          this.upsertMarkdownFileInfo(fileInfo);
          this.markdownMessage = fileInfo.fileName + " を更新しました。";
        }
      );
    },
    /**
     * 保存済みMarkdownファイルを削除する。
     *
     * @returns {Promise<void>} 削除処理の完了Promise
     */
    requestDeleteMarkdownFile() {
      if (!this.validateMarkdownFileName()) {
        return Promise.resolve();
      }
      return this.requestMarkdownApi(
        () => MarkdownApiClient.deleteMarkdownFile(this.markdownFileName),
        (deleteResult) => {
          this.markdownFiles = this.markdownFiles.filter(
            (fileInfo) => fileInfo.fileName !== deleteResult.fileName
          );
          this.markdownContent = "";
          this.clearMarkdownPreview();
          this.markdownMessage = deleteResult.fileName + " を削除しました。";
        }
      );
    },
    /**
     * Markdown操作APIを実行し、成功時は指定された画面反映処理を行う。
     *
     * @param {Function} request API呼び出し関数
     * @param {Function} onSuccess 成功時の画面反映処理
     * @returns {Promise<void>} Markdown API処理の完了Promise
     */
    requestMarkdownApi(request, onSuccess) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.beginProcess(ProcessState.PROCESS_LABEL.MARKDOWN_FILE);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      return request()
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          onSuccess(result.data);
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            MarkdownApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * 差し込みPDF行のページ番号入力を検証する。
     *
     * ファイルが選択され、かつページ指定チェックがONの行だけを検証対象にする。
     *
     * @returns {boolean} すべての対象行が正しい場合はtrue
     */
    validateInsertFilePages() {
      for (let index = 0; index < this.insertFiles.length; index++) {
        const insertData = this.insertFiles[index];
        if (
          Util.isEmpty(insertData.fileObject) ||
          !insertData.insertPageChecked.checked
        ) {
          continue;
        }
        const pageText = insertData.insertPageText.text;
        if (PageNumberValidator.isValidInsertPageText(pageText)) {
          continue;
        }
        this.insertFiles[index].insertPageText.message =
          "ページ番号が数値ではありません。";
        return false;
      }
      return true;
    },
    /**
     * PDF操作APIを実行し、エラー時はメッセージモーダルを表示する。
     *
     * @param {string} url PDF操作APIのURL
     * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
     * @returns {Promise<void>} PDF表示処理の完了Promise
     */
    requestPdfAndOpen(url, payload) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.pendingPasswordRetry = () => this.requestPdfAndOpen(url, payload);
      this.beginProcess(ProcessState.PROCESS_LABEL.PDF_EDIT);
      this.errorMessages = [];
      this.clearApiMessages();
      return PdfApiClient.requestPdfAndOpen(
        url,
        PdfPayload.withPassword(payload, this.pdfPassword)
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.finishProcess("PDFを作成し、別タブで開きました。");
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            PdfApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * PDF操作APIを実行し、成功時はファイルをダウンロードする。
     *
     * @param {string} url PDF操作APIのURL
     * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
     * @param {string} defaultFileName Content-Dispositionが無い場合のファイル名
     * @param {FileSystemFileHandle} [saveTarget] 利用者が選んだ保存先
     * @returns {Promise<void>} ダウンロード処理の完了Promise
     */
    requestFileAndDownload(url, payload, defaultFileName, saveTarget) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.pendingPasswordRetry = () =>
        this.requestFileAndDownload(url, payload, defaultFileName, saveTarget);
      this.beginProcess(ProcessState.PROCESS_LABEL.FILE_DOWNLOAD);
      this.errorMessages = [];
      this.clearApiMessages();
      return PdfApiClient.requestFileAndDownload(
        url,
        PdfPayload.withPassword(payload, this.pdfPassword),
        defaultFileName,
        saveTarget
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.finishProcess(defaultFileName + " をダウンロードしました。");
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            PdfApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * PDFメタデータAPIを実行し、成功時は画面状態へ反映する。
     *
     * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
     * @returns {Promise<void>} PDFメタデータ取得処理の完了Promise
     */
    requestPdfMetadata(payload) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.pendingPasswordRetry = () => this.requestPdfMetadata(payload);
      this.beginProcess(ProcessState.PROCESS_LABEL.METADATA);
      this.errorMessages = [];
      this.clearApiMessages();
      return PdfApiClient.requestPdfMetadata(
        CONST.REST_PATH.METADATA_PDF,
        PdfPayload.withPassword(payload, this.pdfPassword)
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          this.pdfMetadata = PdfFormState.createLoadedPdfMetadataState(
            result.metadata
          );
          this.finishProcess("PDFの基本情報を読み取りました。");
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            PdfApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * PDFテキスト抽出APIを実行し、成功時はMarkdown欄へ反映する。
     *
     * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
     * @returns {Promise<void>} PDFテキスト抽出処理の完了Promise
     */
    requestPdfText(payload) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.pendingPasswordRetry = () => this.requestPdfText(payload);
      this.beginProcess(ProcessState.PROCESS_LABEL.TEXT);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      return PdfApiClient.requestPdfText(
        CONST.REST_PATH.TEXT_PDF,
        PdfPayload.withPassword(payload, this.pdfPassword)
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          const textResponse = result.textResponse;
          this.markdownFileName = this.buildMarkdownFileNameFromPdf(
            textResponse.fileName
          );
          this.markdownContent = textResponse.text || "";
          this.clearMarkdownPreview();
          this.markdownMessage =
            textResponse.fileName +
            " の抽出テキストをMarkdown欄へ反映しました。";
          this.goToMarkdownMemo();
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            PdfApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * 選択中のPDFをブラウザ内で描画し、ページ選択用サムネイルへ反映する。
     *
     * 以前はサーバーへPDF全体をアップロードして画像化していたため、利用者がボタンを押したときだけ
     * 実行していた。pdf.jsでローカル描画に変えてアップロードが不要になったので、ファイル選択直後にも
     * 自動で実行する。PDFの中身はブラウザの外へ出ない。
     *
     * @param {{promptPassword: boolean}} [options] パスワード保護PDFの扱い。
     *     promptPasswordがfalseの場合はパスワード入力欄を出さず、サムネイル欄へ案内だけ出す
     * @returns {Promise<void>} サムネイル描画の完了Promise
     */
    renderPdfThumbnails(options) {
      const promptPassword = options?.promptPassword !== false;
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.pdfThumbnails.message = "ファイル選択されておりません。";
        return Promise.resolve();
      }
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.pendingPasswordRetry = () => this.renderPdfThumbnails();
      this.beginProcess(ProcessState.PROCESS_LABEL.THUMBNAILS);
      this.errorMessages = [];
      this.clearApiMessages();
      this.pdfThumbnails.message = "";
      return PdfThumbnailRenderer.renderThumbnails(
        originalFileData.fileObject,
        this.pdfPassword
      )
        .then((result) => {
          this.pdfThumbnails.pages = result.pages;
          this.pdfThumbnails.selectedPageNumbers = [];
          this.pdfThumbnails.message =
            result.pageCount +
            "ページのサムネイルを表示しています。ページを選ぶとページ指定へ反映します。";
        })
        .catch((error) => {
          this.failThumbnailRendering(error, promptPassword);
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
    /**
     * サムネイル描画の失敗を、既存のエラー振り分けへ渡す。
     *
     * ファイル選択をきっかけにした自動描画では、パスワード入力欄を割り込ませない。利用者はPDFを
     * 選んだだけで、まだ何をするか決めていない。代わりにサムネイル欄へ案内を出し、
     * 「サムネイル表示」を押したときに入力欄を出す。
     *
     * @param {Error} error 描画で発生したエラー
     * @param {boolean} promptPassword パスワード入力欄を出してよい場合はtrue
     */
    failThumbnailRendering(error, promptPassword) {
      const errorCode = error?.errorCode;
      if (Util.isEmpty(errorCode)) {
        // pdf.js本体を読み込めなかった場合など、利用者側で打つ手が無い失敗。
        this.failUnexpectedProcess(
          ApiErrorUtils.buildUnexpectedErrorMessage(error)
        );
        return;
      }
      if (!promptPassword && ApiErrorUtils.isPasswordError([errorCode])) {
        this.resetProcess();
        this.pdfThumbnails.message =
          "パスワードで保護されたPDFです。「サムネイル表示」を押すとパスワードを入力できます。";
        return;
      }
      this.failProcess([error.message], [errorCode]);
    },
    /**
     * ページ単位Markdown下書きAPIを実行し、成功時は既存Markdown編集欄へ反映する。
     *
     * @param {{key: string, value: unknown}[]} payload multipart formとして送信する値
     * @returns {Promise<void>} Markdown下書き生成処理の完了Promise
     */
    requestPdfMarkdownDraft(payload) {
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.pendingPasswordRetry = () => this.requestPdfMarkdownDraft(payload);
      this.beginProcess(ProcessState.PROCESS_LABEL.MARKDOWN_DRAFT);
      this.errorMessages = [];
      this.clearApiMessages();
      this.markdownMessage = "";
      return PdfApiClient.requestPdfMarkdownDraft(
        CONST.REST_PATH.MARKDOWN_DRAFT_PDF,
        PdfPayload.withPassword(payload, this.pdfPassword)
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          const draftResponse = result.markdownDraftResponse;
          this.markdownFileName = this.buildMarkdownFileNameFromPdf(
            draftResponse.fileName
          );
          this.markdownContent = draftResponse.markdown || "";
          this.clearMarkdownPreview();
          this.markdownMessage =
            draftResponse.fileName +
            " のページ単位Markdown下書きを反映しました。";
          this.goToMarkdownMemo();
        })
        .catch((error) => {
          this.failUnexpectedProcess(
            PdfApiClient.buildUnexpectedErrorMessage(error)
          );
        })
        .finally(() => {
          this.endProcessIfBusy();
        });
    },
  },
};

export default pdfApp;
