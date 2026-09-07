/**
 * 成功レスポンスの通知メッセージをインライン表示するコンポーネント。
 *
 * エラーはモーダル（`components/modal.js`）で操作を止めて伝えるが、こちらは成功時の注意喚起なので
 * 操作を止めない。処理はすでに終わっており、閉じる操作を求めても利用者にできることが増えないため。
 * どのメッセージを出すかはBEが決め、画面側は受け取った順に並べるだけにする。
 * これにより注意喚起を返すAPIが増えても、このコンポーネントを触らずに済む。
 * 成功レスポンスの構造の解釈は `api/api-result-utils.js` に閉じているため、ここでは扱わない。
 */
export default {
  name: "ApiMessageList",
  props: {
    messages: { type: Array, required: true },
  },
  template: `
    <div class="api-message-list" v-if="messages.length > 0" role="status" aria-live="polite">
      <p class="api-message-list__item" v-for="(message, index) in messages" :key="message.code + '-' + index">
        {{ message.message }}
      </p>
    </div>
  `,
};
