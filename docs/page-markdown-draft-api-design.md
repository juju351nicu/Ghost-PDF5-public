# ページ単位Markdown下書きAPI設計

作成日: 2026-08-01  
状態: BE初期実装、OpenAPI契約テスト、既存画面の最小UI接続まで完了

## 1. 目的

アップロードされた文字PDFから、PDFBoxでページ単位にテキストを抽出し、
人が確認・編集できるMarkdown下書きをJSONで返す。

このAPIは、既存のPDFテキスト抽出とMarkdown保存の間をつなぐ決定論的な変換境界である。
AIやOCRに依存せず、同じPDFから常に同じ構造の下書きを生成できることを優先する。

## 2. このフェーズで行わないこと

- 既存 `POST /textPdf` の変更。
- Markdownファイルの自動保存。
- 保存済みMarkdownの上書き。
- 見出し、表、段組み、読み順の高度な推定。
- 画像PDFへのOCR。
- AIによる整形、要約、補完。
- Tika、Spring AI、Vector DBの導入。
- MarkdownからPDFへの変換。
- Vite、TypeScript、pdf.jsへの移行。
- Spring Boot 4への更新。

## 3. API概要

| 項目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/markdownDraftPdf` |
| Content-Type | `multipart/form-data` |
| Response Content-Type | `application/json` |
| 認証相当 | 既存と同じ `access-token` headerとHTTP session tokenの照合 |
| ファイル項目名 | `originalFile` |
| ページ番号 | 1始まり |
| 保存 | 行わない |

Pathは既存の `/textPdf`、`/metadataPdf`、`/extractPdf` と同じ命名傾向に合わせる。
将来REST形式へ整理する場合も、この初期URLは互換APIとして扱う。

## 4. Request

### 4.1 Header

```http
access-token: <トップ画面表示時に発行したtoken>
```

既存PDF APIと同じ `AccessTokenValidator` を使用する。

### 4.2 multipart form

| 項目 | 型 | 必須 | 内容 |
| --- | --- | --- | --- |
| `originalFile` | binary | 必須 | Markdown下書きの生成元PDF |

新規DTO `PdfMarkdownDraftRequest` を用意する。
既存 `OriginalPdfRequest` は削除ページや差し込み情報も持つため、新APIでは再利用しない。

DTO案:

```java
public class PdfMarkdownDraftRequest {
    @NotNull(message = "ファイルを入れてください。")
    private MultipartFile originalFile;
}
```

実装時は既存DTOと同じく `@Schema`、`@JsonProperty`、`@Getter`、`@Setter`、Javadocを付ける。

## 5. Response

### 5.1 正常レスポンス

```json
{
  "fileName": "sample.pdf",
  "fileSize": 123456,
  "pageCount": 2,
  "markdown": "## Page 1\n\nfirst page\n\n## Page 2\n\nsecond page",
  "pages": [
    {
      "pageNumber": 1,
      "text": "first page"
    },
    {
      "pageNumber": 2,
      "text": "second page"
    }
  ]
}
```

### 5.2 DTO

新規DTO:

- `PdfMarkdownDraftResponse`
- `PdfMarkdownDraftPageResponse`

`PdfMarkdownDraftResponse`:

| JSON項目 | Java型 | 内容 |
| --- | --- | --- |
| `fileName` | `String` | アップロード時の元ファイル名 |
| `fileSize` | `Long` | アップロードファイルサイズ |
| `pageCount` | `Integer` | PDFの総ページ数。`pages.size()` と一致する |
| `markdown` | `String` | 全ページを連結したMarkdown下書き |
| `pages` | `List<PdfMarkdownDraftPageResponse>` | PDF順のページ抽出結果 |

`PdfMarkdownDraftPageResponse`:

| JSON項目 | Java型 | 内容 |
| --- | --- | --- |
| `pageNumber` | `Integer` | 1始まりのページ番号 |
| `text` | `String` | 改行を正規化したページ本文。空ページは空文字 |

ページ配列にはrawに近い抽出本文を残し、全体Markdownは画面表示や既存保存APIへの受け渡しに使う。
ページごとのMarkdown文字列は `pageNumber` と `text` から決定論的に生成できるため、初期レスポンスでは重複して保持しない。

## 6. Markdown生成規則

### 6.1 ページ見出し

各ページの見出しは次で固定する。

```markdown
## Page {pageNumber}
```

初期実装ではPDF内の文字サイズ、フォント、位置から見出しを推測しない。
英語表記へ固定し、実行環境やLocaleによって出力が変わらないようにする。

### 6.2 ページブロック

本文があるページ:

```markdown
## Page 1

