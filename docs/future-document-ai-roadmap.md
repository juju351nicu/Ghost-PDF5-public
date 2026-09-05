# GhostPdf 将来拡張メモ: Markdown / AI / CSV / Utils 利用方針

## 目的

このメモは、GhostPdf を現在のPDF編集ツールから、将来的に「設計書PDFを読み込み、Markdown化し、AI検索・要約・レビュー支援へつなげるツール」へ拡張する場合の判断を残すためのものです。

特に次の判断がぶれないようにする。

- `PathUtils` / `FileInfoUtils` をGhostPdf本流でどこまで使うか。
- 現場で使っている `openCsv` やCSV処理を、学習目的でGhostPdfへ入れるべきか。
- PDF編集、Markdown保存、AI連携、CSV/Excel/Word対応を同じ責務に混ぜない。
- 今すぐ入れるもの、後で入れるものを分ける。

## 結論

現時点では、GhostPdf のPDF編集コアにCSV / openCsv を無理に入れない。

一方で、`PathUtils` は現在のPDF一時保存・拡張子判定でも自然に使えるため、本流で使ってよい。`FileInfoUtils` は現在の結合・挿入・削除中心の処理では出番が少ないため、Markdown保存・文書一覧・検索機能が出てきた段階で使う。

CSVはGhostPdfで将来使う可能性がある。ただし用途はPDF加工そのものではなく、文書メタ情報、変換結果、AI処理結果、テスト観点、投入状況などの入出力・レポート用途が中心になる。


## 構想名候補

この構想はまだ想像段階であり、正式なプロダクト名・機能名として確定したものではない。
ただし、将来の方向性を説明しやすくするため、候補名として次を残す。

### Legacy Doc Converter

古いPDF / Excel設計書を、AIが読みやすいMarkdown設計書へ変換するツール。

主な狙い:

- 古い設計書資産を捨てずに再利用する。
- PDFやExcelに閉じ込められた設計情報を、検索・差分確認・AI処理しやすい形へ移す。
- 人間が読む資料から、AIも扱える構造化ドキュメントへ橋渡しする。

### AI Design Doc Refiner

PDF / Excel / Word のぐちゃぐちゃな設計書を、Markdown / OpenAPI / YAML へ整理するツール。

主な狙い:

- 表記ゆれ、古いフォーマット、読みづらい構成を整理する。
- API仕様、画面仕様、DB定義、テスト観点などを再利用しやすい形式へ変換する。
- AIレビュー、要約、矛盾検出、テスト観点生成につなげる。

使い分けのイメージ:

- `Legacy Doc Converter`: 古い設計書を現代的なMarkdown資産へ移行するニュアンスが強い。
- `AI Design Doc Refiner`: 既存設計書をAI支援で整理・洗練するニュアンスが強い。

現時点では、GhostPdf本体の名称変更はしない。まずは内部メモ上の構想名として扱う。

## 現在のGhostPdfでの扱い

### 今やってよいこと

- PDFファイル名、拡張子、一時ファイル名の処理で `PathUtils` を使う。
- ファイル作成・コピー・移動・削除では `FileOperationUtils` を使う。
- 旧 `StorageUtils` は削除済みのため、新規処理では責務別Utilsへ寄せる。
- パス、ファイル操作、ファイル情報取得を1つの巨大Utilsへ戻さない。

### 今は無理にやらないこと

- PDF結合・挿入・置換・ページ削除の中にCSV処理を混ぜる。
- 学習目的だけで、GhostPdfControllerやGhostPdfServiceの本流へ `openCsv` を接続する。
- 将来のAI連携を見越して、まだ必要になっていないTika、Spring AI、Vector DB、OpenAI/Claude連携を一気に入れる。
- CSVサンプルをSwagger上の本番APIとして見せる。

## Utilsの使い分け

### PathUtils

パス文字列、ファイル名、拡張子の扱いを担当する。

利用候補:

- アップロードPDFの拡張子判定。
- 一時PDFファイル名生成。
- Markdown保存時の `.md` 拡張子判定。
- CSV出力時の `.csv` 拡張子判定。
- PDF / Markdown / CSV / Excel / Word など、将来対応するファイル種別の軽い判定。
- 画面やAPIから渡されたファイル名を保存用ファイル名へ変換する前処理。

注意:

- 実ファイルの存在確認や読み書きまでは担当しない。
- セキュリティ上重要なパス正規化やディレクトリトラバーサル対策は、別途保存先制御と組み合わせる。
- 拡張子だけでファイルの中身を信頼しない。PDFの実処理ではPDFBox読み込み時の検証も必要。

### FileOperationUtils

ファイルやディレクトリへの変更操作を担当する。

利用候補:

- アップロードPDFの一時保存。
- 抽出テキストから生成したMarkdownの保存。
- Markdown編集内容の上書き保存。
- 処理済みファイルの移動。
- 一時ファイル削除。
- 出力ディレクトリ作成。

注意:

- 削除対象は必ず明確なPathにする。
- 広すぎるディレクトリ、未解決の環境変数、曖昧なglobを削除対象にしない。
- `Files.createDirectories` を使い、親ディレクトリも含めて安全に作る。

### FileInfoUtils

ファイルの参照・一覧・情報取得を担当する。

利用候補:

- 保存済みPDF一覧。
- 保存済みMarkdown一覧。
- Markdownファイルの行数確認。
- Markdownファイルのサイズ確認。
- 保存済み文書のキーワード検索。
- Vector DB投入前の対象ファイル列挙。
- 変換済みファイルと未変換ファイルの差分確認。
- 管理画面やデバッグ画面でのファイル情報表示。

注意:

- 現在のPDF結合・挿入・削除だけなら、無理に使わなくてよい。
- `Files.walk` / `Files.lines` を使う場合は、try-with-resourcesで確実に閉じる。
- 大量ファイルを扱う場合は、全件読み込みではなくページングや上限を検討する。

### StorageUtils

旧Facadeは削除済み。

方針:

- `StorageUtils` は復活させない。
- 新規コードでは `PathUtils` / `FileOperationUtils` / `FileInfoUtils` を直接使う。
- どうしても共通化が必要な場合は、標準API / commons-io で足りないプロジェクト固有ルールだけを責務別Utilsへ追加する。

## CSV / openCsv の扱い

### 現時点の判断

学習目的で `openCsv` を試すこと自体は良い。ただし、GhostPdfのPDF編集本流へ入れるのはまだ早い。

理由:

- 現在の主機能はPDFアップロード、結合、挿入、置換、ページ削除であり、CSVは直接必要ない。
- PDF処理の安定化と責務整理を優先している段階で、別目的のCSV処理を混ぜると差分が読みにくくなる。
- 将来CSVが必要になる可能性は高いが、その用途はPDF加工ではなく、メタ情報・レポート・一括処理・AI結果出力に寄る。

### 学習するなら

おすすめ順:

1. 別ブランチで小さいCSVサンプルを作る。
2. GhostPdf内でやる場合は、PDF本流から独立した `sample` / `experimental` 扱いにする。
3. Swaggerには本番APIとして出さず、必要なら `@Hidden` で隠す。
4. Controllerから直接複雑なCSV処理を書かず、CSV専用のService/Utilsへ分ける。
5. CSVの読み書き仕様をJUnitで固定してから本流へ昇格する。

### 将来GhostPdfでCSVが役立つ場面

- PDF一覧のメタ情報エクスポート。
- PDFテキスト抽出結果のCSV出力。
- 一括アップロード対象のmanifest CSV読み込み。
- Markdown変換済み/未変換ファイルの一覧出力。
- Vector DB投入状況のレポート。
- AI要約結果の一覧出力。
- AI構造化結果の確認用CSV。
- 設計書Q&A結果の履歴出力。
- 矛盾検出結果の一覧出力。
- テスト観点生成結果のCSV/Excel出力。

## 将来構想

想定している大きな流れ:

1. PDFアップロード。
2. PDF分割・結合・抽出。
3. PDFBox / Tika でテキスト抽出。
4. Claude / OpenAI / ローカル処理でMarkdownへ整形。
5. Markdownを保存。
6. Markdownプレビュー。
7. Markdown編集。
8. MarkdownからPDF出力。
9. Spring AI `MarkdownDocumentReader` で読み込み。
10. Vector DBへ格納。
11. 設計書Q&A。
12. 要約。
13. 矛盾検出。
14. テスト観点生成。
15. Excel / Word対応。


## 構想ベースのPhase案

このPhase案は、現時点の想像をメモとして残すものであり、実装順を確定するものではない。
ただし、今後の優先順位を考える時の土台として使う。

### Phase 1: PDF編集基盤

目的:

- PDFの分割。
- PDFの結合。
- PDFメタデータ取得。
- PDFページの差し替え。
- PDFページの抽出。

補足:

- 現在のGhostPdfの中心に近い領域。
- 既存の結合、挿入、置換、末尾挿入、ページ削除の仕様を壊さないことを優先する。
- 最初の新規APIとして `POST /metadataPdf` を追加済み。ファイル名、ファイルサイズ、ページ数、暗号化有無を返す。
- `POST /extractPdf` を追加済み。1始まりのページ番号リストを受け取り、ページ番号順・重複なしで指定ページだけを抽出する。
- `POST /mergePdf` を追加済み。multipartで受け取った複数PDFを送信順に結合する。
- `POST /splitPdf` を追加済み。初期仕様としてPDFを1ページずつ分割し、分割後PDFをZIPで返す。
- 指定範囲ごとの分割は、画面入力とAPI仕様を整理してから別フェーズで検討する。

### Phase 2: PDF解析 / Markdown化準備

目的:

- PDFからテキスト抽出。
- PDFからページ単位のMarkdown化。
- PDFの表抽出。
- PDFの差分比較。

補足:

- PDFBoxでどこまでできるかを確認する。
- 必要になった段階でTikaやOCRを検討する。
- 表抽出、読み順推定、見出し推定は難易度が高いため、最初から完璧を狙わない。
- ページ単位Markdown化は、後続のAI整形や差分確認に使いやすい中間成果物になる。

### Phase 3: Markdown設計書管理 / AIレビュー

目的:

- Markdown設計書管理。
- MarkdownからPDF出力。
- PDFからMarkdown変換補助。
- AI要約。
- AIレビュー。

補足:

- Markdown保存、一覧、プレビュー、編集を整える。
- AIに丸投げする前に、人間が確認・編集できるMarkdownを残す。
- AI要約やAIレビューは、外部AIなしでもテストできる境界を作る。
- `FileInfoUtils` はこの段階で保存済みMarkdown一覧、行数、サイズ、検索に使いやすい。

### Phase 4: 設計書AI移行ツール

目的:

- Excel / PDF / Word から Markdown / YAML / OpenAPI へ変換。
- 旧設計書から新設計書を生成。
- 設計書Q&A。
- 要約。
- 矛盾検出。
- テスト観点生成。

補足:

- GhostPdfのPDF編集機能とは責務が大きく異なるため、同じController/Serviceに詰め込まない。
- OpenAPIやYAML生成は、設計書の種類が見えてから対象を絞る。
- Excel/Word対応は需要が具体化してからApache POIやPandoc等を検討する。
- AI出力は必ず人間がレビューできる中間ファイルとして残す。

## 推奨する責務分割

将来拡張する場合は、PDF編集機能へすべて詰め込まず、次のように分ける。

```text
pdfcontent
  - PDFアップロード
  - PDF結合
  - PDF挿入
  - PDF置換
  - PDFページ削除
  - PDFテキスト抽出

markdown
  - Markdown保存
  - Markdownプレビュー
  - Markdown編集
  - MarkdownからPDF出力

document
  - 文書メタ情報
  - 保存済みファイル一覧
  - 文書検索
  - 変換状態管理

ai
  - Claude / OpenAI / ローカル処理の呼び出し抽象化
  - プロンプト組み立て
  - AIレスポンス整形

vector
  - Spring AI MarkdownDocumentReader
  - chunking
  - embedding
  - Vector DB格納
  - 検索

export
  - CSV出力
  - Excel出力
  - Word出力
  - レポート出力
```

