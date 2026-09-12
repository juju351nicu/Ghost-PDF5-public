# MarkdownからのPDF出力API設計

作成日: 2026-09-13
状態: 実装完了（2026-09-13）。`POST /markdownPdf`、日本語フォント同梱、表・コードブロック・ページ番号まで実測確認済み。
位置づけ: `docs/future-document-ai-roadmap.md` の Phase D「Markdown編集 / Markdown to PDF」の後半。

## 1. 目的

画面のMarkdown欄で編集した設計書を、そのままPDFとして配布できるようにする。
保存済みかどうかに関わらず、**編集中の本文**をPDFにする。保存を挟まずに見た目を確認できることを優先する。

PDFを保存しないのは既存のPDF操作APIと同じ方針で、生成物の置き場所を増やさないため。

## 2. API概要

| 項目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/markdownPdf` |
| Content-Type | `application/json` |
| Response Content-Type | `application/pdf`（`Content-Disposition: attachment`） |
| 認証相当 | 既存と同じ `access-token` header と session token の照合 |
| 保存 | 行わない |

Request:

```json
{ "fileName": "design-note.md", "content": "# 設計書\n\n本文" }
```

- `content` は必須（未指定は400）。
- `fileName` は任意。拡張子を `.pdf` へそろえ、パス区切りや制御文字は `_` へ置き換える。未指定時は `document.pdf`。
  ファイル名は `Content-Disposition` にそのまま出るため、正規化はBE側に寄せる。

`GET` で保存済みファイルを直接PDF化するAPIは作らない。画面は「読込 → PDF出力」で同じことができ、
保存済みと入力中で処理を分けると、同じMarkdownでも経路によって出力が変わる余地が生まれるため。

## 3. 変換の流れ

```text
Markdown
  -> MarkdownHtmlRenderer   commonmark（GFM表拡張）でHTML化し、jsoupでsanitize
  -> MarkdownPdfRenderer    XHTML（CSS埋め込み）へ整形し、openhtmltopdfでPDFへ描画
  -> MarkdownPdfService     ファイル名の正規化とダウンロードレスポンス組み立て
```

### HTML変換は画面プレビューと共有する

`MarkdownHtmlRenderer`（`markdowncontent.logic`）を新設し、`MarkdownDocumentService` の
プレビュー変換もこのクラスへ寄せた。変換規則が2箇所に分かれると「プレビューでは表になるのにPDFでは崩れる」
という差が後から効いてくる。sanitizeのSafelistも1箇所に持つ。

### XHTMLの組み立て

openhtmltopdfは入力をXMLとして解析するため、HTMLのままでは `<br>` のような空要素で整形式エラーになる。
jsoupで `Syntax.xml` として出力し直してから、CSSを `<style>` へ埋め込む。CSSは **CDATAで包む**。
包まないとCSS中の記号やコメントがXMLの解析エラーになる（実装時に実際に踏んだ）。

CSSは `src/main/resources/markdown-pdf.css` に置き、Javaコード側にスタイルを書かない。

## 4. ライブラリ選定

| 案 | 評価 |
| --- | --- |
| **openhtmltopdf（採用）** | `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.85`。PDFBox **3.0.7** に依存し、本プロジェクトと同一バージョンで衝突しない。CSS 2.1相当でレイアウトを制御でき、表の改ページやページ番号も扱える |
| PDFBoxだけで自前レンダリング | 依存は増えないが、行送り・折り返し・表の列幅・改ページをすべて自前実装することになる。規約の「独自実装を増やさない」と逆方向 |
| Flying Saucer / OpenPDF系 | `com.lowagie`（OpenPDF）へ依存する。`CodingConventionTest` でOpenPDFへの依存を禁止しているため対象外 |
| 外部プロセス（Pandoc等） | 実行環境に外部バイナリを要求する。`ProcessBuilder` は `ProcessCommandRunner` に限定しており、用途も広がりすぎる |

ライセンスは **LGPL 2.1+**。jarを改変せず依存として利用するため、本体（Apache-2.0）の配布条件には影響しない。
将来、配布形態を変える場合はここを再確認する。

openhtmltopdfへの依存は `MarkdownPdfRenderer` だけに閉じ込め、`CodingConventionTest` の
`htmlToPdfRendererIsLimitedToMarkdownPdfRenderer` でソーススキャンして固定する。

## 5. 日本語フォント

PDFBoxの標準14フォントは日本語を描画できないため、フォントの埋め込みが必須になる。

- 既定は同梱の **Noto Sans JP**（`src/main/resources/fonts/NotoSansJP-Regular.ttf`、SIL OFL 1.1、約2.3MB）。
  Google Fontsの日本語サブセット版で、ラテン文字・かな・常用漢字を含む静的TrueType。
  ライセンス全文は `src/main/resources/fonts/OFL.txt` に同梱する。
- 差し替えは `ghost.markdown.pdf.font-path`（本文）と `ghost.markdown.pdf.bold-font-path`（太字）。
- 太字フォント未指定時は本文フォントを太字としても登録する。字形は太くならないが、
  登録が無いと太字指定の箇所だけ描画できなくなるため、読める状態を優先する。
- 設定したフォントを読めない場合は**同梱フォントへ黙って戻さず**500で止める。
  黙って代替すると、出力が指定と違う理由に気付けない。

`code` / `pre` はブラウザ既定で `monospace` になる。等幅の日本語フォントは同梱していないため、
CSSで `font-family: "Noto Sans JP", monospace` を明示する。指定を忘れると**コード内の日本語だけが豆腐になる**
（実装時に実際に踏んだ。`MarkdownPdfRendererTest` で固定している）。

## 6. レイアウト（markdown-pdf.css）

- A4、余白 18/16/20/16 mm。
- ページ番号は `@page` の `@bottom-center` に `counter(page) " / " counter(pages)`。
- 表は `-fs-table-paginate: paginate` でページ跨ぎを許可し、`tr` は `page-break-inside: avoid`。
- 見出しは `page-break-after: avoid` で、見出しだけがページ末尾に残らないようにする。
- 日本語は単語区切りが無いため `word-wrap: break-word` を本文と `pre` に指定する。

## 7. セキュリティ

- 入力は既存プレビューと同じsanitizeを通す（jsoup `Safelist.relaxed` + 表の `align`）。
- **外部リソースは取得しない。** `FSUriResolver` で `data:` 以外のURIを解決せず、`http` / `https` / `file` は無視する。
  Markdown本文に外部URLの画像が書かれていても、サーバーがそのURLへアクセスしない（SSRF対策）。
- ログに本文・ファイル内容・ローカルパスを出さない。出力サイズだけをinfoで残す。
- 失敗時のレスポンスは共通の `fieldErrors` 形式で、原因の詳細（本文やフォントのパス）を含めない。

## 8. HTTP status

| Status | 条件 | レスポンス |
| --- | --- | --- |
| `200 OK` | 生成成功 | PDFバイナリ（`attachment`） |
| `400 Bad Request` | `content` 未指定 | `ErrorResponse` |
| `403 Forbidden` | token不一致 | 既存仕様 |
| `500 Internal Server Error` | 描画失敗、フォント読み込み失敗 | `ErrorResponse`（`markdownPdfError`） |

## 9. クラス配置

```text
com.clip.ghost.markdowncontent
  controller.MarkdownPdfController   -> JSON受付, token検証, OpenAPI, Service委譲
  service.MarkdownPdfService         -> ファイル名正規化, レスポンス組み立て
  logic.MarkdownHtmlRenderer         -> Markdown -> sanitize済みHTML（プレビューと共有）
  logic.MarkdownPdfRenderer          -> HTML -> PDF（openhtmltopdfはここだけ）
  dto.MarkdownPdfRequest
  config.MarkdownPdfProperties       -> ghost.markdown.pdf.font-path / bold-font-path
  exception.MarkdownPdfException     -> PDF出力失敗(500)
