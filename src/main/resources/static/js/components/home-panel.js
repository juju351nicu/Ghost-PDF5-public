import FileDropZone from "./file-drop-zone.js";

/**
 * よく使う操作への導線をまとめたホームタブのVueコンポーネント。
 *
 * 機能タブを1つずつ探す代わりに、最初にここから目的の作業へ移動できるようにする。
 * ファイルを先に受け取り、拡張子から行き先の候補を出す導線もここへ置く。
 * 実際の操作（ファイル選択や実行）は移動先の各タブに残し、このコンポーネントはタブ遷移と
 * ファイル受け取り・操作候補の選択・最近保存したMarkdownを開く操作の通知に責務を限定する。
 */
export default {
  name: "HomePanel",
  components: {
    "file-drop-zone": FileDropZone,
  },
  props: {
    quickStartState: { type: Object, required: true },
    recentMarkdownFiles: { type: Array, required: true },
    recentPdfFiles: { type: Array, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: [
    "navigate-tab",
    "open-recent-markdown",
    "quick-file-selected",
    "quick-action",
    "clear-quick-start",
  ],
  template: `
    <article class="pdf-card home-panel">
      <header>ホーム</header>
      <div class="pdf-card__body home-panel__body">
        <section aria-label="ファイルから始める">
          <p class="pdf-card__section-title">ファイルから始める</p>
          <p class="pdf-card__hint">
            ファイルを渡すと、拡張子から次にできる操作を出します。
            操作を押すと、そのファイルを読み込んだ状態で担当タブへ移動します。
          </p>
          <file-drop-zone accept="*" label="ここにファイルをドロップ、またはファイルを選択"
            @files-dropped="handleFilesDropped">
            <div class="file-control">
              <input type="file" @change="handleFileChange($event)" />
            </div>
          </file-drop-zone>
          <div class="home-panel__quick-result" v-if="quickStartState.fileObject">
            <p class="home-panel__quick-file">
              {{ quickStartState.fileName }}（{{ quickStartKindLabel }}）
            </p>
            <div class="home-panel__actions" v-if="quickStartState.actionItems.length > 0">
              <button type="button" class="btn-secondary"
                v-for="actionItem in quickStartState.actionItems" :key="actionItem.id"
                :disabled="isProcessing" @click="selectQuickAction(actionItem.id)">
                {{ actionItem.name }}
              </button>
              <button type="button" class="btn-neutral" :disabled="isProcessing"
                @click="clearQuickStart">クリア</button>
            </div>
            <template v-else>
              <p class="pdf-card__hint">この拡張子に対応する操作はありません。</p>
              <div class="home-panel__actions">
                <button type="button" class="btn-neutral" :disabled="isProcessing"
                  @click="clearQuickStart">クリア</button>
              </div>
            </template>
          </div>
        </section>
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
        <section aria-label="最近使ったPDF">
          <p class="pdf-card__section-title">最近使ったPDF</p>
          <!-- ブラウザはFileにパスを持たせないため、名前からは開き直せない。押せるボタンにすると
               「押しても開かない」ことになるので、一覧は文字のまま出す。 -->
          <ul class="home-panel__recent-pdf-list" v-if="recentPdfFiles.length > 0">
            <li v-for="recentFile in recentPdfFiles" :key="recentFile.fileName">
              <span class="home-panel__recent-pdf-name">{{ recentFile.fileName }}</span>
              <span class="home-panel__recent-pdf-info">{{ formatRecentPdfInfo(recentFile) }}</span>
            </li>
          </ul>
          <p class="pdf-card__hint" v-else>読み込んだPDFはまだありません。</p>
          <p class="pdf-card__hint" v-if="recentPdfFiles.length > 0">
            ブラウザの制約により、ここから開き直すことはできません。同じPDFを使うときはファイル選択からもう一度指定してください。
          </p>
        </section>
      </div>
    </article>
  `,
  computed: {
    /**
     * 受け取ったファイルの種別を画面表示用の名前へ変換する。
     *
     * @returns {string} 画面表示用の種別名
     */
    quickStartKindLabel() {
      const kindLabels = {
        PDF: "PDF",
        IMAGE: "画像",
        MARKDOWN: "Markdown",
        OFFICE: "Office文書",
        HTML: "HTML",
        EPUB: "EPUB",
      };
      return kindLabels[this.quickStartState.kind] || "対応していない形式";
    },
  },
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
    /**
     * ファイル選択で選ばれたファイルを親コンポーネントへ通知する。
     *
     * 通知後は選択欄を空に戻す。受け取ったファイルは担当タブへ渡して手放すため、選択欄に名前が
     * 残っていると「まだここにある」と読める。加えて、値が残ったままだと同じファイルを選び直しても
     * changeが発火せず、操作候補が出ないまま何も起きない状態になる。
     *
     * @param {Event} event ファイル選択イベント
     */
    handleFileChange(event) {
      const file = event.target.files[0];
      if (file) {
        this.$emit("quick-file-selected", file);
      }
      event.target.value = "";
    },
    /**
     * ドロップされたファイルを親コンポーネントへ通知する。
     *
     * @param {File[]} files ドロップされたファイル
     */
    handleFilesDropped(files) {
      if (files.length > 0) {
        this.$emit("quick-file-selected", files[0]);
      }
    },
    /**
     * 選ばれた操作候補を親コンポーネントへ通知する。
     *
     * @param {string} actionId 操作候補の識別子
     */
    selectQuickAction(actionId) {
      this.$emit("quick-action", actionId);
    },
    /**
     * 受け取ったファイルの取り消しを親コンポーネントへ通知する。
     */
    clearQuickStart() {
      this.$emit("clear-quick-start");
    },
    /**
     * 最近使ったPDFのサイズと利用日時を1行へ整形する。
     *
     * 1MB未満をMB表記にすると「0.0MB」が並び、同じ名前のPDFを見分ける手がかりにならないためKBで出す。
     *
     * @param {{fileSize: number, lastUsedTime: string}} recentFile 履歴1件
     * @returns {string} 画面表示用の補足情報
     */
    formatRecentPdfInfo(recentFile) {
      const bytesPerKilobyte = 1024;
      const bytesPerMegabyte = bytesPerKilobyte * bytesPerKilobyte;
      const fileSize =
        recentFile.fileSize < bytesPerMegabyte
          ? Math.max(1, Math.round(recentFile.fileSize / bytesPerKilobyte)) + "KB"
          : (recentFile.fileSize / bytesPerMegabyte).toFixed(1) + "MB";
      const lastUsedTime = new Date(recentFile.lastUsedTime);
      return fileSize + " / " + lastUsedTime.toLocaleString("ja-JP");
    },
  },
};
