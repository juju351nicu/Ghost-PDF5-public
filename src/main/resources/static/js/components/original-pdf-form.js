import PageNumberValidator from "../validation/page-number-validator.js";

/**
 * 編集元PDFカードの表示と入力イベントを扱うVueコンポーネント。
 *
 * PDF削除やプレビュー取得などのAPI処理は親のpdf-app.jsに残し、
 * このコンポーネントは入力欄の表示・簡易validation・イベント通知に責務を限定する。
 */
export default {
  name: "OriginalPdfForm",
  props: {
    originalFile: { type: Object, required: true },
    pdfMetadata: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: [
    "file-change",
    "request-open-original-pdf",
    "request-delete-pdf",
    "request-extract-pdf",
    "request-markdown-draft-pdf",
    "request-metadata-pdf",
    "request-text-pdf",
    "request-split-pdf",
    "clear-all",
  ],
  template: `
    <article class="pdf-card">
      <header>編集元ファイル(PDFのみ)</header>
      <div class="pdf-card__body">
        <div class="file-control">
          <input type="file" @change="handleFileChange($event)" accept=".pdf" />
        </div>
        <div class="pdf-card__preview" v-if="originalFile.previewUrl">
          <div class="pdf-card__preview-head">
            <span>{{ originalFile.fileName }}</span>
            <button type="button" @click="requestOpenOriginalPdf">別タブで開く</button>
          </div>
          <iframe class="pdf-card__preview-frame" :src="originalFile.previewUrl"
            title="選択した編集元PDFのプレビュー"></iframe>
        </div>
        <div class="pdf-action-row">
          <input type="checkbox" v-model="originalFile.delPagesChecked.checked"
            aria-label="ページ指定を有効にする" />
          <input type="text" class="page-input" ref="deletePagesText"
            v-model="originalFile.delPagesText.text"
            @blur="validateDeletePagesOnBlur"
            :class="{ 'textbox--error': originalFile.delPagesText.message }"
            :disabled="originalFile.delPagesText.disabled" placeholder="ページ指定  (入力例：2，3-5)" />
          <button type="button" :disabled="isProcessing" @click="requestExtractPdf">抽出する</button>
          <button type="button" :disabled="isProcessing" @click="requestDeletePdf">削除する</button>
          <button type="button" :disabled="isProcessing" @click="requestMetadataPdf">PDF情報を確認</button>
          <button type="button" :disabled="isProcessing" @click="requestTextPdf">テキスト抽出</button>
          <button type="button" :disabled="isProcessing" @click="requestMarkdownDraftPdf">Markdown下書き</button>
          <input type="text" class="page-input" ref="splitRangesText"
            v-model="originalFile.splitRangesText.text"
            @blur="validateSplitRangesOnBlur"
            :class="{ 'textbox--error': originalFile.splitRangesText.message }"
            placeholder="分割範囲  (入力例：1-5, 6-12 / 空欄で1ページずつ)" />
          <button type="button" :disabled="isProcessing" @click="requestSplitPdf">分割する</button>
          <button type="button" @click="clearAll">全クリア</button>
          <br />
          <span class="error_message">{{ originalFile.delPagesText.message }}</span>
          <span class="error_message">{{ originalFile.splitRangesText.message }}</span>
        </div>
        <dl class="pdf-metadata" v-if="pdfMetadata.loaded">
          <div class="pdf-metadata__row">
            <dt>ファイル名</dt>
            <dd>{{ pdfMetadata.fileName }}</dd>
          </div>
          <div class="pdf-metadata__row">
            <dt>ファイルサイズ</dt>
            <dd>{{ formatFileSize(pdfMetadata.fileSize) }}</dd>
          </div>
          <div class="pdf-metadata__row">
            <dt>ページ数</dt>
            <dd>{{ pdfMetadata.pageCount }}ページ</dd>
          </div>
          <div class="pdf-metadata__row">
            <dt>暗号化</dt>
            <dd>{{ formatEncrypted(pdfMetadata.encrypted) }}</dd>
          </div>
        </dl>
      </div>
    </article>
  `,
  methods: {
    /**
     * 編集元PDFファイル選択イベントを親コンポーネントへ通知する。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      this.$emit("file-change", event);
    },
    /**
     * 選択中の編集元PDFを別タブで開くリクエストを親コンポーネントへ通知する。
     */
    requestOpenOriginalPdf() {
      this.$emit("request-open-original-pdf");
    },
    /**
     * 削除ページ入力欄からフォーカスが外れた時に入力値を検証する。
     */
    validateDeletePagesOnBlur() {
      const pageText = this.originalFile.delPagesText.text;
      if (PageNumberValidator.isValidDeletePagesText(pageText)) {
        this.originalFile.delPagesText.message = "";
        return;
      }
      this.originalFile.delPagesText.message =
        "削除ページの番号が正しくありません。";
      this.$nextTick(() => {
        if (this.$refs.deletePagesText) {
          this.$refs.deletePagesText.focus();
        }
      });
    },
    /**
     * 分割範囲入力欄からフォーカスが外れた時に入力値を検証する。
     *
     * 空欄は「1ページずつ分割」を表す正常な入力のため、エラーにしない。
     */
    validateSplitRangesOnBlur() {
      const rangesText = this.originalFile.splitRangesText.text;
      if (PageNumberValidator.isValidSplitRangesText(rangesText)) {
        this.originalFile.splitRangesText.message = "";
        return;
      }
      this.originalFile.splitRangesText.message =
        "分割範囲の指定が正しくありません。";
      this.$nextTick(() => {
        if (this.$refs.splitRangesText) {
          this.$refs.splitRangesText.focus();
        }
      });
    },
    /**
     * 編集元PDFのページ削除リクエストを親コンポーネントへ通知する。
     */
    requestDeletePdf() {
      this.$emit("request-delete-pdf");
    },
    /**
     * 編集元PDFのページ抽出リクエストを親コンポーネントへ通知する。
     */
    requestExtractPdf() {
      this.$emit("request-extract-pdf");
    },
    /**
     * PDFメタデータ取得リクエストを親コンポーネントへ通知する。
     */
    requestMetadataPdf() {
      this.$emit("request-metadata-pdf");
    },
    /**
     * PDFテキスト抽出リクエストを親コンポーネントへ通知する。
     */
    requestTextPdf() {
      this.$emit("request-text-pdf");
    },
    /**
     * ページ単位Markdown下書き生成リクエストを親コンポーネントへ通知する。
     */
    requestMarkdownDraftPdf() {
      this.$emit("request-markdown-draft-pdf");
    },
    /**
     * 編集元PDFの1ページ単位分割リクエストを親コンポーネントへ通知する。
     */
    requestSplitPdf() {
      this.$emit("request-split-pdf");
    },
    /**
     * ファイルサイズを画面表示用に整形する。
     *
     * @param {number|null} fileSize ファイルサイズ
     * @returns {string} 画面表示用ファイルサイズ
     */
    formatFileSize(fileSize) {
      if (fileSize === null || fileSize === undefined) {
        return "";
      }
      return fileSize.toLocaleString() + " bytes";
    },
    /**
     * 暗号化状態を画面表示用に整形する。
     *
     * @param {boolean|null} encrypted 暗号化状態
     * @returns {string} 画面表示用暗号化状態
     */
    formatEncrypted(encrypted) {
      if (encrypted === null || encrypted === undefined) {
        return "";
      }
      return encrypted ? "あり" : "なし";
    },
    /**
     * 画面全体の入力初期化を親コンポーネントへ通知する。
     */
    clearAll() {
      this.$emit("clear-all");
    },
  },
};
