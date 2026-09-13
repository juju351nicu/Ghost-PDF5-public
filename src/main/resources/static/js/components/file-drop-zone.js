/**
 * ファイルのドラッグ&ドロップを受け取るVueコンポーネント。
 *
 * 既存の `<input type="file">` を置き換えず、その周囲を落とせる領域にする。
 * ファイル選択ダイアログ経由でしか渡せないと、エクスプローラから直接持ってこられない。
 *
 * 受け取ったFileは親コンポーネントへそのまま渡す。サイズ・拡張子の検証は親が
 * ファイル選択と同じ経路で行う。ドロップ経路だけ検証が緩い状態を作らないため。
 */
export default {
  name: "FileDropZone",
  props: {
    /** 受け付ける拡張子。表示と、ドロップ時の絞り込みに使う。 */
    accept: { type: String, required: true },
    /** 領域に出す説明文。 */
    label: { type: String, required: true },
    /** 複数ファイルのドロップを許可するか。 */
    multiple: { type: Boolean, default: false },
  },
  emits: ["files-dropped"],
  template: `
    <div class="file-drop-zone" :class="{ 'file-drop-zone--over': isDragOver }"
      @dragenter.prevent="handleDragEnter"
      @dragover.prevent="handleDragOver"
      @dragleave.prevent="handleDragLeave"
      @drop.prevent="handleDrop">
      <p class="file-drop-zone__label">{{ label }}</p>
      <slot></slot>
    </div>
  `,
  data() {
    return {
      // dragenterとdragleaveは子要素の出入りでも発火するため、入れ子の深さで数える。
      // booleanだけで持つと、子要素をまたいだ瞬間に枠の強調が点滅する。
      dragDepth: 0,
    };
  },
  computed: {
    /**
     * ドラッグ中の要素が領域内にあるか判定する。
     *
     * @returns {boolean} 領域内にある場合はtrue
     */
    isDragOver() {
      return this.dragDepth > 0;
    },
  },
  methods: {
    /**
     * ドラッグ要素が領域へ入ったときに深さを増やす。
     */
    handleDragEnter() {
      this.dragDepth = this.dragDepth + 1;
    },
    /**
     * ドラッグ中の既定動作を止める。
     *
     * dragoverでpreventDefaultを呼ばないとdropが発火しない。
     *
     * @param {DragEvent} event ドラッグイベント
     */
    handleDragOver(event) {
      if (event.dataTransfer) {
        event.dataTransfer.dropEffect = "copy";
      }
    },
    /**
     * ドラッグ要素が領域から出たときに深さを減らす。
     */
    handleDragLeave() {
      this.dragDepth = Math.max(0, this.dragDepth - 1);
    },
    /**
     * ドロップされたファイルを親コンポーネントへ渡す。
     *
     * @param {DragEvent} event ドロップイベント
     */
    handleDrop(event) {
      this.dragDepth = 0;
      const droppedFiles = Array.from(event.dataTransfer?.files ?? []);
      if (droppedFiles.length === 0) {
        return;
      }
      // 複数を落とされても、複数受付でなければ先頭だけを使う。黙って全部読み込むと、
      // どれが処理対象になったのか利用者から分からなくなる。
      const targetFiles = this.multiple ? droppedFiles : [droppedFiles[0]];
      this.$emit("files-dropped", targetFiles);
    },
  },
};