ページ本文
```

空ページ:

```markdown
## Page 2
```

ページブロックはLF 2文字、つまり空行1行で連結する。
全体Markdownの末尾へ改行を強制追加しない。

### 6.3 テキスト正規化

- CRLFとCRをLFへ統一する。
- 本文末尾の空白文字を除去する。
- 本文途中の改行と空白は、PDFBox抽出結果を可能な限り維持する。
- 空または空白だけの抽出結果は空文字として扱う。
- ページを省略せず、空ページも `pages` とMarkdown見出しへ残す。

ファイル名はレスポンスメタデータとして返し、初期MarkdownのH1へ埋め込まない。
これにより、元ファイル名の改行やMarkdown記号をescapeする追加仕様を初期APIへ持ち込まない。

## 7. PDF別の扱い

### 7.1 通常の文字PDF

PDFBox `PDFTextStripper` で1ページずつ抽出する。
ページ順を維持し、1ページにつき1要素を返す。

### 7.2 空ページ

正常レスポンスとする。
`pages[].text` は空文字、Markdownにはページ見出しだけを残す。

### 7.3 画像PDF

OCRは行わない。
PDFBoxで文字を取得できないページは空ページと同じ結果にする。
画像PDFであることを推測するwarning項目は初期レスポンスへ追加しない。

### 7.4 暗号化・パスワード付きPDF

初期APIにpassword項目は追加しない。
PDFBoxがpasswordなしで開けず抽出できない場合は、既存 `PdfProcessingException` と共通500レスポンスを使用する。

password入力対応は、暗号化PDFを正式な対象機能にする段階で別API契約として検討する。

### 7.5 壊れたPDF・PDF以外

既存 `GhostPdfLogic#loadPdf(...)` とPDFBox読み込み時の検証を利用する。
処理不能な場合は `PdfProcessingException` と共通500レスポンスを維持する。

## 8. HTTP status

| Status | 条件 | レスポンス |
| --- | --- | --- |
| `200 OK` | 下書き生成成功 | `PdfMarkdownDraftResponse` |
| `400 Bad Request` | `originalFile` 未指定などDTO validation失敗 | `ErrorResponse` |
| `403 Forbidden` | `access-token` とsession tokenが不一致 | 既存token検証仕様 |
| `413 Payload Too Large` | multipartまたは既存PDFサイズ上限超過 | `ErrorResponse` |
| `500 Internal Server Error` | PDF読み込み・テキスト抽出失敗 | `ErrorResponse` |

ファイルサイズの閾値と判定は既存PDF APIと揃える。
実装時にサイズ検証を共通化する場合も、既存APIのstatusと境界値は変更しない。

## 9. 責務とクラス配置

### 9.1 package

新APIは `pdfcontent` 配下に置く。

理由:

- 入力がPDFである。
- PDFBoxによるページ解析が処理の起点である。
- 下書きを保存せず、保存済みMarkdown管理を担当しない。
- `markdowncontent` は保存、一覧、読込、プレビュー、更新、削除の責務を維持できる。

### 9.2 Controller

候補: `com.clip.ghost.pdfcontent.controller.PdfMarkdownDraftController`

担当:

- multipart request受付。
- DTO validation。
- `access-token` 検証。
- 既存と同じPDFファイルサイズ検証。
- OpenAPI annotation。
- Service呼び出し。

400行程度ある `GhostPdfController` へ新endpointを追加せず、機能単位の専用Controllerにする。
既存Controllerからendpointを移動する作業は行わない。

### 9.3 Service

候補: `com.clip.ghost.pdfcontent.service.PdfMarkdownDraftService`

担当:

- uploadファイル名とサイズの取得。
- `GhostPdfLogic` による一時保存とページ単位テキスト抽出。
- ページ番号の付与。
- Markdown下書きの組み立て。
- response DTOの生成。

DTO生成は既存規約どおり `private build...` メソッドへ集約する。
単一実装のためinterface / ServiceImplは追加しない。

### 9.4 Logic

既存 `PdfDocumentAnalysisLogic` に、1つの `PDDocument` と `PDFTextStripper` を使って
全ページを順に抽出するpackage-privateメソッドを追加する。

`GhostPdfLogic` にはFacade用の新しいpublicメソッドを追加し、入力一時ファイルの削除を `finally` で保証する。
既存publicメソッドの名前、引数、戻り値は変更しない。

LogicはMarkdown見出しを組み立てない。
PDFBox依存のページ抽出だけを担当し、Markdown形式はServiceに閉じ込める。

### 9.5 想定依存方向

```text
PdfMarkdownDraftController
  -> PdfMarkdownDraftService
    -> GhostPdfLogic
      -> PdfDocumentAnalysisLogic
```

既存の `Controller -> Service -> Logic` を維持する。

## 10. 一時ファイルライフサイクル

