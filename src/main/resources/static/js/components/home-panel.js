/**
 * よく使う操作への導線をまとめたホームタブのVueコンポーネント。
 *
 * 機能タブを1つずつ探す代わりに、最初にここから目的の作業へ移動できるようにする。
 * 実際の操作（ファイル選択や実行）は移動先の各タブに残し、このコンポーネントはタブ遷移と
 * 最近保存したMarkdownを開く操作の通知に責務を限定する。
 */
export default {
  name: "HomePanel",
  props: {
    recentMarkdownFiles: { type: Array, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: ["navigate-tab", "open-recent-markdown"],
  template: `
    <article class="pdf-card home-panel">
      <header>ホーム</header>
      <div class="pdf-card__body home-panel__body">
        <section aria-label="よく使う操作">
          <p class="pdf-card__section-title">よく使う操作</p>
          <p class="pdf-card__hint">押すと該当タブへ移動します。ファイル選択と実行は移動先で行います。</p>
          <!-- ここのボタンはタブを開くだけで、まだ何も実行しない。塗りつぶしの青(btn-primary)は
               実際に変換・保存を走らせるボタンに取っておき、導線はbtn-secondaryでそろえる。 -->
          <div class="home-panel__actions">
            <button type="button" class="btn-secondary" @click="navigateTab('edit')">PDFをMarkdown下書き化</button>
            <button type="button" class="btn-secondary" @click="navigateTab('ocr')">画像をOCRしてMarkdownへ</button>
            <button type="button" class="btn-secondary" @click="navigateTab('memo')">MarkdownをPDF出力</button>
            <button type="button" class="btn-secondary" @click="navigateTab('merge')">PDFを結合</button>
            <button type="button" class="btn-secondary" @click="navigateTab('edit')">PDFをページ抽出</button>
          </div>
        </section>
        <section aria-label="最近保存したMarkdown">
          <p class="pdf-card__section-title">最近保存したMarkdown</p>
          <ul class="home-panel__recent-list" v-if="recentMarkdownFiles.length > 0">
            <li v-for="fileInfo in recentMarkdownFiles" :key="fileInfo.fileName">
              <button type="button" class="btn-secondary" :disabled="isProcessing"
                @click="openRecentMarkdown(fileInfo.fileName)">
                {{ fileInfo.fileName }}
              </button>
            </li>
          </ul>
          <p class="pdf-card__hint" v-else>保存済みのMarkdownはまだありません。</p>
        </section>
      </div>
    </article>
  `,
  methods: {
    /**
     * 指定した機能タブへの移動を親コンポーネントへ通知する。
     *
     * @param {string} tabName 移動先タブ名。"merge"はPDF編集タブの結合パネルを開く特別値
     */
    navigateTab(tabName) {
      this.$emit("navigate-tab", tabName);
    },
    /**
     * 最近保存したMarkdownを開く操作を親コンポーネントへ通知する。
     *
     * @param {string} fileName 開くMarkdownファイル名
     */
    openRecentMarkdown(fileName) {
      this.$emit("open-recent-markdown", fileName);
    },
  },
};
