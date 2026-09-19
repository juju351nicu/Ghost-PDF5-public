# vuedraggable（vendor配置）

ページ並べ替えのドラッグ&ドロップに使う [vue.draggable.next](https://github.com/SortableJS/vue.draggable.next)
（パッケージ名は `vuedraggable`）の配布物。Vue 3向けで、内部で `../sortablejs/`（SortableJS）を使う。

| 項目 | 内容 |
| --- | --- |
| パッケージ | `vuedraggable` |
| バージョン | 4.0.2 |
| ライセンス | MIT（同階層の `LICENSE`） |

## CDNではなくvendor配置にしている理由

「ライブラリ追加判断」（[コーディング規約](../../../../../../../docs/coding-guidelines.md)）に従い、
Vue（`webjars`）・pdf.js（`static/vendor/pdfjs/`）と配信元をそろえる。外部CDNが落ちても画面が
使えなくなる状態を作らない。

## 収録しているもの

`npm pack vuedraggable@4.0.2` の展開結果から、実行に必要なものだけを置いている。

| パス | 用途 |
| --- | --- |
| `dist/vuedraggable.umd.min.js` | 本体。`main.html` がグローバル変数として読み込む |

sourcemapや非minify版、CommonJS版（`vuedraggable.common.js`）は収録しない。`main.html` はscriptタグ
での読み込みのみで、使わないものを配信対象へ混ぜないため。

## 更新手順

```bash
npm pack vuedraggable@<新バージョン>
tar -xzf vuedraggable-<新バージョン>.tgz -C <展開先> --strip-components=1
# dist/vuedraggable.umd.min.js, LICENSE を上書き
```

Vue本体（`webjars` の `vue.global.js`）・`../sortablejs/` のバージョンと組み合わせ動作を前提にしている
ため、単独で更新した後は画面の並べ替え動作を必ず確認する。入れ替えたら、このREADMEのバージョンを
同じ値へ直す。
