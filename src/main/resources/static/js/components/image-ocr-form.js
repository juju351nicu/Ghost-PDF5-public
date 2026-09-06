/**
 * 画像から文字起こしする画像OCRカードのVueコンポーネント。
 *
 * ファイル選択とクリップボード貼り付けで画像を受け取り、選択画像のプレビューと
 * 実行・クリアのイベント通知に責務を限定する。API呼び出しは親のpdf-app.jsに残す。
 */
export default {
  name: "ImageOcrForm",
  props: {
    imageState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: ["image-selected", "request-image-draft", "clear-image"],
  template: `
    <article class="pdf-card image-ocr-card">
      <header>画像からMarkdown（文字起こし）</header>
      <div class="pdf-card__body image-ocr-card__body">
        <div class="file-control">
          <input type="file" accept="image/png,image/jpeg,image/gif,image/webp"
            @change="handleFileChange($event)" />
        </div>
        <div class="image-ocr-card__paste" tabindex="0"
          aria-label="画像をクリップボードから貼り付け" @paste="handlePaste($event)">
          ここをクリックして Ctrl+V で画像を貼り付け
        </div>
        <div class="image-ocr-card__preview" v-if="imageState.previewUrl">
          <img :src="imageState.previewUrl" alt="選択した画像のプレビュー" />
          <span>{{ imageState.fileName }}</span>
        </div>
        <div class="image-ocr-card__actions">
          <button type="button" :disabled="isProcessing || !imageState.fileObject"
            @click="requestImageDraft">画像OCR</button>
          <button type="button" :disabled="isProcessing" @click="clearImage">クリア</button>
        </div>
      </div>
    </article>
  `,
  methods: {
    /**
     * ファイル選択で選ばれた画像を親コンポーネントへ通知する。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.$emit("image-selected", file);
      }
    },
    /**
     * クリップボードから貼り付けられた画像を親コンポーネントへ通知する。
     *
     * @param {ClipboardEvent} event 貼り付けイベント
     */
    handlePaste(event) {
      const items = event.clipboardData?.items;
      if (!items) {
        return;
      }
      for (let index = 0; index < items.length; index++) {
        const item = items[index];
        if (item.kind === "file" && item.type.startsWith("image/")) {
          const file = item.getAsFile();
          if (file) {
            this.$emit("image-selected", file);
          }
          return;
        }
      }
    },
    /**
     * 画像OCRの実行を親コンポーネントへ通知する。
     */
    requestImageDraft() {
      this.$emit("request-image-draft");
    },
    /**
     * 選択画像のクリアを親コンポーネントへ通知する。
     */
    clearImage() {
      this.$emit("clear-image");
    },
  },
};
