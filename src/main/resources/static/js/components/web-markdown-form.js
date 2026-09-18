import FileDropZone from "./file-drop-zone.js";

/**
 * WebページのHTMLからMarkdown下書きを起こすカードのVueコンポーネント。
 *
 * ファイル選択とドロップでHTMLを受け取り、選択中ファイル名とセレクタ入力の表示、
 * 実行・クリアのイベント通知に責務を限定する。API呼び出しは親のpdf-app.jsに残す。
 *
 * URL入力からの取り込みは、サーバーが利用者指定の宛先へ接続する機能のため既定で無効。
 * 無効のまま実行すると503が返り、共通のエラー表示で「機能が無効」と分かる。
 */
export default {
  name: "WebMarkdownForm",
  components: {
    "file-drop-zone": FileDropZone,
  },
  props: {
    webState: { type: Object, required: true },
    modeItems: { type: Array, required: true },
    isProcessing: { type: Boolean, required: true },
  },
  emits: [
    "html-selected",
    "request-web-markdown",
    "request-web-markdown-url",
    "clear-web-html",
    "update-selector",
    "update-url",
    "update-mode",
  ],
  template: `
    <article class="pdf-card web-markdown-card">
      <header>WebページからMarkdown</header>
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
          URLから取り込む（既定では無効）
          <input type="url" placeholder="https://example.com/article"
            :value="webState.url" @input="updateUrl($event)" />
        </label>
        <label class="web-markdown-card__selector">
          出力する内容
          <select :value="webState.mode" @change="updateMode($event)">
            <option v-for="item in modeItems" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select>
        </label>
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
          構造レポートは、メタ情報・見出しアウトライン・ランドマーク構成を事実のまま書き出します（評価は書きません）。
        </p>
        <div class="web-markdown-card__actions">
          <button type="button" :disabled="isProcessing || !webState.fileObject"
            @click="requestWebMarkdown">HTMLをMarkdownにする</button>
          <button type="button" :disabled="isProcessing || !webState.url"
            @click="requestWebMarkdownUrl">URLから取り込む</button>
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
     * 入力されたURLを親コンポーネントへ通知する。
     *
     * @param {Event} event 入力イベント
     */
    updateUrl(event) {
      this.$emit("update-url", event.target.value);
    },
    /**
     * 選択された出力モードを親コンポーネントへ通知する。
     *
     * @param {Event} event 選択イベント
     */
    updateMode(event) {
      this.$emit("update-mode", event.target.value);
    },
    /**
     * HTMLファイルからのMarkdown下書き生成を親コンポーネントへ通知する。
     */
    requestWebMarkdown() {
      this.$emit("request-web-markdown");
    },
    /**
     * URLからのMarkdown下書き生成を親コンポーネントへ通知する。
     */
    requestWebMarkdownUrl() {
      this.$emit("request-web-markdown-url");
    },
    /**
     * 選択HTMLのクリアを親コンポーネントへ通知する。
     */
    clearWebHtml() {
      this.$emit("clear-web-html");
    },
  },
};