## 実装フェーズ案

### Phase A: 現在のPDF編集安定化

目的:

- 既存のPDF結合、挿入、置換、末尾挿入、ページ削除を壊さない。
- PDFBox移行後の仕様をJUnitで守る。
- Controller / Service / Logic / Utils の責務を読みやすくする。

この段階ではCSVを本流へ入れない。

### Phase B: PDFテキスト抽出

目的:

- PDFBoxまたはTikaでテキスト抽出を行う。
- 抽出結果を文字列として取得する。
- 日本語PDF、画像PDF、空ページ、パスワード付きPDFなどの境界をテストする。

初期実装:

- `POST /textPdf` を追加済み。PDFBox `PDFTextStripper` で取得できる素のテキストを、`fileName` / `fileSize` / `pageCount` / `text` のJSONで返す。
- 既存画面から `POST /textPdf` を呼び出し、抽出テキストをMarkdown編集欄へ反映できる最小UIを追加済み。保存は自動化せず、ユーザー確認後にMarkdown保存APIを使う。
- まだMarkdown化、OCR、AI整形、ページ単位構造化は行わない。

検討事項:

- PDFBoxだけで十分か。
- Tikaを追加する必要があるか。
- 画像PDFにOCRまで求めるか。

### Phase B-2: ページ単位Markdown下書き

状態:

- 2026-08-01に初期API契約を設計済み。
- request / response DTO、必須ファイルvalidation、JSON項目名・ページ順の契約テストを追加済み。
- `PdfDocumentAnalysisLogic` のページ単位PDFテキスト抽出と専用テストを追加済み。
- `GhostPdfLogic` のFacadeメソッドと、成功・失敗時の入力一時ファイル削除テストを追加済み。
- `PdfMarkdownDraftService` のページ番号付与、改行正規化、決定論的なMarkdown下書き生成を追加済み。
- 専用 `PdfMarkdownDraftController` と、token・validation・ファイルサイズ境界の単体テストを追加済み。
- `/markdownDraftPdf` のmultipart request、必須ファイル、response schema、HTTP statusのOpenAPI定義テストを追加済み。
- 既存 `main.html` の編集元PDFカードから下書きAPIを呼び出し、戻り値をMarkdown編集欄へ反映する最小UIを追加済み。
- 自動保存・自動プレビューは行わず、既存Markdown操作とVite未導入の方針を維持する。
- sample PDFの実API呼び出し、デスクトップ、モバイル表示で初期導線を確認済み。
- 詳細は `docs/page-markdown-draft-api-design.md` を参照する。

目的:

- PDFBoxでページ単位に抽出したテキストを構造化して返す。
- ページ番号を1始まりで固定する。
- 全ページを決定論的なMarkdown下書きへ組み立てる。
- AIやOCRを使わずに単体テストできる中間成果物を作る。
- 人が確認・編集してから既存Markdown保存APIへ渡せるようにする。

初期API契約:

- `POST /markdownDraftPdf`。
- requestは `multipart/form-data` の `originalFile`。
- responseは `fileName` / `fileSize` / `pageCount` / `markdown` / `pages`。
- `pages` は1始まりの `pageNumber` とページ本文 `text` を持つ。
- ページ見出しは `## Page {pageNumber}` で固定する。
- 空ページも省略せず、空文字本文とページ見出しを残す。
- 自動保存と既存Markdown上書きは行わない。

責務:

