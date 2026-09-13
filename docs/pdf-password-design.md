# パスワード保護PDFの取り扱い設計

対象: `POST /showPdf` / `/metadataPdf` / `/textPdf` / `/extractPdf` / `/mergePdf` / `/splitPdf` /
`/deletePdf` / `/insertPdf` / `/markdownDraftPdf` / `/thumbnailsPdf`

---

## 1. 目的

ユーザーパスワードで保護されたPDFを、パスワードを受け取って処理できるようにする。

## 2. 何が問題だったか

PDFBoxはユーザーパスワード付きPDFに対して `InvalidPasswordException` を送出する。これは
`IOException` のサブクラスのため、各ロジックの `catch (IllegalStateException | IOException e)` に
そのまま吸われ、`PdfProcessingException`（HTTP 500、「PDF処理に失敗しました」）になっていた。

利用者から見ると、保護されたPDFを指定したことが分からず、対処のしようもない。
`POST /metadataPdf` だけは `encrypted` を返していたが、これは判定結果を表示していただけで、
他のAPIの分岐には使われていなかった。

## 3. 設計判断: アップロード直後に保護を外す

素直に作ると、PDFを開くすべての場所へパスワードが必要になる。`GhostPdfLogic` の10メソッドから
`PdfDocumentAnalysisLogic` / `PdfPageOperationLogic` / `PdfInsertLogic` / `PdfPageCopySupport` まで、
`password` 引数が連鎖的に伝播する。`README.md` の優先方針「public メソッドシグネチャ、URL、
フォーム項目名は原則維持する」にも触れる。

そうはせず、**アップロード直後に一度だけ保護を外した一時ファイルを作り、以降は保護の無いPDFとして
同じ経路を通す**（`PdfDecryptionSupport`）。ページ削除・抽出・結合・分割・差し込み・サムネイル・
Markdown下書きのすべてが、パスワードを知らないまま動く。

- 変わったpublicシグネチャは `GhostPdfLogic.loadPdf(MultipartFile, String)` の追加だけ。
  1引数版は残し、パスワード未指定の呼び出しはそのまま使える。
- パスワードが未指定なら復号処理へ入らない。保護されていないPDFの経路は従来と1バイトも変わらない。
- パスワードを指定しても保護されていなかった場合は、保存したファイルをそのまま使う。
  誤ってパスワードを入れても、保護の無いPDFは従来どおり処理できる。

### 副作用: PDF情報の「暗号化」

解析対象が保護を外したコピーになるため、`document.isEncrypted()` をそのまま返すと
「暗号化なし」になる。利用者がパスワードを入力した直後に「暗号化なし」と出るのは、
渡したファイルの事実と食い違う。

`PdfUploadResult`（保存先パス + 元が保護されていたか）で保存時点の事実を持ち回り、
`GhostPdfService.getPdfMetadata` がアップロードされたファイルの状態を返す。

## 4. エラーの書き分け

PDFBoxは「パスワードが必要」も「パスワードが違う」も同じ `InvalidPasswordException` で、
メッセージも同じ。パスワードを指定したかどうかを `PdfPasswordProtectedException` 自身に持たせて区別する。

| 状況 | errorCode | HTTP status | メッセージ |
| --- | --- | --- | --- |
| パスワード未指定で保護されたPDF | `pdfPasswordProtected` | 400 | このPDFはパスワードで保護されています。PDFを開くパスワードを入力してください。 |
| 指定したパスワードで開けない | `pdfPasswordIncorrect` | 400 | パスワードが違うためPDFを開けません。もう一度入力してください。 |

利用者がパスワードを入力すれば通るため、処理失敗（500）とは分ける。
`PdfPasswordProtectedException` は非検査例外にしてある。検査例外にすると、
呼び出し元の `catch (IOException)` に再び捕まって汎用エラーへ戻ってしまう。

## 5. API契約

PDFを受け取る6つのリクエストDTOへ、任意項目 `password` を追加した。
既存のフォーム項目名は変えていない。

| DTO | 対象API |
| --- | --- |
| `OriginalPdfRequest` | `/showPdf` `/metadataPdf` `/textPdf` `/deletePdf` `/insertPdf` |
| `ExtractPdfRequest` | `/extractPdf` |
| `MergePdfRequest` | `/mergePdf` |
| `SplitPdfRequest` | `/splitPdf` |
| `PdfMarkdownDraftRequest` | `/markdownDraftPdf` |
| `PdfThumbnailRequest` | `/thumbnailsPdf` |

