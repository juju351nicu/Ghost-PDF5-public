# Sortable.js（vendor配置）

ページ並べ替えのドラッグ&ドロップに使う [SortableJS](https://github.com/SortableJS/Sortable) の配布物。
`vuedraggable`（同階層 `../vuedraggable/`）が内部で使う依存ライブラリで、`main.html` から先に
読み込んでおく必要がある。

| 項目 | 内容 |
| --- | --- |
| パッケージ | `sortablejs` |
| バージョン | 1.10.2 |
| ライセンス | MIT（同階層の `LICENSE`） |

## CDNではなくvendor配置にしている理由

「ライブラリ追加判断」（[コーディング規約](../../../../../../../docs/coding-guidelines.md)）に従い、
Vue（`webjars`）・pdf.js（`static/vendor/pdfjs/`）と配信元をそろえる。外部CDNが落ちても画面が
使えなくなる状態を作らない。

## 収録しているもの

`npm pack sortablejs@1.10.2` の展開結果から、実行に必要なものだけを置いている。

| パス | 用途 |
| --- | --- |
| `Sortable.min.js` | 本体。`main.html` がグローバル変数として読み込む |

sourcemapや非minify版、ESM版（`modular/`）は収録しない。`main.html` はscriptタグでの読み込みのみで、
使わないものを配信対象へ混ぜないため。

## 更新手順

```bash
npm pack sortablejs@<新バージョン>
tar -xzf sortablejs-<新バージョン>.tgz -C <展開先> --strip-components=1
# Sortable.min.js, LICENSE を上書き
```

`vuedraggable` が対応するSortableJSのバージョン範囲を前提にしているため、`vuedraggable` を更新しない
まま単独でメジャーバージョンを上げない。入れ替えたら、このREADMEのバージョンを同じ値へ直す。
