/**
 * PDFのページ選択用サムネイル一覧を表示するVueコンポーネント。
 *
 * API呼び出しは親のpdf-app.jsに残し、このコンポーネントは表示・選択状態の見せ方・
 * イベント通知に責務を限定する（既存のoriginal-pdf-form.jsと同じ分担）。
 *
 * サムネイルの取得は自動では行わない。サーバー側に文書セッションが無く、取得のたびに
 * PDF全体をアップロードするため、利用者がボタンを押したときだけ走らせる。
 */
export default {
  name: "PdfThumbnailList",
  props: {
    thumbnailState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: ["request-thumbnails", "toggle-page", "clear-selection"],
  template: `
    <div class="pdf-thumbnail-list">
      <div class="pdf-thumbnail-list__toolbar">
        <button type="button" :disabled="isProcessing" @click="requestThumbnails">サムネイル表示</button>
        <button type="button" :disabled="isProcessing || selectedCount === 0"
          @click="clearSelection">選択解除</button>
        <span class="pdf-thumbnail-list__message" v-if="thumbnailState.message">
          {{ thumbnailState.message }}
        </span>
      </div>
      <div class="pdf-thumbnail-list__grid" v-if="thumbnailState.pages.length > 0">
        <button type="button" class="pdf-thumbnail-list__item"
          :class="{ 'pdf-thumbnail-list__item--selected': isSelected(page.pageNumber) }"
          :aria-pressed="isSelected(page.pageNumber)" :disabled="isProcessing"
          v-for="page in thumbnailState.pages" :key="page.pageNumber"
          @click="togglePage(page.pageNumber)">
          <img class="pdf-thumbnail-list__image" :src="page.dataUri" :width="page.width"
            :height="page.height" :alt="page.pageNumber + 'ページ目のサムネイル'" />
          <span class="pdf-thumbnail-list__page-number">{{ page.pageNumber }}</span>
        </button>
      </div>
    </div>
  `,
  computed: {
    /**
     * 選択中のページ数を返す。
     *
     * @returns {number} 選択中のページ数
     */
    selectedCount() {
      return this.thumbnailState.selectedPageNumbers.length;
    },
  },
  methods: {
    /**
     * 指定ページが選択中か判定する。
     *
     * @param {number} pageNumber 1始まりのページ番号
     * @returns {boolean} 選択中の場合はtrue
     */
    isSelected(pageNumber) {
      return this.thumbnailState.selectedPageNumbers.includes(pageNumber);
    },
    /**
     * サムネイル取得リクエストを親コンポーネントへ通知する。
     */
    requestThumbnails() {
      this.$emit("request-thumbnails");
    },
    /**
     * ページの選択・解除を親コンポーネントへ通知する。
     *
     * @param {number} pageNumber 1始まりのページ番号
     */
    togglePage(pageNumber) {
      this.$emit("toggle-page", pageNumber);
    },
    /**
     * 選択の全解除を親コンポーネントへ通知する。
     */
    clearSelection() {
      this.$emit("clear-selection");
    },
  },
};
