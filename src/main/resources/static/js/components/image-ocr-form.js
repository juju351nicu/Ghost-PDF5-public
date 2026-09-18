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
        <p class="image-ocr-card__notice">
          PNG / JPEG などの画像を選択、またはCtrl+Vで貼り付けてMarkdown形式の文字起こしを作ります。
          結果はMarkdownメモタブの編集欄へそのまま入るので、続けて保存・プレビュー・PDF出力へ進めます。
        </p>
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
        <p class="markdown-draft-mode__notice">
          文字起こしの実行先は ghost.ocr.provider の設定で決まります。anthropic / openai を選んでいる場合は
          画像データを外部AIへ送信します。tesseract はローカルで処理し、外部送信は行いません。
        </p>
        <div class="image-ocr-card__actions">
          <button type="button" class="btn-primary" :disabled="isProcessing || !imageState.fileObject"
            @click="requestImageDraft">画像OCR</button>
          <button type="button" class="btn-neutral" :disabled="isProcessing" @click="clearImage">クリア</button>
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