- 専用 `PdfMarkdownDraftController` を追加し、既存 `GhostPdfController` をさらに肥大化させない。
- `PdfMarkdownDraftService` がMarkdown下書きとresponse DTOを組み立てる。
- `PdfDocumentAnalysisLogic` はPDFBoxによるページ単位抽出だけを担当する。
- `GhostPdfLogic` はFacadeとして入力一時ファイル削除を保証する。
- `markdowncontent` は保存、一覧、読込、プレビュー、更新、削除の責務を維持する。

初期対象外:

- AI、OCR、Tika、Spring AI、Vector DB。
- 見出し、表、段組み、読み順の高度な推定。
- Markdown to PDF。
- Vite、TypeScript、pdf.js。
- Spring Boot 4。

### Phase C: Markdown保存

目的:

- 抽出テキストをMarkdownとして保存する。
- 保存先ディレクトリを明確にする。
- 保存済みMarkdownを一覧表示する。
- Markdownをプレビューする。

初期実装:

- `POST /saveMarkdown` を追加済み。JSONの `fileName` / `content` を受け取り、UTF-8の `.md` ファイルとして保存する。
- 保存先は `ghost.markdown.storage-directory` で設定する。未指定時は一時ディレクトリ配下を使う。
- 保存後は `fileName` / `byteSize` / `lineCount` / `lastModifiedTime` を返す。
- `GET /markdownFiles` を追加済み。保存先直下の `.md` ファイル一覧を、`fileName` / `byteSize` / `lineCount` / `lastModifiedTime` のJSON配列で返す。
- `GET /markdownFile` を追加済み。保存先直下の `.md` ファイル名を指定し、本文と最小メタデータをJSONで返す。
- `GET /markdownPreview` を追加済み。保存先直下の `.md` ファイル名を指定し、sanitize済みHTMLと最小メタデータをJSONで返す。
- `POST /markdownPreview` を追加済み。入力中Markdown本文を保存せずにHTML化し、sanitize済みHTMLを返す。
- `PUT /markdownFile` を追加済み。保存先直下に存在する `.md` ファイル名を指定し、本文をUTF-8で更新する。
- `DELETE /markdownFile` を追加済み。保存先直下に存在する `.md` ファイル名を指定し、ファイルを削除する。
- 既存 `main.html` にMarkdownメモの最小UIを追加済み。保存・一覧・読込・プレビュー・更新・削除をAPI経由で触れる範囲に留め、Markdown管理の複数画面化はまだ行わない。
- 保存/更新後はMarkdown一覧へ返却メタデータを即時反映し、本文変更・ファイル選択・読込・削除時は古いHTMLプレビューを消して画面状態のずれを避ける。
- Markdown一覧のファイル名クリックで本文読込まで行う。手入力ファイル名からの読込ボタンも残す。
- Markdown一覧には `lineCount` / `byteSize` / `lastModifiedTime` を表示する。
- Markdown一覧取得後に0件だった場合は空状態メッセージを表示する。
- Markdownファイル名は画面入力とMarkdown保存/読込系サービスで前後空白を除去する。
- Markdown保存先のシンボリックリンクは、保存時の上書きを拒否し、一覧・読込・プレビュー・更新・削除の対象外にする。
- Markdownライブラリ判断: 保存だけの段階では追加しない。HTMLプレビューの入口で `commonmark-java` と `jsoup` を導入済み。
- まだPDF出力、AI整形は行わない。

ここで `PathUtils` / `FileOperationUtils` / `FileInfoUtils` の出番が増える。

利用例:

- `PathUtils`: `.md` 拡張子、保存ファイル名生成。
- `FileOperationUtils`: Markdown保存、ディレクトリ作成。
- `FileInfoUtils`: Markdown一覧、サイズ、行数、検索。

### Phase D: Markdown編集 / Markdown to PDF

目的:

- 保存済みMarkdownを編集できるようにする。
- 編集後のMarkdownからPDFを出力できるようにする。

注意:

- MarkdownからPDFへの変換ライブラリは別途検討する。
- 日本語フォント、改ページ、表、コードブロックの見た目確認が必要。

### Phase E: AI整形 / 要約

目的:

