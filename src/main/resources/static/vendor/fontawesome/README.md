# Font Awesome（vendor配置）

操作ボタン・アイコン表示に使う [Font Awesome Free](https://fontawesome.com/) の配布物。

| 項目 | 内容 |
| --- | --- |
| パッケージ | `@fortawesome/fontawesome-free` |
| バージョン | 5.3.1 |
| ライセンス | 混合（同階層の `LICENSE.txt`）。アイコン部分はCC BY 4.0、コード部分はMIT。
  `js/all.min.js` はコードにアイコンのSVGパスを含むJSファイルのため、CC BY 4.0の対象。 |

以前は `https://use.fontawesome.com/releases/v5.3.1/js/all.js` から読んでいた。このCDN配信物は
npmパッケージの `js/all.min.js` と内容が一致することをバイト単位で確認済み（ファイル名だけが
`all.js`）。vendor化にあたり、ファイル名を実体に合わせて `all.min.js` にした。

## CDNではなくvendor配置にしている理由

「ライブラリ追加判断」（[コーディング規約](../../../../../../../docs/coding-guidelines.md)）に従い、
Vue（`webjars`）・pdf.js / Pico CSS / SortableJS / vuedraggable（`static/vendor/`）と配信元をそろえる。
外部CDNが落ちても画面が使えなくなる状態を作らない。

## 収録しているもの

`npm pack @fortawesome/fontawesome-free@5.3.1` の展開結果から、実行に必要なものだけを置いている。

| パス | 用途 |
| --- | --- |
| `js/all.min.js` | 本体。`main.html` が読み込み、アイコン用SVGをJSで挿入する |

Webフォント（`webfonts/`）、CSS版（`css/`）、個別SVG（`svgs/`）、v4互換シム（`v4-shims.js`）は収録
しない。`main.html` はJS版（SVG + JS framework方式）のみを使っており、使わないものを配信対象へ
混ぜないため。

## 更新手順

```bash
npm pack @fortawesome/fontawesome-free@<新バージョン>
tar -xzf fortawesome-fontawesome-free-<新バージョン>.tgz -C <展開先> --strip-components=1
# js/all.min.js, LICENSE.txt を上書き
```

入れ替えたら、このREADMEのバージョンを同じ値へ直す。
