import FileDropZone from "./file-drop-zone.js";
import PdfFormState from "../models/pdf-form-state.js";

/**
 * 複数の画像を1つのPDFへまとめるカードのVueコンポーネント。
 *
 * ファイル選択とドロップで画像を受け取り、並び順の表示と実行・クリアのイベント通知に
 * 責務を限定する。API呼び出しは親のpdf-app.jsに残す。
 */
export default {
  name: "ImagesToPdfForm",
  components: {
    "file-drop-zone": FileDropZone,
  },
  props: {
    imagesPdfState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: ["images-selected", "request-pdf-from-images", "clear-images"],
  template: `
    <article class="pdf-card images-pdf-card">
      <header>画像からPDF</header>
      <div class="pdf-card__body images-pdf-card__body">
        <file-drop-zone accept="image/*" label="ここに画像をドロップ、またはファイルを選択"
          @files-dropped="handleFilesDropped">
          <div class="file-control">
            <input type="file" multiple accept="image/png,image/jpeg,image/tiff,image/bmp"
              @change="handleFileChange($event)" />
          </div>
        </file-drop-zone>
        <ol class="images-pdf-card__list" v-if="imagesPdfState.files.length > 0">
          <li v-for="(imageFile, index) in imagesPdfState.files" :key="index">
            {{ imageFile.name }}
          </li>
        </ol>
        <p class="images-pdf-card__notice" v-if="imagesPdfState.files.length > 0">
          選択した順にページへ並べます。
        </p>
        <div class="images-pdf-card__actions">
          <select class="page-size-select" v-model="imagesPdfState.pageSize"
            aria-label="PDFのページサイズ">
            <option v-for="item in pageSizeItems" :key="item.id" :value="item.id">
              {{ item.name }}
            </option>
          </select>
          <button type="button" :disabled="isProcessing || imagesPdfState.files.length === 0"
            @click="requestPdfFromImages">PDFにする</button>
          <button type="button" :disabled="isProcessing" @click="clearImages">クリア</button>
        </div>
      </div>
    </article>
  `,
  data() {
    return {
      pageSizeItems: PdfFormState.createImagePageSizeItems(),
    };
  },
  methods: {
    /**
     * ファイル選択で選ばれた画像を親コンポーネントへ通知する。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      this.$emit("images-selected", Array.from(event.target.files));
    },
    /**
     * ドロップされた画像を親コンポーネントへ通知する。
     *
     * @param {File[]} files ドロップされたファイル
     */
    handleFilesDropped(files) {
      this.$emit("images-selected", files);
    },
    /**
     * 画像からのPDF作成を親コンポーネントへ通知する。
     */
    requestPdfFromImages() {
      this.$emit("request-pdf-from-images");
    },
    /**
     * 選択画像のクリアを親コンポーネントへ通知する。
     */
    clearImages() {
      this.$emit("clear-images");
    },
  },
};
