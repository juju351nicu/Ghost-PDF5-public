import FileDropZone from "./file-drop-zone.js";

/**
 * Office文書（Word / Excel / PowerPoint）を変換するカードのVueコンポーネント。
 *
 * ファイル選択とドロップでOffice文書を受け取り、Markdown化・PDF化のイベント通知に
 * 責務を限定する。API呼び出しは親のpdf-app.jsに残す。
 */
export default {
  name: "OfficeForm",
  components: {
    "file-drop-zone": FileDropZone,
  },
  props: {
    officeState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: [
    "office-selected",
    "request-office-markdown",
    "request-pdf-from-office",
    "clear-office",
  ],
  template: `
    <article class="pdf-card office-card">
      <header>Office文書（Word / Excel / PowerPoint）</header>
      <div class="pdf-card__body office-card__body">
        <file-drop-zone accept=".docx,.xlsx,.pptx"
          label="ここに .docx / .xlsx / .pptx をドロップ、またはファイルを選択"
          @files-dropped="handleFilesDropped">
          <div class="file-control">
            <input type="file" accept=".docx,.xlsx,.pptx" @change="handleFileChange($event)" />
          </div>
        </file-drop-zone>
        <p class="office-card__selected" v-if="officeState.fileObject">
          {{ officeState.fileName }}
        </p>
        <p class="office-card__notice">
          Word / Excel は内容レベルの変換です（レイアウトと書式は再現しません）。
          PowerPoint はスライドを画像化するため見た目は保たれますが、PDFの文字は選択できません。
          旧形式（.doc / .xls / .ppt）は対象外です。
        </p>
        <div class="office-card__actions">
          <button type="button" class="btn-primary" :disabled="isProcessing || !officeState.fileObject"
            @click="requestOfficeMarkdown">Markdownにする</button>
          <button type="button" class="btn-primary" :disabled="isProcessing || !officeState.fileObject"
            @click="requestPdfFromOffice">PDFにする</button>
          <button type="button" class="btn-neutral" :disabled="isProcessing" @click="clearOffice">クリア</button>
        </div>
      </div>
    </article>
  `,
  methods: {
    /**
     * ファイル選択で選ばれたOffice文書を親コンポーネントへ通知する。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.$emit("office-selected", file);
      }
    },
    /**
     * ドロップされたOffice文書を親コンポーネントへ通知する。
     *
     * @param {File[]} files ドロップされたファイル
     */
    handleFilesDropped(files) {
      if (files.length > 0) {
        this.$emit("office-selected", files[0]);
      }
    },
    /**
     * Office文書からのMarkdown生成を親コンポーネントへ通知する。
     */
    requestOfficeMarkdown() {
      this.$emit("request-office-markdown");
    },
    /**
     * Office文書からのPDF生成を親コンポーネントへ通知する。
     */
    requestPdfFromOffice() {
      this.$emit("request-pdf-from-office");
    },
    /**
     * 選択Office文書のクリアを親コンポーネントへ通知する。
     */
    clearOffice() {
      this.$emit("clear-office");
    },
  },
};
