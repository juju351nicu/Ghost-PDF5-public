# 検索可能PDF（OCRサンドイッチPDF）設計

作成日: 2026-09-17
状態: 実装完了（2026-09-17）。バックエンド・テスト・最小UIまで実装済み。
実Tesseract（日本語）による実データ確認も完了済み（第7節）。

## 1. 目的

スキャン画像や、画像化された文字だけのPDFページに対し、ローカルのTesseractでOCRを行い、
認識した文字を元のPDFへ**見た目を変えずに**透明テキスト層として書き戻す。結果は検索・コピペが
できるPDF（いわゆる「OCRサンドイッチPDF」）になる。既存の画像Markdown下書き（`/markdownDraftImage`等）や
PDF編集APIとは別APIで、既存の挙動には影響しない。

## 2. スコープ

- OCRエンジンはTesseractのみ。Vision LLM（Anthropic/OpenAI）は使わない。Vision LLMは画像1枚を
  「文章として書き起こす」ことはできても、文字ごとの正確な座標（バウンディングボックス）を返す
  API契約を持たないため、透明テキストを元の文字の位置に正確に重ねる用途には使えない。
  Tesseractは`tsv`出力で単語単位の`left/top/width/height`をピクセル単位で返すため、この用途に唯一使える。
- 対象は「画像だけでテキスト情報が無いページ」（既存の文字レイヤーを壊さない）。
  既に文字レイヤーがあるページに二重に文字を重ねるかどうかは`mode`で選べる（第4節）。
- 出力はPDF1つ（アップロードしたPDFと同じページ数・同じ見た目）。Markdownや画像には変換しない。
- CJK（日本語）は単語単位の分かち書きの精度に既知の限界がある（第9節）。矯正はスコープ外。

## 3. API概要

