/**
 * フッターに表示するリンク情報。
 *
 * @typedef {Object} FooterLink
 * @property {string} title 画面に表示するリンク名
 * @property {string} link 遷移先URL
 */

/**
 * 画面共通フッターコンポーネント。
 *
 * 現時点では固定リンクを表示するだけに留め、API通信や画面状態管理は持たない。
 */
export default {
  name: "TheFooter",
  data() {
    return {
      /** @type {FooterLink[]} */
      footerLinks: [
        { title: "プライバシーポリシー", link: "#" },
        { title: "お問い合わせ", link: "#" },
      ],
      copyrightText: "@ 2025 CLIP All rights reserved.",
    };
  },
  template: `
    <footer class="footer">
      <ul class="footer__list">
        <li v-for="footerLink in footerLinks" :key="footerLink.title">
          <a :href="footerLink.link" class="footer__link">{{ footerLink.title }}</a>
        </li>
      </ul>
      <p class="footer__copyright">{{ copyrightText }}</p>
    </footer>
  `,
};
