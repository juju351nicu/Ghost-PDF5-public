# pdf.js（vendor配置）

ページ選択用サムネイルをブラウザ内で描画するために使う [pdf.js](https://github.com/mozilla/pdf.js) の配布物。

| 項目 | 内容 |
| --- | --- |
| パッケージ | `pdfjs-dist` |
| バージョン | 6.3.289 |
| ライセンス | Apache-2.0（同階層の `LICENSE`） |

## CDNではなくvendor配置にしている理由

- サムネイル描画はローカルのPDFを読む処理で、外部への通信を挟む理由が無い。CDNが落ちるとページ選択が
  使えなくなるだけで、得るものが無い。
- 日本語PDFの表示には `cmaps/`（CID フォントの CMap）と `standard_fonts/`（未埋め込みフォントの代替）が要る。
  CDN運用ではこの2つも同じバージョンで指し続ける必要があり、`build/` だけ差し替えると静かに文字化けする。
- Vueを `webjars` から配信している既存方針と、配信元をそろえる。

## 収録しているもの

`npm pack pdfjs-dist@6.3.289` の展開結果から、実行に必要なものだけを置いている。

| パス | 用途 |
| --- | --- |
| `build/pdf.min.mjs` | 本体。`pdf-thumbnail-renderer.js` が動的importで読む |
| `build/pdf.worker.min.mjs` | 解析処理のworker。本体と同じバージョンでなければ動かない |
| `cmaps/` | 日本語などのCIDフォントを持つPDFの文字対応表 |
| `standard_fonts/` | フォントを埋め込んでいないPDFの代替フォント |

sourcemap（`*.mjs.map`）と非minify版、`pdf.sandbox.mjs`（PDFフォームのJavaScript実行用）は収録しない。
サムネイル描画では使わず、リポジトリサイズだけが増えるため。

型定義（`types/`）もここへは置かない。実行に使わないものを配信対象へ混ぜないため。APIを確認するための
`api.d.ts` / `pdf.d.ts` は `docs/reference/pdfjs-6.3.289/` にある。

## 更新手順

バージョンを上げるときは `build/` だけを差し替えない。`cmaps/` と `standard_fonts/` の中身もバージョンに
追随するため、4つまとめて入れ替える。

```bash
npm pack pdfjs-dist@<新バージョン>
tar -xzf pdfjs-dist-<新バージョン>.tgz
# build/pdf.min.mjs, build/pdf.worker.min.mjs, cmaps/, standard_fonts/, LICENSE を上書き
```

入れ替えたら、このREADMEのバージョンと `pdf-thumbnail-renderer.js` の `PDFJS_VERSION` を同じ値へ直す。
**参照用の型定義 `docs/reference/pdfjs-<バージョン>/` も作り直し、古い版のディレクトリを消す。**
`FrontendThumbnailContractTest` が3者の一致を検証しているため、どれか1つでも直し忘れるとテストで落ちる。
