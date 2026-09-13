import FileDropZone from "./file-drop-zone.js";

/**
 * HTMLファイルからPDFを作るカードのVueコンポーネント。
 *
 * ファイル選択とドロップでHTMLを受け取り、選択中ファイル名の表示と
 * 実行・クリアのイベント通知に責務を限定する。API呼び出しは親のpdf-app.jsに残す。
 */
export default {
  name: "HtmlToPdfForm",
  components: {
    "file-drop-zone": FileDropZone,
  },
  props: {
    htmlPdfState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: ["html-selected", "request-pdf-from-html", "clear-html"],
  template: `
    <article class="pdf-card html-pdf-card">
      <header>HTMLからPDF</header>
      <div class="pdf-card__body html-pdf-card__body">
        <file-drop-zone accept=".html,.htm" label="ここにHTMLをドロップ、またはファイルを選択"
          @files-dropped="handleFilesDropped">
          <div class="file-control">
            <input type="file" accept=".html,.htm,text/html" @change="handleFileChange($event)" />
          </div>
        </file-drop-zone>
        <p class="html-pdf-card__selected" v-if="htmlPdfState.fileObject">
          {{ htmlPdfState.fileName }}
        </p>
        <p class="html-pdf-card__notice">
          外部CSSや外部画像は取り込みません。見た目を保つにはHTML内でスタイルを完結させてください。
        </p>
        <div class="html-pdf-card__actions">
          <button type="button" :disabled="isProcessing || !htmlPdfState.fileObject"
            @click="requestPdfFromHtml">PDFにする</button>
          <button type="button" :disabled="isProcessing" @click="clearHtml">クリア</button>
        </div>
      </div>
    </article>
  `,
  methods: {
    /**
     * ファイル選択で選ばれたHTMLを親コンポーネントへ通知する。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.$emit("html-selected", file);
      }
    },
    /**
     * ドロップされたHTMLを親コンポーネントへ通知する。
     *
     * @param {File[]} files ドロップされたファイル
     */
    handleFilesDropped(files) {
      if (files.length > 0) {
        this.$emit("html-selected", files[0]);
      }
    },
    /**
     * HTMLからのPDF作成を親コンポーネントへ通知する。
     */
    requestPdfFromHtml() {
      this.$emit("request-pdf-from-html");
    },
    /**
     * 選択HTMLのクリアを親コンポーネントへ通知する。
     */
    clearHtml() {
      this.$emit("clear-html");
    },
  },
};
