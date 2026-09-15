import PdfFormState from "../models/pdf-form-state.js";
import PageNumberValidator from "../validation/page-number-validator.js";
import PdfThumbnailList from "./pdf-thumbnail-list.js";
import FileDropZone from "./file-drop-zone.js";

/**
 * 編集元PDFカードの表示と入力イベントを扱うVueコンポーネント。
 *
 * PDF削除やプレビュー取得などのAPI処理は親のpdf-app.jsに残し、
 * このコンポーネントは入力欄の表示・簡易validation・イベント通知に責務を限定する。
 */
export default {
  name: "OriginalPdfForm",
  components: {
    "pdf-thumbnail-list": PdfThumbnailList,
    "file-drop-zone": FileDropZone,
  },
  props: {
    originalFile: { type: Object, required: true },
    pdfMetadata: { type: Object, required: true },
    thumbnailState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: [
    "file-change",
    "files-dropped",
    "request-thumbnails-pdf",
    "toggle-thumbnail-page",
    "clear-thumbnail-selection",
    "request-open-original-pdf",
    "request-delete-pdf",
    "request-extract-pdf",
    "request-markdown-draft-pdf",
    "request-metadata-pdf",
    "request-text-pdf",
    "request-split-pdf",
    "request-rotate-pdf",
    "request-images-pdf",
    "request-html-pdf",
    "request-office-from-pdf",
    "request-epub-pdf",
    "clear-all",
  ],
  template: `
    <article class="pdf-card">
      <header>編集元ファイル(PDFのみ)</header>
      <div class="pdf-card__body">
        <file-drop-zone accept=".pdf" label="ここにPDFをドロップ、またはファイルを選択"
          @files-dropped="handleFilesDropped">
          <div class="file-control">
            <input type="file" @change="handleFileChange($event)" accept=".pdf" />
          </div>
        </file-drop-zone>
        <div class="pdf-card__preview" v-if="originalFile.previewUrl">
          <div class="pdf-card__preview-head">
            <span>{{ originalFile.fileName }}</span>
            <button type="button" @click="requestOpenOriginalPdf">別タブで開く</button>
          </div>
          <iframe class="pdf-card__preview-frame" :src="originalFile.previewUrl"
            title="選択した編集元PDFのプレビュー"></iframe>
        </div>
        <p class="pdf-card__hint" v-if="!originalFile.fileObject">
          PDFを選択すると、ページ操作・解析・変換のメニューが表示されます。
        </p>
        <template v-else>
          <h3 class="pdf-card__section-title">ページ操作</h3>
          <div class="pdf-action-row">
            <input type="checkbox" v-model="originalFile.delPagesChecked.checked"
              aria-label="ページ指定を有効にする" />
            <input type="text" class="page-input" ref="deletePagesText"
              v-model="originalFile.delPagesText.text"
              @blur="validateDeletePagesOnBlur"
              :class="{ 'textbox--error': originalFile.delPagesText.message }"
              :disabled="originalFile.delPagesText.disabled" placeholder="ページ指定  (入力例：2, 3-5)" />
            <button type="button" :disabled="isProcessing" @click="requestExtractPdf">抽出する</button>
            <button type="button" :disabled="isProcessing" @click="requestDeletePdf">削除する</button>
            <input type="text" class="page-input" ref="splitRangesText"
              v-model="originalFile.splitRangesText.text"
              @blur="validateSplitRangesOnBlur"
              :class="{ 'textbox--error': originalFile.splitRangesText.message }"
              placeholder="分割範囲  (入力例：1-5, 6-12 / 空欄で1ページずつ)" />
            <button type="button" :disabled="isProcessing" @click="requestSplitPdf">分割する</button>
            <select class="rotation-select" v-model="originalFile.rotation" aria-label="ページの回転角">
              <option v-for="item in rotationItems" :key="item.id" :value="item.id">
                {{ item.name }}
              </option>
            </select>
            <button type="button" :disabled="isProcessing" @click="requestRotatePdf">回転する</button>
            <br />
            <span class="error_message">{{ originalFile.delPagesText.message }}</span>
            <span class="error_message">{{ originalFile.splitRangesText.message }}</span>
          </div>

          <h3 class="pdf-card__section-title">解析・他形式へ変換</h3>
          <div class="pdf-action-row">
            <button type="button" class="outline" :disabled="isProcessing" @click="requestMetadataPdf">PDF情報を確認</button>
            <button type="button" class="outline" :disabled="isProcessing" @click="requestTextPdf">テキスト抽出</button>
            <select class="markdown-draft-mode-select" v-model="originalFile.markdownDraftMode"
              aria-label="Markdown下書きの変換モード">
              <option v-for="item in markdownDraftModeItems" :key="item.id" :value="item.id">
                {{ item.name }}
              </option>
            </select>
            <button type="button" :disabled="isProcessing" @click="requestMarkdownDraftPdf">Markdown下書き</button>
            <select class="image-format-select" v-model="originalFile.imageFormat" aria-label="画像化の出力形式">
              <option v-for="item in imageFormatItems" :key="item.id" :value="item.id">
                {{ item.name }}
              </option>
            </select>
            <input type="number" class="dpi-input" v-model="originalFile.imageDpi" min="1"
              aria-label="画像化の解像度（DPI）" placeholder="dpi（空欄で既定値）" />
            <button type="button" :disabled="isProcessing" @click="requestImagesPdf">画像化する</button>
            <button type="button" :disabled="isProcessing" @click="requestHtmlPdf">HTML出力</button>
            <select class="office-format-select" v-model="originalFile.officeFormat"
              aria-label="出力するOffice形式">
              <option v-for="item in officeFormatItems" :key="item.id" :value="item.id">
                {{ item.name }}
              </option>
            </select>
            <button type="button" :disabled="isProcessing" @click="requestOfficeFromPdf">Office出力</button>
            <button type="button" :disabled="isProcessing" @click="requestEpubPdf">EPUB出力</button>
            <p class="markdown-draft-mode__notice" v-if="isVisionModeSelected">
              VISIONは全ページを外部AIへ送るため、ページ数分の費用が発生します。
            </p>
          </div>

          <button type="button" class="secondary" @click="clearAll">全クリア</button>
        </template>
        <pdf-thumbnail-list
          :thumbnail-state="thumbnailState"
          :is-processing="isProcessing"
          @request-thumbnails="requestThumbnailsPdf"
          @toggle-page="toggleThumbnailPage"
          @clear-selection="clearThumbnailSelection">
        </pdf-thumbnail-list>
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
  data() {
    return {
      markdownDraftModeItems: PdfFormState.createMarkdownDraftModeItems(),
      rotationItems: PdfFormState.createRotationItems(),
      imageFormatItems: PdfFormState.createImageFormatItems(),
      officeFormatItems: PdfFormState.createOfficeFormatItems(),
    };
  },
  computed: {
    /**
     * 変換モードにVISIONが選ばれているか判定する。
     *
     * @returns {boolean} VISIONが選ばれている場合はtrue
     */
    isVisionModeSelected() {
      return this.originalFile.markdownDraftMode === "VISION";
    },
  },
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
     * ドロップされた編集元PDFを親コンポーネントへ通知する。
     *
     * @param {File[]} files ドロップされたファイル
     */
    handleFilesDropped(files) {
      this.$emit("files-dropped", files);
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
     * 編集元PDFのページ回転リクエストを親コンポーネントへ通知する。
     */
    requestRotatePdf() {
      this.$emit("request-rotate-pdf");
    },
    /**
     * 編集元PDFのページ画像化リクエストを親コンポーネントへ通知する。
     */
    requestImagesPdf() {
      this.$emit("request-images-pdf");
    },
    /**
     * 編集元PDFのHTML出力リクエストを親コンポーネントへ通知する。
     */
    requestHtmlPdf() {
      this.$emit("request-html-pdf");
    },
    /**
     * 編集元PDFのOffice文書出力リクエストを親コンポーネントへ通知する。
     */
    requestOfficeFromPdf() {
      this.$emit("request-office-from-pdf");
    },
    /**
     * 編集元PDFのEPUB出力リクエストを親コンポーネントへ通知する。
     */
    requestEpubPdf() {
      this.$emit("request-epub-pdf");
    },
    /**
     * サムネイル取得リクエストを親コンポーネントへ通知する。
     */
    requestThumbnailsPdf() {
      this.$emit("request-thumbnails-pdf");
    },
    /**
     * サムネイルのページ選択・解除を親コンポーネントへ通知する。
     *
     * @param {number} pageNumber 1始まりのページ番号
     */
    toggleThumbnailPage(pageNumber) {
      this.$emit("toggle-thumbnail-page", pageNumber);
    },
    /**
     * サムネイル選択の全解除を親コンポーネントへ通知する。
     */
    clearThumbnailSelection() {
      this.$emit("clear-thumbnail-selection");
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
