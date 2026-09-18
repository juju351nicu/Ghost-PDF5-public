# 画面の処理状態設計

対象: `static/js/models/process-state.js` / `components/process-panel.js` /
`components/file-drop-zone.js` / `pdf/pdf-app.js`

---

## 1. 目的

「今なにが起きているか」と「次に何をすればよいか」を画面へ出す。

## 2. 何が問題だったか

画面状態は `isProcessing` のboolean 1つだけで、処理中はボタンが `disabled` になる以外に
何も伝わらなかった。

- `mode=VISION` のMarkdown下書きは外部API呼び出しがページ数だけ走る。
  `ghost.ocr.pdf.max-pages` の既定20ページなら分単位で無反応に見え、
  「止まった」と判断して再読み込みされると、それまでのAPI呼び出しがまるごと無駄になる。
- 失敗はすべて同じモーダルへ流れていた。利用者が自分で直せる失敗（サイズ超過、パスワード保護、
  ページ上限超過）も、サーバー側の想定外エラーも、見た目が同じで区別が付かない。
- 処理が終わっても画面がそのままで、成功したかどうかがブラウザのダウンロード表示でしか分からない。
  連続して別のPDFを扱うとき、前のファイルの入力が残る。

## 3. 状態

| 状態 | 意味 | 出す行動 |
| --- | --- | --- |
| `IDLE` | 初期。パネルを出さない | - |
| `PROCESSING` | 送信からBE処理完了まで | 何を待っているかの文言と経過秒 |
| `DONE` | 成功 | やり直す（画面を初期化して次のファイルへ） |
| `NEEDS_ACTION` | 利用者が自分で直せる失敗 | 直し方の説明と、閉じる |
| `PASSWORD_REQUIRED` | パスワード待ち | パスワード入力欄と、別のファイルを選ぶ |
| `ERROR` | 利用者が直せない失敗 | 閉じる |

**状態は「次に取れる行動が1つ以上あるもの」だけを置く。行動が同じ状態は分けない。**

- 「送信中」と「処理中」を分けていない。進捗率を取っていない現状では、2つを分けても
  画面の出しわけも次の行動も変わらない。local-firstで相手はlocalhostのため、送信は実質一瞬で終わる。
- サイズ超過と拡張子違いは `NEEDS_ACTION` にまとめている。どちらも
  「何が起きたか + 直し方 + 閉じる」で画面の形が同じ。
- `PASSWORD_REQUIRED` だけは分けた。他の `NEEDS_ACTION` は「読んで別のファイルを用意する」で
  終わるが、パスワードは「その場で入力して同じ操作をやり直す」という別の行動になる。
  出す部品（入力欄と実行ボタン）も違う。

どの状態にも「見出し / 説明 / 次の行動」を揃える。説明だけで終わる状態は作らない。

## 4. 失敗の2分類

振り分けはBEのerrorCodeで行う。メッセージ文字列には依存しない。

- **利用者が直せる失敗** → パネル（`NEEDS_ACTION` / `PASSWORD_REQUIRED`）。
  `multipartError` / `pdfPasswordProtected` / `pdfPasswordIncorrect` / `pdfPageLimitExceeded` /
  `pdfSplitRangeOutOfBounds` / `imageInputError` / `ocrUnavailable`
- **直せない失敗** → 従来どおりモーダル。`pdfProcessingError` / `imageProcessingError` /
  `markdownPdfError`

errorCodeの定義と判定は `api/api-error-utils.js` へ閉じる。`fieldErrors` の解釈を
他のファイルへ散らさない既存方針（`CodingConventionTest`）に合わせている。
BE側でerrorCodeを増やしたときにFEの定義を足し忘れると、そのエラーは判定から静かに漏れる。
`FrontendProcessStateContractTest` がBE側の定数を読んで突き合わせ、追加漏れを落とす。

## 5. `isProcessing` の互換

`original-pdf-form` / `insert-pdf-row` / `image-ocr-form` / `pdf-thumbnail-list` は
`is-processing` propsを受け取っている。この契約を壊さないよう、`isProcessing` は
`computed` として状態から導き、同じ名前で公開し続ける。

`pdf-app.js` 側では `this.isProcessing = true` のような直接代入を禁止し、
`beginProcess` / `finishProcess` / `failProcess` / `blockProcess` / `resetProcess` を通す。
直接代入が残っていないことは契約テストで確認する。

