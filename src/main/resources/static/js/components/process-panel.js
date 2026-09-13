import ProcessState from "../models/process-state.js";

/**
 * 実行中の操作と、その結果を1箇所へ出すVueコンポーネント。
 *
 * 状態ごとに「見出し」「説明」「次に取れる行動」の3点を必ず揃える。説明だけを出して
 * 利用者を止めないため、行動を持たない状態はこのコンポーネントでは表示しない。
 *
 * 想定外エラーは従来どおりモーダルが受け持つ。ここが受け持つのは、
 * 利用者が自分で直せる失敗（サイズ超過、パスワード保護）と、処理中・完了の表示。
 */
export default {
  name: "ProcessPanel",
  props: {
    panelState: { type: Object, required: true },
  },
  emits: ["start-over", "dismiss", "submit-password"],
  template: `
    <article class="process-panel" v-if="isVisible" :class="panelClass" aria-live="polite">
      <div class="process-panel__body">
        <p class="process-panel__title">{{ panelState.title }}</p>
        <p class="process-panel__detail" v-if="panelState.detail">{{ panelState.detail }}</p>
        <p class="process-panel__hint" v-if="panelState.hint">{{ panelState.hint }}</p>
        <p class="process-panel__elapsed" v-if="isProcessingState">
          経過 {{ panelState.elapsedSeconds }} 秒
        </p>
        <div class="process-panel__password" v-if="isPasswordRequiredState">
          <input type="password" v-model="panelState.password" autocomplete="off"
            aria-label="PDFを開くパスワード" placeholder="PDFを開くパスワード"
            @keyup.enter="submitPassword" />
          <button type="button" :disabled="!panelState.password" @click="submitPassword">
            PDFを開く
          </button>
        </div>
        <div class="process-panel__actions" v-if="isDoneState">
          <button type="button" @click="startOver">やり直す</button>
        </div>
        <div class="process-panel__actions" v-if="isDismissibleState">
          <button type="button" @click="dismiss">閉じる</button>
        </div>
        <div class="process-panel__actions" v-if="isPasswordRequiredState">
          <button type="button" @click="dismiss">別のファイルを選ぶ</button>
        </div>
      </div>
    </article>
  `,
  computed: {
    /**
     * パネルを表示するか判定する。
     *
     * @returns {boolean} 初期状態以外の場合はtrue
     */
    isVisible() {
      return this.panelState.state !== ProcessState.PROCESS_STATE.IDLE;
    },
    /**
     * 処理中かどうかを判定する。
     *
     * @returns {boolean} 処理中の場合はtrue
     */
    isProcessingState() {
      return this.panelState.state === ProcessState.PROCESS_STATE.PROCESSING;
    },
    /**
     * 完了状態かどうかを判定する。
     *
     * @returns {boolean} 完了状態の場合はtrue
     */
    isDoneState() {
      return this.panelState.state === ProcessState.PROCESS_STATE.DONE;
    },
    /**
     * パスワード待ちかどうかを判定する。
     *
     * @returns {boolean} パスワード待ちの場合はtrue
     */
    isPasswordRequiredState() {
      return (
        this.panelState.state === ProcessState.PROCESS_STATE.PASSWORD_REQUIRED
      );
    },
    /**
     * 閉じるだけで初期状態へ戻せる状態か判定する。
     *
     * 失敗は入力をクリアせずに閉じる。入力まで消すと、直して再実行する導線が切れるため。
     *
     * @returns {boolean} 閉じる操作を出す状態の場合はtrue
     */
    isDismissibleState() {
      return (
        this.panelState.state === ProcessState.PROCESS_STATE.NEEDS_ACTION ||
        this.panelState.state === ProcessState.PROCESS_STATE.ERROR
      );
    },
    /**
     * 状態に応じた見た目のclassを返す。
     *
     * @returns {string} 状態別のclass名
     */
    panelClass() {
      return "process-panel--" + this.panelState.state.toLowerCase();
    },
  },
  methods: {
    /**
     * 画面全体を初期化して次のファイルへ進む操作を親コンポーネントへ通知する。
     */
    startOver() {
      this.$emit("start-over");
    },
    /**
     * パネルを閉じる操作を親コンポーネントへ通知する。
     */
    dismiss() {
      this.$emit("dismiss");
    },
    /**
     * 入力されたパスワードで同じ操作をやり直すよう親コンポーネントへ通知する。
     *
     * パスワードの値はpropsのpanelStateが持っているため、ここでは渡さない。
     * 親が保持する値を1つに保ち、画面とアプリ状態で食い違わないようにする。
     */
    submitPassword() {
      if (!this.panelState.password) {
        return;
      }
      this.$emit("submit-password");
    },
  },
};