| 項目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/searchablePdf` |
| Content-Type | `multipart/form-data` |
| Response Content-Type | `application/pdf`（inline表示） |
| 認証相当 | 既存と同じ `access-token` header と session token の照合 |
| 保存 | 行わない（一時ファイルのみ。既存PDF編集APIと同じレスポンス方式） |

### Request（`SearchablePdfRequest`）

| フィールド | 必須 | 内容 |
| --- | --- | --- |
| `originalFile` | 必須 | OCR対象のPDF |
| `mode` | 任意（省略時`AUTO`） | `AUTO` / `FORCE_OCR` |
| `password` | 任意 | 暗号化PDFのパスワード |

### Response

成功時は`application/pdf`をinlineで返す（`/rotatePdf`と同じ方式）。失敗時は既存`ErrorResponse`形式。

| Status | 条件 |
| --- | --- |
| `200 OK` | 生成成功。検索可能PDFを返す |
| `400 Bad Request` | `originalFile`未指定、OCR対象ページ数が上限超過 |
| `403 Forbidden` | token不一致 |
| `413 Payload Too Large` | アップロードファイルサイズが上限超過 |
| `500 Internal Server Error` | PDFの読み込み・画像化・透明テキスト書き込み・保存に失敗 |
| `503 Service Unavailable` | Tesseract機能が無効 |

## 4. 変換モード（`SearchablePdfMode`）

`PdfMarkdownDraftMode`と同じ`CodeEnum<String>`パターン。

| 値 | OCR対象ページ | 用途 |
| --- | --- | --- |
| `AUTO`（既定） | 文字レイヤーが空白のページだけ | スキャン混在PDFの補完。既存の文字レイヤーには触れない |
| `FORCE_OCR` | 全ページ | 文字レイヤーはあるが誤字や抽出崩れがあるページも含め、OCR結果で確実に検索可能にしたい場合 |

対象ページの判定は`PDFTextStripper`でページ単位テキストを抽出し、`StringUtils.isBlank`で空白判定する
（`PdfDocumentAnalysisLogic`の`mode=AUTO`と同じ判定基準）。対象ページ数は上限チェック
（`ghost.ocr.pdf.max-pages`、既存の画像Markdown下書きと共有設定）の対象になり、超過時は1ページも
処理せず`PdfPageLimitExceededException`（400）を投げる。

## 5. 座標変換

Tesseractの`tsv`出力はピクセル単位・左上原点（`left/top/width/height`）。PDFはポイント単位・左下原点のため、
ページを画像化したときの解像度（`renderDpi`）を使って変換する。

```
pdfX = pixelLeft * 72 / renderDpi
pdfY = pageHeightPt - (pixelTop + pixelHeight) * 72 / renderDpi
```

`pageHeightPt`は書き込み対象ページの`MediaBox`の高さ（ポイント）。Y座標はPDFが下から上へ増える座標系のため、
画像の`top`（上端からの距離）をそのまま使わず、単語の下端（`top + height`）を基準に反転させる。

フォントサイズは単語の高さ（ポイント換算）をそのまま使い、`Tz`（水平方向スケーリング）で単語の幅を
Tesseractが報告した幅に合わせる（`font.getStringWidth(text)`から求めた自然な幅と目標幅の比率。1〜1000%にクランプ、
自然な幅が0以下なら100%のまま）。文字の大きさ・字間を完全に一致させることが目的ではなく、
「検索・コピペで正しい文字列が拾えること」を優先している。

## 6. 不可視テキストの書き込み方式

- レンダリングモードは`RenderingMode.NEITHER`（`Tr 3`）。塗りも線も描かれないため見た目は変化しない。
- 既存ページへの追記は`PDPageContentStream`を`AppendMode.APPEND`で開き、既存の内容ストリームの後ろに
  追記する（既存の可視コンテンツを消さない）。
- フォントは`fonts/NotoSansJP-Regular.ttf`（既存の`MarkdownPdfRenderer`で使用しているものと同じ、
  ライセンス済み・埋め込み済みのフォント）を`PDType0Font.load(document, fontStream, false)`
  （**`embedSubset=false`**、フォント全体を埋め込む）で読み込む。

  **既知の不具合と対処（重要）**: 2引数版の`PDType0Font.load(document, fontStream)`
  （`embedSubset=true`が既定）を使うと、このNoto Sans JPフォントに限りPDFBoxのサブセット化で
  グリフIDが2つずれる不具合が実測で見つかった（例: 書き込んだ"HELLO"が抽出時に"FCJJM"になる。
  スペース以外の全文字がコードポイント-2ずれる）。3引数版で`embedSubset=false`を指定することで
  解消することを確認した。ファイルサイズは増える（フォント全体を埋め込むため）が、検索可能PDFの
  目的は検索・コピペの正しさであり、正しさを優先する。
- フォントが表現できない文字（`font.getStringWidth`等が`IllegalArgumentException`を投げる場合）は
  その単語だけを警告ログでスキップし、ページ全体を失敗させない。
- フォントの読み込みは1ドキュメントにつき1回だけ行い、対象ページ全体で使い回す（`SearchablePdfLogic`）。

## 7. 実Tesseract確認（2026-09-17）

日本語の実データで実際にTesseract（5.4.0.20240606、ローカルインストール、`jpn`言語データ）を使い、
実際にアプリを起動して`POST /searchablePdf`を実行し、以下を確認した。

- テスト用PDF: 1ページ目=日本語文章の画像のみ（文字レイヤー無し）、2ページ目=既存の文字レイヤーあり。
- `mode=AUTO`でリクエストし、`HTTP 200`で検索可能PDFを取得。
- ページ数は変化なし（2ページのまま）。
- 生成後の1ページ目を`PDFTextStripper`で抽出すると、「検索可能PDFの実証試験」「文書番号12345番」
  「承認者は佐藤部長」を実際に含んでいた（CJKの単語分かち書きに起因する軽微な語間の乱れはあるが、
  想定どおり。第9節）。
- 2ページ目（既存の文字レイヤー）は`AUTO`モードにより一切変更されていないことを確認した。
- **視覚的な同一性の最終確認**: 生成前後のPDFの1ページ目を同一DPI（150）で画像化し、全ピクセルを比較。
  総ピクセル数2,173,720に対し、差分ピクセル数は**0**だった。不可視テキスト層の追加が見た目に
  一切影響しないことを、目視ではなくピクセル単位の比較で証明した。
- ブラウザ（Chrome、Playwright経由）からも実際にファイルをアップロードし、「検索可能PDFにする」
  ボタンから新規タブでPDFが開くところまで一連の操作を確認した。

詳細な実行手順・実測値は`../成果物/4_確認記録/33_実Tesseract確認結果_検索可能PDF.md`を参照。

## 8. 例外とHTTP status

| 例外 | Status | 備考 |
| --- | --- | --- |
| `PdfPageLimitExceededException`（既存を再利用） | 400 | OCR対象ページ数が`ghost.ocr.pdf.max-pages`超過 |
| `SearchablePdfUnavailableException`（新規） | 503 | Tesseract無効時 |
| `PdfProcessingException`（既存を再利用） | 500 | 読み込み・画像化・書き込み・保存の失敗 |

`SearchablePdfUnavailableException`を新規に作った理由: 当初の設計方針は
「新しい例外クラスは増やさず`imagecontent.exception.OcrUnavailableException`（503）を再利用する」
だったが、実装・テスト中に、共有ハンドラー`GlobalExceptionErrorHandler#handleOcrUnavailable`が
「画像Markdown下書き機能は無効です。」という**この機能とは無関係な固定メッセージ**を返すことが
実際のログ出力から判明した。誤ったメッセージを利用者に見せる設計上の欠陥と判断し、他の全機能と同様に
機能固有の正しいメッセージを持つ専用例外・専用ハンドラーを新設する方針へ変更した。

