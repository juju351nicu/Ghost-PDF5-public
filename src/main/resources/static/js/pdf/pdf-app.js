import Util from "../util.js";
import CONST from "../const.js";
import Modal from "../components/modal.js";
import TheHeader from "../components/theheader.js";
import TheFooter from "../components/thefooter.js";
import OriginalPdfForm from "../components/original-pdf-form.js";
import InsertPdfRow from "../components/insert-pdf-row.js";
import ImageOcrForm from "../components/image-ocr-form.js";
import PdfApiClient from "../api/pdf-api-client.js";
import FileResponseHandler from "../api/file-response-handler.js";
import ImageApiClient from "../api/image-api-client.js";
import MarkdownApiClient from "../api/markdown-api-client.js";
import PdfPayload from "../api/pdf-payload.js";
import ImagePayload from "../api/image-payload.js";
import PdfFormState from "../models/pdf-form-state.js";
import PageNumberValidator from "../validation/page-number-validator.js";
import FileSizeValidator from "../validation/file-size-validator.js";

const draggable = window["vuedraggable"];
const SPLIT_PDF_FILE_NAME = "split.zip";

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
  },
  data() {
    return {
      originalFile: PdfFormState.createOriginalFileState(),
      pdfMetadata: PdfFormState.createPdfMetadataState(),
      insertFiles: PdfFormState.createInitialInsertFiles(),
      newNo: 3,
      insertPagePulldown: PdfFormState.createInsertOptionItems(),
      isShowModal: false,
      errorMessages: [],
      isProcessing: false,
      imageDraft: PdfFormState.createImageDraftState(),
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
      this.originalFile = PdfFormState.createOriginalFileState();
      this.pdfMetadata = PdfFormState.createPdfMetadataState();
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
      const index = this.findInsertFileIndex(fileNo);
      const fileObject = event.target.files[0];
      if (Util.isEmpty(fileObject)) {
        return;
      }
      if (!FileSizeValidator.isWithinPdfSizeLimit(fileObject)) {
        // 上限超過はサーバーへ送らずここで止める。送るとTomcatが上限検知時に接続を切るため、
        // ブラウザには413ではなく理由の分からないネットワークエラーだけが残る。
        this.errorMessages = [
          FileSizeValidator.buildPdfSizeLimitMessage(fileObject),
        ];
        this.showMessageModal();
        // 受け付けなかったファイル名が選択欄に残ると、保持中のPDFと表示が食い違うため戻す。
        event.target.value = "";
        return;
      }
      if (index !== -1) {
        this.insertFiles[index].fileObject = fileObject;
        this.insertFiles[index].fileName = fileObject.name;
        // 差し込みPDFはカード内に表示枠を持たないため、従来どおり別タブで開く。
        FileResponseHandler.openPdfBlob(fileObject);
        return;
      }
      this.originalFile.fileObject = fileObject;
      this.originalFile.fileName = fileObject.name;
      this.pdfMetadata = PdfFormState.createPdfMetadataState();
      this.updateOriginalPdfPreview(fileObject);
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
        PdfPayload.buildMarkdownDraftPayload(originalFileData.fileObject)
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
      this.isProcessing = true;
      this.errorMessages = [];
      this.markdownMessage = "";
      return ImageApiClient.requestImageMarkdownDraft(
        CONST.REST_PATH.MARKDOWN_DRAFT_IMAGE,
        payload
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.errorMessages = result.errorMessages;
            this.showMessageModal();
            return;
          }
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
          this.errorMessages = [
            ImageApiClient.buildUnexpectedErrorMessage(error),
          ];
          this.showMessageModal();
        })
        .finally(() => {
          this.isProcessing = false;
        });
    },
    /**
     * 編集元PDFを1ページずつ分割し、生成されたZIPをダウンロードする。
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
      // 保存ダイアログ表示中はブラウザ側がモーダルで操作を止めるため、isProcessingは立てない。
      // ここで立てるとキャンセル時に解除漏れの経路が増える。
      const saveTarget = await this.requestSaveTarget(
        SPLIT_PDF_FILE_NAME,
        FileResponseHandler.FILE_TYPES.ZIP
      );
      if (saveTarget.cancelled) {
        return;
      }
      const payload = PdfPayload.buildSplitPayload(originalFileData.fileObject);
      await this.requestFileAndDownload(
        CONST.REST_PATH.SPLIT_PDF,
        payload,
        SPLIT_PDF_FILE_NAME,
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
      this.isProcessing = true;
      this.errorMessages = [];
      this.markdownMessage = "";
      return request()
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.errorMessages = result.errorMessages;
            this.showMessageModal();
            return;
          }
          onSuccess(result.data);
        })
        .catch((error) => {
          this.errorMessages = [
            MarkdownApiClient.buildUnexpectedErrorMessage(error),
          ];
          this.showMessageModal();
        })
        .finally(() => {
          this.isProcessing = false;
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
      this.isProcessing = true;
      this.errorMessages = [];
      return PdfApiClient.requestPdfAndOpen(url, payload)
        .then((errorMessages) => {
          if (!Util.isEmpty(errorMessages)) {
            this.errorMessages = errorMessages;
            this.showMessageModal();
          }
        })
        .catch((error) => {
          this.errorMessages = [
            PdfApiClient.buildUnexpectedErrorMessage(error),
          ];
          this.showMessageModal();
        })
        .finally(() => {
          this.isProcessing = false;
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
      this.isProcessing = true;
      this.errorMessages = [];
      return PdfApiClient.requestFileAndDownload(
        url,
        payload,
        defaultFileName,
        saveTarget
      )
        .then((errorMessages) => {
          if (!Util.isEmpty(errorMessages)) {
            this.errorMessages = errorMessages;
            this.showMessageModal();
          }
        })
        .catch((error) => {
          this.errorMessages = [
            PdfApiClient.buildUnexpectedErrorMessage(error),
          ];
          this.showMessageModal();
        })
        .finally(() => {
          this.isProcessing = false;
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
      this.isProcessing = true;
      this.errorMessages = [];
      return PdfApiClient.requestPdfMetadata(CONST.REST_PATH.METADATA_PDF, payload)
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.errorMessages = result.errorMessages;
            this.showMessageModal();
            return;
          }
          this.pdfMetadata = PdfFormState.createLoadedPdfMetadataState(
            result.metadata
          );
        })
        .catch((error) => {
          this.errorMessages = [
            PdfApiClient.buildUnexpectedErrorMessage(error),
          ];
          this.showMessageModal();
        })
        .finally(() => {
          this.isProcessing = false;
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
      this.isProcessing = true;
      this.errorMessages = [];
      this.markdownMessage = "";
      return PdfApiClient.requestPdfText(CONST.REST_PATH.TEXT_PDF, payload)
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.errorMessages = result.errorMessages;
            this.showMessageModal();
            return;
          }
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
          this.errorMessages = [
            PdfApiClient.buildUnexpectedErrorMessage(error),
          ];
          this.showMessageModal();
        })
        .finally(() => {
          this.isProcessing = false;
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
      this.isProcessing = true;
      this.errorMessages = [];
      this.markdownMessage = "";
      return PdfApiClient.requestPdfMarkdownDraft(
        CONST.REST_PATH.MARKDOWN_DRAFT_PDF,
        payload
      )
        .then((result) => {
          if (!Util.isEmpty(result.errorMessages)) {
            this.errorMessages = result.errorMessages;
            this.showMessageModal();
            return;
          }
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
          this.errorMessages = [
            PdfApiClient.buildUnexpectedErrorMessage(error),
          ];
          this.showMessageModal();
        })
        .finally(() => {
          this.isProcessing = false;
        });
    },
  },
};

export default pdfApp;
