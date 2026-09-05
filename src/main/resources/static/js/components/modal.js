/**
 * APIエラーや画面メッセージを表示するモーダルコンポーネント。
 */
export default {
  name: "Modal",
  props: {
    messages: { type: Array, required: true },
  },
  emits: ["close-modal"],
  template: `
    <dialog open>
      <article class="message-dialog">
        <template v-for="(message, index) in messages" :key="index">
          <p>{{ message }}</p>
        </template>
        <footer>
          <button type="button" @click="handleCloseModal()">閉じる</button>
        </footer>
      </article>
    </dialog>
  `,
  methods: {
    /**
     * モーダルを閉じる操作を親コンポーネントへ通知する。
     */
    handleCloseModal() {
      this.$emit("close-modal");
    },
  },
};