## 9. 既知の限界

- **CJK（日本語）の単語分かち書き**: Tesseractはスペース区切りの無い言語（日本語等）でも文字送りの
  まとまりを「単語」として認識するが、区切り方が実際の単語境界と一致しない場合がある。
  抽出したテキストが正しい文字列を含んでいても、語間に想定外のスペースが入ることがある
  （第7節の実データ確認でも観測済み）。検索・コピペ用途では実用上問題にならないことが多いが、
  抽出テキストをそのまま自然文として扱う用途には向かない。
- Tesseractのpsm（ページセグメンテーションモード、既定6=「均一なテキストブロック」）が
  レイアウトに合わない場合、認識精度・座標精度が下がる。複雑なレイアウト（段組、表等）は
  未検証。
- 手書き文字、低解像度スキャン、傾いた画像はOCR精度がTesseractの一般的な限界に従う
  （既存の画像Markdown下書きのTesseract providerと同じ限界）。

## 10. 責務とクラス配置

新しい最上位パッケージは作らず、既存の`pdfcontent`/`imagecontent`に配置した
（既存の`imageControllerServiceLogicDependenciesKeepDirection`の`optionalLayer("PdfDraftService")`が
`pdfcontent.service`パッケージ全体から`imagecontent.logic`への依存を既に許可しているため、
新しいArchUnitルールは不要）。

```text
com.clip.ghost.imagecontent
  dto.OcrWordBox                          -> OCR結果1単語分(text, left, top, width, height)。DTOのため層の縛りが無く、pdfcontent.logicからも参照可能
  logic.TesseractWordBoxExtractor (if)    -> isEnabled() / describe() / extractWordBoxes(byte[])
  logic.TesseractWordBoxExtractorImpl     -> Tesseract CLIをtsv出力で実行し、単語ボックス一覧へ変換

com.clip.ghost.pdfcontent
  controller.SearchablePdfController      -> multipart受付, token検証, ファイルサイズ検証, Service委譲
  service.SearchablePdfService            -> 有効性確認(503), モード既定値解決, GhostPdfLogic呼び出し
  logic.SearchablePdfLogic                -> 対象ページ選定, 上限チェック, 画像化, OCR呼び出し, 透明テキスト書き込み, 保存
  logic.SearchableTextLayerWriter         -> フォント読み込み, 座標変換, 不可視テキストの書き込み
  logic.SearchablePdfPageOcr (if)         -> 画像化した1ページ分(byte[])を単語ボックス一覧へ変換する関数型interface(Serviceでbridge)
  enums.SearchablePdfMode                 -> AUTO / FORCE_OCR（CodeEnum<String>）
  dto.SearchablePdfRequest
  exception.SearchablePdfUnavailableException(503)
```

