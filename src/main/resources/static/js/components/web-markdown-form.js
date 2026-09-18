import FileDropZone from "./file-drop-zone.js";

/**
 * WebページのHTMLからMarkdown下書きを起こすカードのVueコンポーネント。
 *
 * ファイル選択とドロップでHTMLを受け取り、選択中ファイル名とセレクタ入力の表示、
 * 実行・クリアのイベント通知に責務を限定する。API呼び出しは親のpdf-app.jsに残す。
 *
 * URL入力欄は持たない。サーバーが任意の宛先へ接続する機能は別段階で追加する。
 */
export default {
  name: "WebMarkdownForm",
  components: {
    "file-drop-zone": FileDropZone,
  },
  props: {
    webState: { type: Object, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: ["html-selected", "request-web-markdown", "clear-web-html", "update-selector"],
  template: `
    <article class="pdf-card web-markdown-card">
      <header>WebページからMarkdown（HTMLファイル）</header>
      <div class="pdf-card__body web-markdown-card__body">
        <file-drop-zone accept=".html,.htm" label="ここにHTMLをドロップ、またはファイルを選択"
          @files-dropped="handleFilesDropped">
          <div class="file-control">
            <input type="file" accept=".html,.htm,text/html" @change="handleFileChange($event)" />
          </div>
        </file-drop-zone>
        <p class="web-markdown-card__selected" v-if="webState.fileObject">
          {{ webState.fileName }}
        </p>
        <label class="web-markdown-card__selector">
          本文の絞り込み（任意）
          <input type="text" placeholder="article / main / #content など" maxlength="200"
            :value="webState.selector" @input="updateSelector($event)" />
        </label>
        <p class="web-markdown-card__notice">
          公開WebページをMarkdownメモの下書きにします。
          ログインが必要なページ、有料記事、アクセス制限されたページ、利用規約で取得を禁じているページには使用しないでください。
          取得した内容の権利は取得元にあります。自分が読むための整理以外の用途に使わないでください。
        </p>
        <p class="web-markdown-card__notice">
          ブラウザで開いたページを「名前を付けて保存」したHTMLを指定します。画像は参照だけを残し、取得しません。
        </p>
        <div class="web-markdown-card__actions">
          <button type="button" :disabled="isProcessing || !webState.fileObject"
            @click="requestWebMarkdown">Markdownにする</button>
          <button type="button" class="secondary" :disabled="isProcessing" @click="clearWebHtml">クリア</button>
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
     * 入力されたセレクタを親コンポーネントへ通知する。
     *
     * @param {Event} event 入力イベント
     */
    updateSelector(event) {
      this.$emit("update-selector", event.target.value);
    },
    /**
     * Markdown下書きの生成を親コンポーネントへ通知する。
     */
    requestWebMarkdown() {
      this.$emit("request-web-markdown");
    },
    /**
     * 選択HTMLのクリアを親コンポーネントへ通知する。
     */
    clearWebHtml() {
      this.$emit("clear-web-html");
    },
  },
};