```

依存方向はController → Service → Logicで、`CodingConventionTest` の
`markdownControllerServiceLogicDependenciesKeepDirection` で固定する。

Controllerを `MarkdownController` へ相乗りさせないのは、レスポンスがJSONではなくPDFバイナリで、
扱うヘッダーもエラーの見え方も違うため。

## 10. フロントエンド

- Markdownパネルのツールバーへ「PDF出力」ボタンを追加。押すと編集中の本文とファイル名を送り、ダウンロードする。
- HTTPは `fetch-client.js` 経由。既定ヘッダーが `Accept: application/json` のため、
  そのままではPDFを返すAPIが**406**になる。`postRequestForFile(uri, data, acceptMediaType)` を追加して
  受け取るmedia typeを明示する（実装時に実際に406を踏んだ）。
- 保存は `file-response-handler.js` の `downloadBlob` に寄せる。Object URLと `<a>` の扱いを1箇所に閉じる。
- ファイル名は `Content-Disposition` の `filename*=UTF-8''` を優先して使う。日本語ファイル名もそのまま保存できる。

## 11. テスト

- `MarkdownPdfRendererTest`: 生成したPDFをPDFBoxで読み直し、日本語 / 表のセル / コード内の日本語 /
  ページ番号 / 複数ページ / 空本文 / 外部URL画像を取りに行かないこと / 設定フォントの成否を確認する。
- `MarkdownPdfServiceTest`: 拡張子の正規化、既定ファイル名、パス区切りの除去、`Content-Disposition`、空本文。
- `MarkdownPdfControllerTest`: token一致/不一致(403)、`content` 未指定(400)、描画失敗(500)、Content-Type。
- `OpenApiDocumentationTest`: `/markdownPdf` の公開、JSON request、`application/pdf` の200、400 / 500。
- `FrontendMarkdownPdfContractTest`: ボタン → app → API client → fetch-client → downloadBlob の接続。
- `CodingConventionTest`: markdowncontentの層依存、openhtmltopdfの利用箇所限定。

実際の見た目（表の罫線、コードブロックの背景、ページ番号）は、生成PDFを `POST /thumbnailsPdf` で画像化して目視確認した。

## 12. やっていないこと

- 目次（TOC）とヘッダー/フッターのカスタマイズ。必要になった段階で `@page` へ足す。
- 画像の埋め込み。外部URLは取得しない方針のため、data URIのみ通る。ローカル画像を扱うなら別途設計する。
- ページサイズ・余白の設定化。まずA4固定で運用し、要望が出たら `ghost.markdown.pdf` へ足す。
- Word / 他形式への出力（Phase G）。