`SearchablePdfPageOcr`は、Logic層が`imagecontent`へ直接依存できない制約の中で、Service層が
`TesseractWordBoxExtractor::extractWordBoxes`をこのinterfaceのラムダとして`GhostPdfLogic`経由で渡す
橋渡し役（`PdfMarkdownDraftService`が`ImageToMarkdownConverter`を橋渡しするのと同じパターン）。

設定は既存の`ghost.ocr.pdf.*`（`max-pages` / `render-dpi`）と`ghost.ocr.tesseract.*`
（`enabled` / `command` / `tessdata-directory` / `languages` / `psm` / `preserve-interword-spaces` /
`timeout-seconds`）をそのまま再利用する。この機能専用の新しい設定キーは追加していない。

## 11. テスト計画

- `SearchablePdfModeTest`: `fromKey` / `KEY_MAP` / 大文字小文字無視 / 不正値の例外 / `convertsEveryPage()`。
- `TesseractWordBoxExtractorImplTest`: `CommandRunner`をmockし、固定サンプルの`tsv`出力を解析、
  コマンド引数の並び（`tsv`が最後）、異常系。
- `TesseractWordBoxExtractorIntegrationTest`（`@Tag("ocr")`）: 実Tesseractで英語のみ確認
  （既存の`TesseractOcrIntegrationTest`と同じ移植性の方針）。Tesseract未導入環境では`Assumptions`でskip。
- `SearchableTextLayerWriterTest`: 書き込んだ文字が`PDFTextStripper`で抽出できること、
  `RenderingMode.NEITHER`が実際に設定されていること（`PDFStreamParser`で生の内容ストリームを走査し、
  `Tr`オペレーターの直前のオペランドが`3`であることを確認）。
- `SearchablePdfLogicTest`: 実際に小さいPDF（空白ページ・文字レイヤーありページの混在）を作り、
  `AUTO`は空白ページのみOCR対象になること、`FORCE_OCR`は全ページ対象になること、上限超過時は
  OCR呼び出しが1回も発生しないこと、既存の文字レイヤーページが変更されないことを検証。
- `SearchablePdfServiceTest`（Mockito）: 無効時503、`mode`省略時`AUTO`解決、`FORCE_OCR`受け渡し、
  `SearchablePdfPageOcr`ラムダが実際に`TesseractWordBoxExtractor`へ委譲すること。
- `SearchablePdfControllerTest`（standalone MockMvc）: 200 / 403 / 400（ファイル未指定・不正mode） / 413 / 503。
- 新しいArchUnitルールは追加していない（第10節）。
- 実Tesseractを叩く自動テストは`@Tag("ocr")`で分離し、`-Pfast-test`では除外する
  （既存のOCR系テストと同じ方針）。

## 12. FE最小UI

既存「編集元ファイル」カード（`main.html` / `original-pdf-form.js`）に「検索可能PDF（OCRサンドイッチ）」
セクションを追加した。

- `mode`選択（AUTO/FORCE_OCR）と「検索可能PDFにする」ボタン、
  「ローカルのTesseractでOCRし、見た目はそのままで検索・コピペ可能なPDFを作ります。外部送信は行いません。」
  という注意書きを常時表示する。
- 既存の汎用ヘルパー`requestPdfAndOpen(url, payload)`（パスワード付与・新規タブ表示・処理状態パネル連携を
  既存API呼び出しと共通化済み）をそのまま使う。新しいFE共通処理は追加していない。
- ブラウザでの動作確認: PDFをアップロードし、新セクションの表示（見出し・mode選択・ボタン・注意書き）を
  確認したうえで実際に「検索可能PDFにする」を押し、新規タブに検索可能PDFが開くところまで確認した
  （第7節）。