```text
Controller
  -> Service
    -> GhostPdfLogic#loadPdf
      -> uploadを一時ファイルへ保存
    -> GhostPdfLogicのページ単位抽出メソッド
      -> PdfDocumentAnalysisLogicへ委譲
      -> finallyで入力一時ファイルを削除
```

- 成功時に入力一時ファイルを削除する。
- PDFBox読み込み失敗時にも削除する。
- Markdown組み立ては抽出完了後のメモリ上データだけを使う。
- 出力ファイルは作成しない。

## 11. HTML・セキュリティ

このAPIはMarkdown文字列をJSONで返し、HTMLへ変換しない。
PDFから抽出した文字列中にHTML風の文字が含まれていても、この段階では削除しない。

画面でHTMLプレビューする場合は、既存 `POST /markdownPreview` を使い、
commonmark-javaでHTML化した後にjsoupでsanitizeする。

## 12. テスト計画

### 12.1 DTO

- `originalFile` 未指定でvalidation errorになる。
- JSON property名がAPI契約どおりになる。
- `pages` の順序と項目名が維持される。

### 12.2 `PdfDocumentAnalysisLogicTest`

- 複数ページをページ順に抽出する。
- 1ページごとに別要素を返す。
- 空ページを空文字で残す。
- 内部Logicは入力ファイルを削除しない。
- 壊れたPDFで `PdfProcessingException` を返す。

### 12.3 `GhostPdfLogicTest`

- Facadeがページ単位抽出結果を返す。
- 成功時に入力一時ファイルを削除する。
- 例外時にも入力一時ファイルを削除する。

### 12.4 `PdfMarkdownDraftServiceTest`

- uploadメタデータとページ配列をresponseへ設定する。
- ページ番号を1から順に付ける。
- 本文ありページのMarkdownを規則どおり組み立てる。
- 空ページの見出しを残す。
- CRLF / CRをLFへ正規化する。
- Markdown末尾へ不要な改行を追加しない。
- DTO生成を `build...` メソッドへ集約する。

### 12.5 `PdfMarkdownDraftControllerTest`

- token一致時にServiceへ委譲する。
- token不一致時は403でServiceを呼ばない。
- ファイル未指定時は400になる。
- ファイルサイズ上限時は413になる。
- response Content-TypeがJSONになる。

### 12.6 `OpenApiDocumentationTest`

既存のSpringdoc統合テストへ追加済み。通常起動時のOpenAPI無効化は維持し、テスト時だけ有効化する。

- `/markdownDraftPdf` がPOSTで定義される。
- `access-token` headerが必須である。
- requestが `multipart/form-data` である。
- response schemaが `PdfMarkdownDraftResponse` である。
- 200 / 400 / 403 / 413 / 500が定義される。
- `pageNumber` がinteger、`pages` がarray、`markdown` がstringとして現れる。

### 12.7 `FrontendMarkdownDraftContractTest`

- `original-pdf-form.js` の操作が `main.html` のVue appメソッドへ通知される。
- endpoint定数、multipart payload、`PdfApiClient` を経由する。
- responseの `markdown` が既存Markdown編集欄へ反映される。
- WebJar Vue構成のファイル間接続をJava 25のMavenテストで検知する。

## 13. 実装コミット順

1. 完了: 本設計書とREADME・ロードマップ更新。
2. 完了: request / response DTOとDTOテスト。
3. 完了: `PdfDocumentAnalysisLogic` のページ単位抽出と専用テスト。
4. 完了: `GhostPdfLogic` のFacadeメソッドと一時ファイル削除テスト。
5. 完了: `PdfMarkdownDraftService` と単体テスト。
6. 完了: `PdfMarkdownDraftController` と単体テスト。
7. 完了: OpenAPI定義テスト。
8. 完了: 既存 `main.html` へ最小UIを追加し、sample PDFで実動作を確認。

各コミットでJava 25の `mvn test` を実行する。

## 14. 実装完了条件

- 既存publicメソッドシグネチャが変わっていない。
- 既存 `/textPdf` のresponseが変わっていない。
- PDFBox 3.0.7の既存PDF操作仕様が変わっていない。
- ページ番号が1始まりである。
- 空ページを含めページ数と配列数が一致する。
- 下書き生成がAI、OCR、外部APIなしで決定論的に動く。
- 自動保存や既存Markdown上書きを行わない。
- OpenAPI定義にmultipart request、response DTO階層、400 / 403 / 413 / 500が現れる。
- 既存画面から下書きを生成し、PDF由来の `.md` 候補名とMarkdown本文を編集欄へ反映できる。
- 下書き生成だけでMarkdown保存やHTMLプレビューを自動実行しない。
- 入力一時ファイルが成功時・失敗時とも削除される。
- OpenAPIに新しいrequest / response / statusが定義される。
- Java 25で全テストが成功する。
