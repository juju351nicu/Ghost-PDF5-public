# pdf.js 型定義（参照用・6.3.289）

`static/vendor/pdfjs/` へ同梱している pdf.js のAPI定義。**実行には使わない。**
AIエージェントや開発者が「このバージョンのAPIが実際にどうなっているか」を、npmから取り直さずに
確認するために置いている。

| ファイル | 中身 |
| --- | --- |
| `api.d.ts` | `pdfjs-dist` の `types/src/display/api.d.ts`。`getDocument` / `PDFDocumentLoadingTask` / `PDFDocumentProxy` / `PDFPageProxy` / `RenderParameters` などの定義 |
| `pdf.d.ts` | `pdfjs-dist` の `types/src/pdf.d.ts`。モジュールがexportするものの一覧 |

ライセンスは pdf.js 本体と同じ Apache-2.0（`../../../src/main/resources/static/vendor/pdfjs/LICENSE`）。

## なぜ `static/` の下に置かないか

`src/main/resources/static/` 配下はすべてWebへ配信される。型定義は実行時に要らないため、
配信対象へ混ぜない。

## なぜディレクトリ名にバージョンを入れるか

**古い型定義は、型定義が無いことより危険なため。** 置いてあるものは正しいと見なして読まれるので、
pdf.js本体だけ更新して型定義を置き忘れると、誤った情報が確信をもって使われる。

ディレクトリ名を `pdfjs-<バージョン>` にしておけば、本体との食い違いを機械的に検知できる。
`FrontendThumbnailContractTest.pdfjsVersionIsRecordedConsistently` が
`pdf-thumbnail-renderer.js` の `PDFJS_VERSION` とこのディレクトリ名の一致を検証する。

## 期待してよい効果の範囲

調査を速くする補助であって、事故を防ぐ主役ではない。ここに置いてあっても、実装前に読まれるとは
限らない（実際、今回の実装では `pdfDocument.destroy()` を記憶で書いてから、実機で失敗して初めて
`api.d.ts` を読んだ）。**再発防止は `docs/coding-guidelines.md` の「pdf.js実装時の注意」が担う。**

## 更新手順

pdf.js本体を上げるときは、`static/vendor/pdfjs/README.md` の手順に沿って本体を差し替えたうえで、
このディレクトリも作り直す。

```bash
npm pack pdfjs-dist@<新バージョン>
tar -xzf pdfjs-dist-<新バージョン>.tgz
# docs/reference/pdfjs-<新バージョン>/ を作り、package/types/src/display/api.d.ts と
# package/types/src/pdf.d.ts をコピーする。このREADMEも一緒に移して版数を直す
# 古い pdfjs-<旧バージョン>/ は消す
```
