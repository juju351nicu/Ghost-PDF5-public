import Util from "../util.js";
import CONST from "../const.js";
import Modal from "../components/modal.js";
import ApiMessageList from "../components/api-message-list.js";
import TheHeader from "../components/theheader.js";
import TheFooter from "../components/thefooter.js";
import OriginalPdfForm from "../components/original-pdf-form.js";
import InsertPdfRow from "../components/insert-pdf-row.js";
import ImageOcrForm from "../components/image-ocr-form.js";
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
import PageNumberValidator from "../validation/page-number-validator.js";
import FileSizeValidator from "../validation/file-size-validator.js";
import FileTypeValidator from "../validation/file-type-validator.js";
import ApiErrorUtils from "../api/api-error-utils.js";

const draggable = window["vuedraggable"];
const SPLIT_PDF_FILE_NAME = "split.zip";
const IMAGES_PDF_FILE_NAME = "images.zip";
const HTML_PDF_FILE_NAME = "document.html";
const EPUB_PDF_FILE_NAME = "document.epub";
const MARKDOWN_FILES_CSV_FILE_NAME = "markdown-files.csv";

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
    "original-pdf-form": OriginalPdfForm,
    "insert-pdf-row": InsertPdfRow,
    "image-ocr-form": ImageOcrForm,
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
      activeTab: "edit",
      originalFile: PdfFormState.createOriginalFileState(),
      pdfMetadata: PdfFormState.createPdfMetadataState(),
      pdfThumbnails: PdfFormState.createThumbnailState(),
      insertFiles: PdfFormState.createInitialInsertFiles(),
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
      imagesPdf: PdfFormState.createImagesPdfState(),
      htmlPdf: PdfFormState.createHtmlPdfState(),
      officeDocument: PdfFormState.createOfficeState(),
      epubDocument: PdfFormState.createEpubState(),
      markdownFileName: "design-note.md",
      markdownContent: "",
      markdownFiles: [],
      isMarkdownFileListLoaded: false,
      markdownPreviewHtml: "",
      markdownMessage: "",
    };
  },
  mounted() {
    Util.detectBrowserName();
    Util.canUseLocalStorage();
    // ドロップ領域の外へ落としたPDFは、既定動作のままだとブラウザがそのファイルを開いて
    // 画面を離れてしまい、入力中の内容が失われる。window側で既定動作だけを止める。
    window.addEventListener("dragover", this.preventWindowFileDrop);
    window.addEventListener("drop", this.preventWindowFileDrop);
  },
  beforeUnmount() {
    window.removeEventListener("dragover", this.preventWindowFileDrop);
    window.removeEventListener("drop", this.preventWindowFileDrop);
    this.stopElapsedTimer();
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
    markdownContent(_newValue, _oldValue) {
      this.clearMarkdownPreview();
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
      this.originalFile = PdfFormState.createOriginalFileState();
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
      this.imagesPdf = PdfFormState.createImagesPdfState();
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
     * ページ選択用サムネイルAPIを実行し、成功時はサムネイル一覧へ反映する。
     *
     * サムネイル取得はPDF全体のアップロードを伴うため、利用者がボタンを押したときだけ実行する。
     * 1リクエストで全ページ分を受け取り、ページごとには呼ばない。
     *
     * @returns {Promise<void>} サムネイル取得処理の完了Promise
     */
    requestPdfThumbnails() {
      const originalFileData = this.originalFile;
      if (Util.isEmpty(originalFileData.fileObject)) {
        this.pdfThumbnails.message = "ファイル選択されておりません。";
        return Promise.resolve();
      }
      if (this.isProcessing) {
        return Promise.resolve();
      }
      this.pendingPasswordRetry = () => this.requestPdfThumbnails();
      this.beginProcess(ProcessState.PROCESS_LABEL.THUMBNAILS);
      this.errorMessages = [];
      this.clearApiMessages();
      this.pdfThumbnails.message = "";
      return PdfApiClient.requestPdfThumbnails(
        CONST.REST_PATH.THUMBNAILS_PDF,
        PdfPayload.withPassword(
          PdfPayload.buildThumbnailPayload(originalFileData.fileObject),
          this.pdfPassword
        )
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.failProcess(result.errorMessages, result.errorCodes);
            return;
          }
          this.applyApiMessages(result.messages);
          const thumbnailResponse = result.thumbnailResponse;
          this.pdfThumbnails.pages = thumbnailResponse.pages || [];
          this.pdfThumbnails.selectedPageNumbers = [];
          this.pdfThumbnails.message =
            thumbnailResponse.pageCount +
            "ページのサムネイルを表示しています。ページを選ぶとページ指定へ反映します。";
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