1リクエストにつきパスワードは1つ。`/mergePdf` の結合対象と `/insertPdf` の差し込みPDFにも
同じパスワードを適用する。保護されていないPDFは指定されても影響を受けない。

## 6. クラス配置

| クラス | 責務 |
| --- | --- |
| `pdfcontent.logic.PdfDocumentLoader` | PDF読み込みの集約。`InvalidPasswordException` を専用例外へ振り替える |
| `pdfcontent.logic.PdfDecryptionSupport` | 保護を外した一時ファイルの作成と、失敗時の一時ファイル削除 |
| `pdfcontent.dto.PdfUploadResult` | 保存先パスと「元が保護されていたか」の持ち回り |
| `pdfcontent.exception.PdfPasswordProtectedException` | パスワードで開けなかったことと、指定の有無 |

一時ファイルの後始末は `PdfDecryptionSupport` の中で完結させている。復号に失敗すると
呼び出し元はパスを受け取れず、外側で消せないため。

## 7. セキュリティ

- パスワードをレスポンスへ含めない。エラーメッセージは固定文言で、入力値を反映しない。
- パスワードをログへ出さない。例外メッセージにも含めない（`PdfDocumentLoader` の定数を参照）。
- 画面はパスワードを画面状態としてだけ保持し、ブラウザストレージへ保存しない。
  ファイルを選び直したときと全クリア時に破棄する。
- 保護を外した一時ファイルは他のPDF一時ファイルと同じ扱いで、処理の完了時に削除される。

## 8. フロントエンド

`PASSWORD_REQUIRED` 状態で入力欄を出し、入力されたパスワードで**同じ操作をそのままやり直す**。
状態機械の詳細は [画面の処理状態設計](process-state-design.md) を参照。

パスワードは画面状態として保持し、以降の操作にも自動で付ける。同じPDFを操作するたびに
入力し直さずに済ませるため。payloadへ足すのは送信直前（`PdfPayload.withPassword`）で、
payloadを組み立てる各methodへは配らない。パスワードは「開けなかったので入力してもらう」という
後から決まる値で、payloadを組み立てる時点では分かっていないため。

## 9. テスト

- `GhostPdfLogicTest`: 正しいパスワードで保護が外れること、パスワード未指定/誤りで例外になること、
  誤ったパスワードで一時ファイルが残らないこと、保護の無いPDFにパスワードを指定しても通ること。
- `PdfDocumentAnalysisLogicTest`: 保護されたPDFがPDF処理例外ではなくパスワード保護例外になること、
  所有者パスワードだけのPDFは従来どおり読めること。
- `GlobalExceptionErrorHandlerTest`: 2つのerrorCodeとメッセージの書き分け。
- `GhostPdfServiceTest`: 保護を外して処理しても「暗号化あり」を返すこと。
- `FrontendProcessStateContractTest`: BEのerrorCodeをFEが把握していること、
  入力してやり直せること、パスワードを保存せず持ち越さないこと。

テスト用の保護PDFは `StandardProtectionPolicy` で実行時に作る。パスワードはテスト用の自明な値にする。

## 10. やっていないこと

- **所有者パスワード（権限のみの制限）の解除。** 空パスワードで開けるため、従来どおり処理を継続する。
  印刷や編集の制限を外すことが妥当かは、技術ではなく利用許諾の判断になる。必要になった段階で、
  誰の権限で何を外すのかを決めてから扱う。
- **1リクエストに複数のパスワード。** `/mergePdf` の結合対象や `/insertPdf` の差し込みPDFが
  それぞれ別のパスワードで保護されている場合、1つしか指定できない。
  実運用で「別パスワードのPDFを結合する」場面が出たら、DTOを `password` から
  ファイル単位の指定へ広げる。今は使われない構造を先に作らない。
- **パスワードのセッション保持。** サーバー側に文書セッションを持たない構成のため、
  パスワードもリクエストごとに受け取る。アップロードしたPDFのサーバー保持（`documentId` 構想）を
  入れる段階で、あわせて再検討する。
- **暗号化したPDFの出力。** 保護を外す方向だけを扱う。PDFへパスワードを掛ける機能は別の要件。