- Claude / OpenAI / ローカル処理のどれを使ってもController/Serviceが大きく変わらないようにする。
- AIベンダー固有処理を `ai` 配下に閉じ込める。
- プロンプトとレスポンス整形をテストしやすくする。

注意:

- APIキーやモデル名をコードに直書きしない。
- 最初は外部AIに依存しないローカル整形から始めてもよい。
- AI結果は必ず人間が確認できる画面/ファイルとして残す。

### Phase F: Vector DB / RAG

目的:

- MarkdownをSpring AI `MarkdownDocumentReader` で読み込む。
- chunking / embedding / Vector DB格納を行う。
- 設計書Q&A、要約、矛盾検出、テスト観点生成につなげる。

注意:

- Vector DBは後から差し替えられるようにする。
- PDF処理Serviceが直接Vector DBを触らないようにする。
- 投入済み/未投入の管理が必要になるため、ここでCSV/一覧出力が役立つ可能性がある。

### Phase G: CSV / Excel / Word対応

目的:

- AI処理結果や文書メタ情報をCSV/Excel/Wordへ出力する。
- 現場のレビュー・共有・報告に使いやすい形式を用意する。

注意:

- CSVはこの段階で本流に入れる方が自然。
- Excel/Wordは帳票・レビュー資料として需要が出た段階で検討する。
- openCsvを使う場合は、CSV読み書き専用クラスを作り、PDFロジックへ混ぜない。


## 変換難易度メモ

| 変換 | 難易度 | コメント |
| --- | ---: | --- |
| Markdown → HTML | 低い | かなり簡単。プレビュー機能の最初の候補。 |
| Markdown → PDF | 低〜中 | レイアウトに凝ると中。日本語フォント、表、改ページの確認が必要。 |
| Markdown → Word | 中 | Pandoc等で可能。業務資料化したい場合に検討する。 |
| Word → Markdown | 中 | そこそこ可能だが、レイアウトや表が崩れることがある。 |
| Excel → Markdown | 中〜高 | 結合セル、複数シート、セル内改行、罫線依存の表現が厄介。 |
| PDF → Text | 中 | 文字PDFなら可能。読み順、日本語、ヘッダー/フッター除去が課題。 |
| PDF → Markdown | 高 | 表、見出し、読み順、段組み、注釈の推定が難しい。 |
| 画像PDF → Markdown | かなり高い | OCR必須。精度、表認識、文字化け、手書き混在が問題になる。 |

判断:

- 最初に狙うなら `PDF → Text` と `Markdown → HTML` が現実的。
- 次に `PDF → ページ単位Markdown` と `Markdown → PDF` を試す。
- `PDF → 完全なMarkdown` や `画像PDF → Markdown` は難易度が高いため、AI補助やOCR前提の別フェーズにする。
- Excel / Word対応は、PDFとMarkdown管理が安定してから検討する。

## テスト観点

### PDFテキスト抽出

- 通常PDFから文字列を抽出できる。
- 日本語PDFを文字化けさせない。
- 空ページを含むPDFで落ちない。
- 画像だけのPDFで想定通り空文字または警告扱いになる。
- 壊れたPDFで適切な例外になる。
- パスワード付きPDFで想定通り失敗する。

### Markdown保存

- 抽出結果を `.md` として保存できる。
- 保存先ディレクトリがない場合に作成される。
- 同名ファイルの扱いが仕様通りになる。
- 日本語ファイル名を扱える。
- 保存後に一覧表示できる。
- 保存後にプレビューできる。

### FileInfoUtils利用

- 保存済みMarkdown一覧を取得できる。
- 空ディレクトリで空リストになる。
- 存在しないディレクトリで落ちずに扱える。
- ファイルサイズ、行数、内容検索が期待通りになる。
- 大量ファイルでもテストが不安定にならない。

### CSV / openCsv

- カンマ、ダブルクォート、改行を含む値を正しく扱える。
- UTF-8 / BOM有無の扱いを決める。
- ヘッダーあり/なしを仕様化する。
- 空行、空列、不正行の扱いを決める。
- 読み込みエラー時にユーザーへ分かるメッセージを返す。
- CSV処理がPDF編集APIに副作用を与えない。

