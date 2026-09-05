import PdfApp from "./pdf/pdf-app.js";

/**
 * PDF編集画面のVue appを起動する。
 *
 * 画面ロジックはpdf-app.jsへ集約し、main.jsはWebJar Vueでのbootstrapだけを担当する。
 */
Vue.createApp(PdfApp).mount("#app");
