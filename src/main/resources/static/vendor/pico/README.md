# Pico CSS（vendor配置）

画面全体の配色・レイアウトの土台に使う [Pico CSS](https://github.com/picocss/pico) の配布物。

| 項目 | 内容 |
| --- | --- |
| パッケージ | `@picocss/pico` |
| バージョン | 2.1.1 |
| ライセンス | MIT（同階層の `LICENSE.md`） |

以前は `https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css` からメジャーバージョン
固定（`@2`）で読んでいた。vendor化にあたり、取得時点でこのURLが配信していた `2.1.1` へ固定する。
floatingのままでは、CDN側の更新でアプリの見た目が意図せず変わる。

## CDNではなくvendor配置にしている理由

「ライブラリ追加判断」（[コーディング規約](../../../../../../../docs/coding-guidelines.md)）に従い、
Vue（`webjars`）・pdf.js（`static/vendor/pdfjs/`）と配信元をそろえる。外部CDNが落ちても画面が
使えなくなる状態を作らない。

## 収録しているもの

`npm pack @picocss/pico@2.1.1` の展開結果から、実行に必要なものだけを置いている。

| パス | 用途 |
| --- | --- |
| `css/pico.min.css` | 本体。`main.html` が読む既定配色版 |

配色バリエーション（`pico.amber.css` など）やSCSSソースは収録しない。使っていないものを配信対象へ
混ぜないため。

## 更新手順

```bash
npm pack @picocss/pico@<新バージョン>
tar -xzf picocss-pico-<新バージョン>.tgz -C <展開先> --strip-components=1
# css/pico.min.css, LICENSE.md を上書き
```

入れ替えたら、このREADMEのバージョンを同じ値へ直す。