### AI / Vector DB

- AIを呼ばない単体テストを用意する。
- Prompt組み立ては固定入力でテストする。
- AIレスポンスのparse失敗をテストする。
- Vector DB未接続時にPDF/Markdown機能が壊れない。
- 外部APIキーなしでも通常テストが通る。

## ライブラリ追加方針

現時点では、上記構想のために本番依存を急いで増やさない。

追加候補は、機能が必要になった段階で検討する。

- Tika: PDFBoxだけでは抽出しづらい文書形式やメタ情報抽出が必要になった場合。
- Spring AI: MarkdownDocumentReader、Embedding、Vector DB連携を実装する段階。
- openCsv: CSVの読み込み/出力が本番機能として必要になった段階。
- Apache POI: Excel/Word入出力が必要になった段階。
- Markdown変換ライブラリ: 保存だけなら追加しない。HTMLプレビューの入口として `commonmark-java` と `jsoup` は導入済み。GitHub風Markdownや表/TOCを重視するなら `flexmark-java`、MarkdownからPDF出力を強化する段階ではOpenHTMLToPDF等も候補にする。

## 判断メモ

- 今はPDF編集コアの安定化が優先。
- CSVは学習として試してよいが、GhostPdf本流への組み込みはまだ早い。
- GhostPdfでCSVを使うなら、PDF編集ではなく、文書メタ情報・変換結果・AI結果・レポート用途が自然。
- `PathUtils` は今後も本流で使いやすい。
- `FileInfoUtils` はMarkdown保存・一覧・検索が始まったタイミングで使う。
- `StorageUtils` は削除済み。新規機能は責務別Utilsへ寄せる。
- AI/RAG化する前に、まず「PDFテキスト抽出 → Markdown保存 → Markdown一覧/プレビュー」を固める。
- Markdown保存の入口として `POST /saveMarkdown`、保存済みMarkdown一覧として `GET /markdownFiles`、本文取得として `GET /markdownFile`、HTMLプレビューとして `GET /markdownPreview`、既存Markdown更新として `PUT /markdownFile`、削除として `DELETE /markdownFile` は追加済み。最小UI連携も完了している。
- AIなしの `POST /markdownDraftPdf` は専用Controller、単体テスト、OpenAPI定義テスト、既存画面の最小UI接続まで追加済み。
- ページ単位Markdown下書きAPIはSpring Boot 3.5.16 / Java 25で固定済み。
- Spring Boot 4.0.7 / Springdoc 3.0.3へのplatform更新は、事前監査後の独立コミットで実施済み。
- Boot 4標準のJackson 3と、`JsonUtils` が公開APIで利用するJackson 2の段階移行設計は完了した。
  `JsonUtils` 内部エンジンと専用テストのJackson 3化も完了し、全286テストが成功している。
  DTO / enum / OpenAPIテストの直接mapper利用もJackson 3へ移行済み。
  移行用 `spring-boot-jackson2` も削除し、既存publicシグネチャ用Jackson 2 coreだけを直接宣言済み。
- Jackson移行はMarkdown / AI機能の追加とは独立した基盤整備であり、annotation packageは
  Jackson 3でも `com.fasterxml.jackson.annotation` のまま維持する。
- Markdown / AI系の初期実装はVite移行を前提にしない。`main.html` 1画面で足りる間は、BE APIと既存画面の最小UIで検証する。
- `pdf.js` はページサムネイル、ページ単位選択、テキストレイヤーなどの高度なPDFプレビュー操作が必要になってから検討する。
- `Legacy Doc Converter` / `AI Design Doc Refiner` は現時点では構想名候補として扱い、GhostPdf本体の名称変更や責務拡大は急がない。
- `PDF → Text`、`Markdown → HTML`、`PDF → ページ単位Markdown下書き` のBE/FE初期導線は完了済み。