`finally` では `endProcessIfBusy()` を呼ぶ。成功・失敗の分岐で状態を決めた後の保険で、
分岐の中で例外が起きても操作できないまま固まらないようにする。

## 6. ドラッグ&ドロップ

`components/file-drop-zone.js` が既存の `<input type="file">` を囲う。入力欄は置き換えない。

- **検証経路を1本にする。** ファイル選択もドロップも `pdf-app.js` の `applyPdfFile` を通る。
  ドロップ経路だけ上限超過や拡張子違いを素通りさせない。契約テストで固定している。
- **拡張子を検証する。** ファイル選択は `accept=".pdf"` で絞れるが、ドロップは何でも渡せる。
  `validation/file-type-validator.js` で判定する。BE側の受け入れ判定
  （`PdfTemporaryFileStorage`）も拡張子で行っており、条件をそろえている。
- **領域外へのドロップを止める。** 既定動作のままだとブラウザがそのPDFを開いて画面を離れ、
  入力中の内容が失われる。`window` の `dragover` / `drop` で既定動作だけを抑止し、
  `beforeUnmount` で解除する。
- `dragenter` / `dragleave` は子要素の出入りでも発火するため、入れ子の深さで数える。
  booleanだけで持つと枠の強調が点滅する。

## 7. テスト

`FrontendProcessStateContractTest` がソーススキャンで次を固定する。
JavaScriptのテストランナーを持たない構成のため、規約テストと同じ方式を取る。

- BEのerrorCodeをFEがすべて把握しているか
- `isProcessing` へ直接代入していないか、状態が定義されているか
- ドロップとファイル選択が同じ検証を通るか
- 領域外ドロップでページが離脱しないか
- 保護されたPDFをパスワード入力でやり直せるか
- 入力されたパスワードを保存せず、別のファイルへ持ち越さないか

## 8. やっていないこと

- **アップロード進捗率（%）。** `api/fetch-client.js` は `fetch` を使っており、
  `fetch` はリクエストボディの送信進捗を取れない。取るには `XMLHttpRequest` へ差し替えるか、
  `ReadableStream` のリクエストボディ（Chrome系のみ、HTTP/2必須）を使うことになる。
  local-firstでサーバーはlocalhostのため送信フェーズは体感できる長さにならず、待ち時間の実体は
  BE処理側（特にVISIONの外部API呼び出し）にある。費用対効果が合わないため、
  処理フェーズの文言と経過秒に留めた。リモート配置を検討する段階で `XMLHttpRequest` 版を足す。
- **ページ単位の進捗（「20ページ中7ページ目」）。** SSEかポーリングが要る。
  アップロードしたPDFのサーバー保持（`documentId` 構想）が入ってからでないと器が無い。
  当面は `ghost.ocr.pdf.max-pages` から見込み時間を示す方が安い。
- **差し込みPDF行（`insert-pdf-row`）のドラッグ&ドロップ。** `file-drop-zone` は `multiple`
  propsを持たせてあり、部品は揃っている。編集元PDFで使い勝手を確かめてから広げる。
- **アップロード上限（1ファイル20MB）の見直し。** 超過時のメッセージは
  「ページを分割してから指定してください」だったが、この画面の分割も同じ上限を通るため
  実行できない指示だった。文言は実行できる行動へ直したが、上限値自体は変えていない。
  変えるなら `UploadConstants.MAX_UPLOAD_FILE_SIZE_BYTES` / `const.js` / `application.yml` の
  `max-file-size` と `max-swallow-size` を揃えて動かし、`/splitPdf` のメモリ実測とセットで判断する。
  値だけ上げると、上限超過時にアプリの413が返らずTomcatが接続を切る側へ倒れる。
- **1画面1目的への分割（タブ / ルーティング）。** `main.html` に全機能が縦積みのままで、
  目的から入れない。`README.md` が「`main.html` 1画面で扱える規模の間はViteへ急いで移行しない」
  としているため、URL分割はVite移行の判断とセットで扱う。先にタブ切り替えで分け方を確かめる。
- **成功時のパネル表示を全操作へ広げること。** `DONE` を出すのは、結果が画面外へ出る操作
  （別タブで開く、ダウンロードする）とPDF情報の取得だけ。Markdown欄へ反映する操作は
  カード内に専用のメッセージ欄を持っており、両方へ出すと同じ文言が画面に2つ並ぶ。
