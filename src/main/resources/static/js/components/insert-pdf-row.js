import PageNumberValidator from "../validation/page-number-validator.js";

/**
 * 差し込みPDFの1行分の入力UIを扱うVueコンポーネント。
 *
 * 並び替え、PDF生成、API通信は親のpdf-app.jsに残し、
 * このコンポーネントは行単位の表示・ファイル選択通知・ページ番号validationに責務を限定する。
 */
export default {
  name: "InsertPdfRow",
  props: {
    insertFile: { type: Object, required: true },
    insertPagePulldown: { type: Array, required: true },
  },
  emits: ["file-change", "clear-insert-file-row", "remove-insert-file-row"],
  template: `
    <article class="pdf-card">
      <header>No.{{ insertFile.fileNo }}&nbsp;{{ insertFile.fileName }}</header>
      <div class="pdf-card__body">
        <div class="file-control">
          <input type="file" @change="handleFileChange($event)" accept=".pdf" />
        </div>
        <div class="pdf-action-row">
          <input type="checkbox" v-model="insertFile.insertPageChecked.checked"
            aria-label="差し込みページ指定を有効にする" />
          <input type="number" class="page-input" ref="insertPageText"
            v-model="insertFile.insertPageText.text"
            @blur="validateInsertPageOnBlur"
            :class="{ 'textbox--error': insertFile.insertPageText.message }"
            :disabled="insertFile.insertPageText.disabled" placeholder="ページ番号" />
          <select class="insert-option-select" v-model="insertFile.insertOption">
            <option v-for="item in insertPagePulldown" :key="item.id" :value="item.id">
              {{ item.name }}
            </option>
          </select>
          <div class="insert-edge-options">
            <input type="checkbox" v-model="insertFile.insertPrev.checked" />
            <span>{{ insertFile.insertPrev.text }}</span>
            <br />
            <input type="checkbox" v-model="insertFile.insertNext.checked" />
            <span>{{ insertFile.insertNext.text }}</span>
          </div>
          <button type="button" @click="clearInsertFileRow">クリア</button>
          <button type="button" @click="removeInsertFileRow">行の削除</button>
        </div>
      </div>
    </article>
  `,
  methods: {
    /**
     * 差し込みPDFファイル選択イベントを親コンポーネントへ通知する。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      this.$emit("file-change", event, this.insertFile.fileNo);
    },
    /**
     * 差し込みページ入力欄からフォーカスが外れた時に入力値を検証する。
     */
    validateInsertPageOnBlur() {
      const pageText = this.insertFile.insertPageText.text;
      if (PageNumberValidator.isValidInsertPageText(pageText)) {
        this.insertFile.insertPageText.message = "";
        return;
      }
      this.insertFile.insertPageText.message =
        "ページ番号が数値ではありません。";
      this.$nextTick(() => {
        if (this.$refs.insertPageText) {
          this.$refs.insertPageText.focus();
        }
      });
    },
    /**
     * 差し込みPDF行の入力値初期化を親コンポーネントへ通知する。
     */
    clearInsertFileRow() {
      this.$emit("clear-insert-file-row", this.insertFile.fileNo);
    },
    /**
     * 差し込みPDF行の削除を親コンポーネントへ通知する。
     */
    removeInsertFileRow() {
      this.$emit("remove-insert-file-row", this.insertFile.fileNo);
    },
  },
};
