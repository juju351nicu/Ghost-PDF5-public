import FileDropZone from "./file-drop-zone.js";

/**
 * EPUBからPDFを作るカードのVueコンポーネント。
 *
 * ファイル選択とドロップでEPUBを受け取り、選択中ファイル名の表示と
 * 実行・クリアのイベント通知に責務を限定する。API呼び出しは親のpdf-app.jsに残す。
 */
export default {
  name: "EpubToPdfForm",
  components: {
    "file-drop-zone": FileDropZone,
  },
  props: {
    epubState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: ["epub-selected", "request-pdf-from-epub", "clear-epub"],
  template: `
    <article class="pdf-card epub-card">
      <header>EPUBからPDF</header>
      <div class="pdf-card__body epub-card__body">
        <file-drop-zone accept=".epub" label="ここにEPUBをドロップ、またはファイルを選択"
          @files-dropped="handleFilesDropped">
          <div class="file-control">
            <input type="file" accept=".epub,application/epub+zip" @change="handleFileChange($event)" />
          </div>
        </file-drop-zone>
        <p class="epub-card__selected" v-if="epubState.fileObject">
          {{ epubState.fileName }}
        </p>
        <p class="epub-card__notice">
          本文を読む順序どおりに連結してPDFにします。EPUB内のCSS・画像・フォントは取り込まないため、
          リーダーで開いたときの見た目とは一致しません。
        </p>
        <div class="epub-card__actions">
          <button type="button" class="btn-primary" :disabled="isProcessing || !epubState.fileObject"
            @click="requestPdfFromEpub">PDFにする</button>
          <button type="button" class="btn-neutral" :disabled="isProcessing" @click="clearEpub">クリア</button>
        </div>
      </div>
    </article>
  `,
  methods: {
    /**
     * ファイル選択で選ばれたEPUBを親コンポーネントへ通知する。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.$emit("epub-selected", file);
      }
    },
    /**
     * ドロップされたEPUBを親コンポーネントへ通知する。
     *
     * @param {File[]} files ドロップされたファイル
     */
    handleFilesDropped(files) {
      if (files.length > 0) {
        this.$emit("epub-selected", files[0]);
      }
    },
    /**
     * EPUBからのPDF作成を親コンポーネントへ通知する。
     */
    requestPdfFromEpub() {
      this.$emit("request-pdf-from-epub");
    },
    /**
     * 選択EPUBのクリアを親コンポーネントへ通知する。
     */
    clearEpub() {
      this.$emit("clear-epub");
    },
  },
};
