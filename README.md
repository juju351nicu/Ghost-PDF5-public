# Ghost-PDF5

[![Java CI](https://github.com/juju351nicu/Ghost-PDF5-public/actions/workflows/maven.yml/badge.svg)](https://github.com/juju351nicu/Ghost-PDF5-public/actions/workflows/maven.yml)

Ghost-PDF5は、Spring Boot 4、Java 25、Apache PDFBoxを使用したlocal-firstのPDF・Markdown文書ツールです。
PDFの結合、分割、ページ操作、テキスト抽出、Markdown下書き作成を、既存`main.html`から利用できます。

## ドキュメント

- [コーディング規約](docs/coding-guidelines.md)
- [パッケージリネーム計画](docs/package-rename-plan.md)
- [Service / Logic構成判断](docs/service-logic-structure.md)
- [将来拡張メモ: Markdown / AI / CSV / Utils 利用方針](docs/future-document-ai-roadmap.md)
- [ページ単位Markdown下書きAPI設計](docs/page-markdown-draft-api-design.md)
- [画像Markdown下書きAPI設計（vision）](docs/image-markdown-draft-design.md)
- [Spring Boot 4移行事前監査](docs/spring-boot-4-migration-readiness.md)
- [Jackson 3段階移行設計](docs/jackson-3-migration-design.md)
- [同梱サンプル素材の由来](docs/sample-assets.md)
- [セキュリティ方針](SECURITY.md)
- [コントリビューションガイド](CONTRIBUTING.md)

## テスト実行

普段の軽量確認では、Spring Context起動やOpenAPI定義確認を除外する `fast-test` profile を使います。

```bash
mvn test -Pfast-test
```

push前や大きめの変更後は、タグ除外なしで全件確認します。

```bash
mvn test
```

`fast-test` profile は `@Tag("context")` / `@Tag("openapi")` を除外します。`@SpringBootTest` を使うテストには `@Tag("context")` を付け、用途が明確な重いテストには追加タグを付けます。

## 修正計画

現在のリファクタリングでは、`GhostPdfController` を起点に BE のPDF処理と関連ユーティリティを段階的に整理します。
機能拡張は当面 BE first で進め、API / Service / Logic / DTO / JUnit / OpenAPI を固めてから FE を接続します。
FE は当面 WebJar の Vue 3.2.37 を継続利用し、将来的に npm / Node.js / TypeScript / Vite へ移行する前提で、既存画面の安定化を優先します。
Public repository化後も、当面はlocal development / portfolio用途を前提とします。
現在の認証、Cookie、upload制御を本番Internet公開向けの完成形とは扱わないでください。
`main.html` 1画面で扱える規模の間はViteへ急いで移行せず、Markdown / AI系の拡張もまずBE APIとして小さく試します。

### 優先方針

1. 既存仕様を壊さない
   - public メソッドシグネチャ、URL、フォーム項目名は原則維持する。
   - 結合、挿入、置換、末尾挿入、ページ削除の挙動は変更しない。
2. PDFBox 移行後の品質を安定させる
   - OpenPDF / `com.lowagie` 系依存を戻さない。
   - PDFBox の `PDDocument` は try-with-resources で確実に close する。
3. テストでバグを潰す
   - controller / service / logic / utils の変更には JUnit を追加・更新する。
   - PDFやファイル操作のテストではローカル絶対パスを使わない。
4. 命名とコメントを整える
   - メソッド名・クラス名は役割が分かる名前にする。
   - Javadoc / JSDoc / `//` コメントで、互換維持や仕様上の注意を明記する。
5. utility を使いやすくする
   - `StringUtils` / `CollectionUtils` は意味が変わらない範囲で活用する。
   - ファイル操作は NIO `Path` / `Files` と Commons IO を優先し、責務別Utilsへ整理する。
6. DTO変換と区分値を明確にする
   - Lombok は `@Data` ではなく `@Getter` / `@Setter` を基本にする。
   - 単純なコンストラクタは `@RequiredArgsConstructor` / `@NoArgsConstructor` を使い、変換や派生生成がある場合だけ明示実装する。
   - service層のDTO変換は `private build〇〇` メソッドへ集約する。
   - 数値区分は既存API仕様を維持しつつ、内部処理ではenum化を検討する。

### 実施済み

- OpenPDF から PDFBox 3.x へ移行
- 未使用の `ghost4j` とS3 NIO providerを削除し、旧iText / Log4j 1.2とAWS SDK群の不要な推移依存を解消
- JUnit依存を `spring-boot-starter-test` に集約し、重複していたAPI / engineの直接指定を削除
- 固定PDFパス依存テストの解消
- `HttpSession` mock 未設定テストの修正
- `.DS_Store` の Git 混入対策
- `GhostPdfController` 起点の controller / service 整理
- CSV endpoint の `CsvController` 分離
- PDF request / response / DTO を `pdfcontent.model` から `pdfcontent.dto` へ整理
- `GhostPdfLogic` の例外・ログ・一時ファイル削除整理
- `GhostPdfLogic` の既存public APIと一時ファイルライフサイクルを維持した段階的な内部分割
  - `PdfDocumentAnalysisLogic`: メタデータ取得・テキスト抽出
  - `PdfTemporaryFileStorage`: 一時ファイル保存・読込・パス生成・削除
  - `PdfPageCopySupport`: ページ辞書・表示領域・継承リソースの複製
  - `PdfPageOperationLogic`: ページ削除・抽出・結合・分割
  - `PdfInsertLogic`: 差し込み・置換・末尾挿入
- 分割済みLogicのJavadocと理由コメントを整理し、単純コンストラクタをLombokへ統一
- AIなしの `POST /markdownDraftPdf` を追加し、API設計、request / response DTO、ページ単位抽出Logic、Facade、下書き生成Service、専用Controller、OpenAPI契約テスト、最小UI接続まで完了
- `mode=AUTO` のページ上限とコストガードを追加（`ghost.ocr.pdf.max-pages` / `render-dpi`、課金前に400で拒否、ページ単位の画像処理、`PdfPageLimitExceededException`）
- `JsonUtils` のログ処理整理
- 旧 `StorageUtils` の責務分割と削除
  - `PathUtils`: パス文字列・拡張子・PDF拡張子判定
  - `FileOperationUtils`: ファイル/ディレクトリの作成・コピー・移動・削除
  - `FileInfoUtils`: ファイル一覧・件数・サイズ・行数・内容検索
- Lombok `@Data` を `@Getter` / `@Setter` へ置き換え
- Controller / Service のconstructor injectionを `final` field + `@RequiredArgsConstructor` 方針へ整理
- `GhostPdfService` の差し込みDTO変換を `buildInsertPdfDto` へ分離
- PDF差し込み方法を `PdfInsertOption` enum で扱う形へ整理
- ArchUnit + JUnitで動く規約テスト `CodingConventionTest` を追加
  - `@Data` 再混入
  - field injection の `@Autowired` 再混入
  - `System.out` / `printStackTrace`
  - OpenPDF / 固定PDF絶対パス
  - JavaScript の `console.*` / `debugger`
  - JavaScript の `==` / `!=`
  - JavaScript のStorage直接参照 / deprecated utility alias
  - JavaScript のFetchClient迂回
  - JavaScript のDOM直接操作 / HTML直接挿入
  - JavaScript の `window.open` noopener 漏れ
  - Vue template / HTML の `button type` / `v-for key` / inline style
  - PDF enum の `CodeEnum` 実装漏れ
  - Controller / Service / Logic の依存方向
  - service層DTO生成の `build〇〇` 集約漏れ
- `main.js` の責務分割を実施
  - `main.js`: WebJar Vueでのapp起動処理
  - `pdf/pdf-app.js`: PDF編集画面のVue app定義/画面イベント仲介
  - `const.js`: 区切り文字とPDF操作API URLの定数
  - `models/pdf-form-state.js`: 画面状態の初期値生成
  - `validation/page-number-validator.js`: 削除ページ/差し込みページのvalidation
  - `api/pdf-payload.js`: PDF操作API向けmultipart payload生成
  - `api/pdf-api-client.js`: PDF操作API通信/レスポンス処理/PDF表示
  - `api/fetch-client.js`: fetch共通処理/access-token付与/multipart送信
  - Phase 6の責務が分かりやすいよう、JSファイルは責務別ディレクトリとkebab-caseへ整理
- 編集元PDFカードを `components/original-pdf-form.js` へ分離
- 差し込みPDF行を `components/insert-pdf-row.js` へ分離
- 差し込みPDF行のinline styleをCSSクラスへ移動し、modal内buttonにも `type="button"` を明示
- 共通コンポーネントのtemplateを複数行化し、JSDocと `v-for` key を追加
- `pdf-app.js` の差し込みページvalidationをhelper methodへ切り出し、JSDocを追加
- `CodingConventionTest` にVue template / HTMLの `button type` / `v-for key` / inline style再混入検知を追加
- `rest.js` を責務が分かる `api/fetch-client.js` へ移動
- アップロードサイズ上限を `PdfConstants.MAX_PDF_FILE_SIZE_BYTES`（20MB）へ集約し、multipart / Tomcat設定と噛み合わせて超過時に413を画面へ返すよう修正
  - 以前は `application.yml` の10MBが先に効き、Tomcatが接続を切るためブラウザには `Failed to fetch` しか出なかった
  - FEの `validation/file-size-validator.js` で送信前に停止し、`FrontendUploadSizeContractTest` でFE / コンテナ / BEの値の整合を固定
- 編集元PDFのプレビューを `/showPdf` へのアップロード往復から、ローカルObject URLのiframe表示へ変更
  - ページ番号を指定する加工機能では入力PDFを見られることが前提のため、カード内に表示枠と「別タブで開く」を用意
  - `FrontendPdfPreviewContractTest` でプレビューのためにアップロードしないことを固定
- `const.js` のJSDocとフォーマットをES Modules側の書き方へ統一
- `util.js` の互換関数を維持したままJSDocと保存キー定数を整理
- `typingGame` のutility設計を参考に、`util.js` のlocalStorage操作安全化とブラウザ判定を整理
- `CodingConventionTest` にStorage直接参照とdeprecated utility aliasの再混入検知を追加
- `CodingConventionTest` にFetchClient迂回の再混入検知を追加
- `api/api-error-utils.js` へAPIエラーメッセージ変換を集約し、`fieldErrors` 直接参照の再混入検知を追加
- `deletePdf` / `insertPdf` のファイルサイズ検証をOpenAPIの413定義と整合させ、Controller単体テストで固定
- `CodingConventionTest` にDOM直接操作とHTML直接挿入の再混入検知を追加
- `CodingConventionTest` にfield injection の `@Autowired` 再混入検知を追加
- PDF表示時の `window.open` に `noopener` を明示し、再混入検知を追加
- `util.js` / `file.js` のデバッグ用 `console.log` を削除
- `util.js` の差し込みページ番号validationを1以上の整数文字列に整理
- 削除ページ入力のparse処理を1以上の整数/範囲に限定し、不正入力時はAPI送信しないよう修正
- 削除ページ/差し込みページ入力を正しい値に直した際、エラーメッセージと赤文字状態をクリアするよう修正
- PDF API処理中の二重送信を防ぐため、`isProcessing` とボタンdisabled制御を追加
- 未使用の `file.js` を参照確認後に削除
- 入力エラーの赤文字/枠線表示をDOM直接style操作からVue class bindingへ変更
- 差し込みページ番号欄のフォーカス復帰を `document.getElementById` からVue function refへ変更
- `SampleController` の sample resource 読み込み整理
- Springdoc OpenAPI UIを追加し、PDF APIのSwagger確認準備を実施
  - `/showPdf`, `/deletePdf`, `/insertPdf` にOpenAPI annotationを追加
  - `access-token` ヘッダー、multipartフォーム項目、PDF/エラーレスポンスのOpenAPI metadataを整理
  - 画面表示/サンプル用Controllerは `@Hidden` でSwagger対象外に整理
  - `/v3/api-docs` をMockMvcで確認する `OpenApiDocumentationTest` を追加し、JSON構造ベースの検証へ強化
- `Optional<MultipartFile>` を廃止し、未指定/空ファイルはservice層で判定する方針へ整理
- `CodingConventionTest` に `Optional<MultipartFile>` 再混入検知を追加
- `@SpringBootTest` を使うテストは `@Tag("context")` で分類し、`fast-test` profileで普段の軽量確認から除外可能に整理
- コーディング規約の追加
- Spring Boot 4.0.7 / Springdoc OpenAPI 3.0.3 へ更新
- Java 25 へ更新し、Spring Boot 3.5系のJava 25対応範囲で検証
- Java 25対応のため、MockitoをSurefireのjavaagentとして指定し、ArchUnit 1.4.2へ更新
- Java 25コンパイルでLombok annotation processorを明示指定
- Spring Boot側で非推奨になった `@MockBean` をSpring Frameworkの `@MockitoBean` へ移行
- Spring Boot 4.0.7 / Springdoc 3.0.3の作業用コピーで全284テストと起動を確認し、移行事前監査を文書化
- Web MVC starter/test starterと移行用Jackson 2 moduleを明示し、監査済みの最小構成でBoot 4へ移行
- 通常起動ではSpringdocの `/v3/api-docs` / `/swagger-ui.html` を無効化し、OpenAPI定義確認テストだけ明示的に有効化
- WebJar Vue 3.2.37 は `vue.global.js` のブラウザ配信用途に限定し、不要な推移WebJarを除外
- Phase 2の入口として `POST /textPdf` を追加し、PDFBoxで取得できる素のPDFテキストをJSONで返却
- Phase Cの入口として `POST /saveMarkdown` を追加し、Markdown本文をUTF-8の `.md` ファイルとして保存
- 保存済みMarkdown一覧として `GET /markdownFiles` を追加し、保存済み `.md` の最小メタデータをJSONで返却
- 保存済みMarkdown本文として `GET /markdownFile` を追加し、指定した `.md` の本文とメタデータをJSONで返却
- 保存済みMarkdownプレビューとして `GET /markdownPreview` を追加し、指定した `.md` のsanitize済みHTMLをJSONで返却
- 入力中Markdownプレビューとして `POST /markdownPreview` を追加し、保存前のMarkdown本文をsanitize済みHTMLとしてJSONで返却
- 保存済みMarkdown更新として `PUT /markdownFile` を追加し、指定した既存 `.md` の本文をUTF-8で更新
- 保存済みMarkdown削除として `DELETE /markdownFile` を追加し、指定した既存 `.md` を削除
- 既存の `main.html` にMarkdownメモの最小UIを追加し、保存・一覧・読込・プレビュー・更新・削除をAPI経由で確認可能に整理
- 既存の編集元PDFカードから `POST /textPdf` を呼び出し、抽出テキストをMarkdownメモ欄へ反映可能に整理
- Markdown保存/更新後の一覧即時反映と、本文変更時の古いプレビュー消去を追加し、最小UIの表示状態を整理
- Markdown一覧のファイル名クリックで本文読込まで行い、手入力ファイル名からの読込ボタンも維持
- Markdown一覧に更新日時を表示し、保存済みファイルの確認に必要な最小メタデータを画面へ反映
- Markdown一覧取得後に0件だった場合の空状態メッセージを表示
- Markdownファイル名は画面入力とMarkdown保存/読込系サービスで前後空白を除去し、読込・更新・削除時の入力ぶれを抑制

### 追加ライブラリ方針

本番依存ライブラリは、具体的な機能用途が出た段階で小さく追加します。
Markdown保存だけの段階では追加しませんでしたが、HTMLプレビューの入口として `commonmark-java` と `jsoup` を導入済みです。

優先度:

1. commonmark-java / jsoup
   - 導入済み。
   - `commonmark-java` は保存済みMarkdownを標準MarkdownとしてHTML化する。
   - `jsoup` はプレビューHTMLのsanitizeに使い、FEで表示する前提の安全性をBE側で補強する。
1. ArchUnit
   - 導入済み。
   - controller / service / logic の依存方向、PDF enum の `CodeEnum` 実装、OpenPDFパッケージ依存を `CodingConventionTest` で検出する。
   - `@Data` はSOURCE retentionのためArchUnitではなくJUnitのソーススキャンで検出する。
   - Java 25のclass fileを扱うため、1.4.2以降を使う。
2. JaCoCo Maven Plugin
   - JUnitのカバレッジを継続的に見える化したい場合に検討する。
3. Spotless Maven Plugin または formatter plugin
   - フォーマット差分を自動整形し、レビュー差分を小さくしたい場合に検討する。
4. MapStruct
   - DTO変換が増え、手書きの `private build〇〇` が肥大化した場合にのみ検討する。
   - 現状の小さなDTO変換では追加しない。
5. Testcontainers
   - DB / S3 など外部ミドルウェアを本物に近い形でテストする必要が出た場合に検討する。

### 将来拡張メモ

PDF編集機能を将来的にMarkdown保存、AI要約、Vector DB検索、CSV/Excel/Word出力へ拡張する場合の判断は、[将来拡張メモ](docs/future-document-ai-roadmap.md) に残しています。

現時点では、CSV / openCsv をPDF編集コアへ無理に入れません。CSVは、文書メタ情報、変換結果、AI処理結果、テスト観点、投入状況などの入出力・レポート用途が具体化した段階で扱います。

`PathUtils` はファイル名・拡張子・一時ファイル名生成で本流利用してよく、`FileInfoUtils` はMarkdown保存・一覧・検索機能が出てきた段階で利用します。

構想名候補として `Legacy Doc Converter` / `AI Design Doc Refiner` をメモ化しています。現時点では名称変更や本番機能化はせず、将来のPDF/Excel/Word → Markdown/YAML/OpenAPI変換の方向性を整理するためのメモとして扱います。


### 現在地と次フェーズ候補（2026-08-01時点）

今回の `GhostPdfController` 起点のBE/FEリファクタリングは、PDFBox移行後の安定化・テスト固定・主要なFE分割・FE Phase 6棚卸しまで完了しています。
その後の独立フェーズとして、package renameとSwagger / OpenAPI確認向けのController / DTO整理も完了しています。
Phase 1のPDF基本API拡張として、新規API `POST /metadataPdf`、`POST /extractPdf`、`POST /mergePdf`、`POST /splitPdf` を追加済みです。
`metadataPdf` はファイル名・ファイルサイズ・ページ数・暗号化有無をJSONで返し、`extractPdf` は指定ページだけを抽出したPDFを返します。
`mergePdf` は複数PDFを送信順に結合したPDFを返します。
`splitPdf` はPDFを1ページずつ分割し、分割後PDFを格納したZIPを返します。
Phase 2の入口として `POST /textPdf` を追加し、`fileName` / `fileSize` / `pageCount` / `text` をJSONで返します。
既存の編集元PDFカードには、このAPIを呼び出して抽出テキストをMarkdownメモ欄へ流し込む最小UIを追加しています。保存は自動実行せず、ユーザーが内容を確認してからMarkdown保存APIを呼び出します。

`POST /markdownDraftPdf` は、ページ見出し付きの決定論的なMarkdown下書きをJSONで返します。
編集元PDFカードの「Markdown下書き」から呼び出し、PDF由来の `.md` 候補名と下書きを既存編集欄へ反映します。
自動保存と自動プレビューは行わず、内容確認後の既存Markdown操作を維持します。

Markdown保存の最小APIとして `POST /saveMarkdown` を追加し、JSONの `fileName` / `content` を受け取って、設定 `ghost.markdown.storage-directory` 配下へUTF-8で保存します。レスポンスは `fileName` / `byteSize` / `lineCount` / `lastModifiedTime` を返します。

保存済みMarkdown一覧APIとして `GET /markdownFiles` を追加し、保存先直下の `.md` ファイルを `fileName` / `byteSize` / `lineCount` / `lastModifiedTime` のJSON配列で返します。

保存済みMarkdown本文取得APIとして `GET /markdownFile?fileName=...` を追加し、保存先直下の `.md` ファイルだけを対象に `fileName` / `byteSize` / `lineCount` / `lastModifiedTime` / `content` を返します。

保存済みMarkdownプレビューAPIとして `GET /markdownPreview?fileName=...` を追加し、保存先直下の `.md` ファイルだけを対象に `fileName` / `byteSize` / `lineCount` / `lastModifiedTime` / `html` を返します。MarkdownのHTML化には `commonmark-java`、HTMLのsanitizeには `jsoup` を利用します。

入力中MarkdownプレビューAPIとして `POST /markdownPreview` を追加し、JSONの `content` をHTML化して `html` を返します。既存画面の「プレビュー」ボタンはこのAPIを呼び、保存前の編集内容をそのまま確認できるようにしています。

保存済みMarkdown更新APIとして `PUT /markdownFile?fileName=...` を追加し、保存先直下に存在する `.md` ファイルだけを対象に、JSONの `content` で本文をUTF-8更新します。レスポンスは `fileName` / `byteSize` / `lineCount` / `lastModifiedTime` を返します。

保存済みMarkdown削除APIとして `DELETE /markdownFile?fileName=...` を追加し、保存先直下に存在する `.md` ファイルだけを対象に削除します。レスポンスは `fileName` / `deleted` を返します。

既存の `main.html` にはMarkdownメモの最小UIを追加しています。WebJar Vue構成のまま、保存・一覧・読込・プレビュー・更新・削除を `api/markdown-api-client.js` 経由で呼び出します。Markdown管理が複数画面化するまでは、この薄いUIでAPI検証を続けます。
保存/更新後は画面上のMarkdown一覧へ返却メタデータを即時反映し、本文変更・ファイル選択・読込・削除時には古いHTMLプレビューを消して、表示中の本文とプレビューの対応がずれないようにしています。
一覧のファイル名クリックは選択だけでなく本文読込まで実行します。ファイル名を直接入力して読み込む操作も残し、最小UIのまま保存済みMarkdownを確認できるようにしています。
一覧では `lineCount` / `byteSize` に加えて `lastModifiedTime` も表示し、保存済みファイルの更新状態を画面上で確認できるようにしています。
一覧取得後に保存済みMarkdownが0件だった場合は、空状態メッセージを表示します。初期表示では出さず、一覧取得後だけ表示することで、未操作状態と空一覧を分けています。
Markdownファイル名は、画面入力とMarkdown保存/読込/プレビュー/更新/削除のサービス処理で前後空白を除去します。サーバー側のファイル名validationは維持しつつ、画面上とAPI直呼び出し時の入力ぶれを小さくしています。
Markdown保存先のシンボリックリンクは通常ファイルとして扱いません。保存時はリンク先への上書きを拒否し、一覧・読込・プレビュー・更新・削除の対象から除外して、設定した保存先外のファイルへ到達しないようにしています。

FE Phase 6では、次の観点を確認・整理済みです。

- `main.js` はVue appのbootstrapだけを担当し、画面ロジックは `pdf/pdf-app.js` へ分離。
- 初期状態生成は `models/pdf-form-state.js` へ分離。
- ページ番号解析・validationは `validation/page-number-validator.js` へ分離。
- PDF API通信は `api/pdf-api-client.js`、multipart payload生成は `api/pdf-payload.js` へ分離。
- Fetch / FormData / Headers の直接利用は `api/fetch-client.js` へ集約。
- Blob URLは `URL.revokeObjectURL` で解放。
- `isProcessing` による二重送信防止と、リクエスト単位のエラー初期化を維持。
- 開発確認用モーダルボタン、未使用PDF表示状態、ブラウザ標準 `alert()` を削除。
- Vue template規約として、`button type`、`v-for :key`、DOM直接操作禁止、inline style抑制を `CodingConventionTest` で固定。

今回の安定化で完了したこと、次フェーズ候補、今回はやらないことはREADMEと規約へ整理済みです。

BE/PDF中核、Controller / Service / Logic / Utils のテスト固定は大枠完了済みです。以降に進める場合は、下記の大きな構成変更を混ぜず、専用フェーズ・専用コミットで扱います。
`GhostPdfLogic` の段階的分割も完了し、現在は既存public APIと入力一時ファイルのライフサイクルを維持するFacadeとして扱います。
AIなしの `POST /markdownDraftPdf` は、Spring Boot 3.5.16 / Java 25の段階で先に実装・固定しました。
既存 `/textPdf` とMarkdown保存APIは変更せず、ページ単位テキストと決定論的なMarkdown下書きをJSONで返します。
初期API契約は [ページ単位Markdown下書きAPI設計](docs/page-markdown-draft-api-design.md) に記載しています。
PDFBoxによるページ単位抽出、入力一時ファイルを削除するFacade、Markdown下書き生成Service、専用Controller、
multipart request・response schema・HTTP statusのOpenAPI契約テストまで完了しています。
既存 `main.html` の最小UI接続と、sample PDFを使ったブラウザ確認まで完了し、BEからFEまでの初期導線を固定しています。
Spring Boot 4移行事前監査後、Boot 4.0.7 / Springdoc 3.0.3へplatformを更新しました。
Web MVCの新starter構成と移行用Jackson 2 moduleを明示し、全284テスト、Tomcat 11での起動、
設定propertyの互換性を確認しています。
Springの非推奨 `HttpStatus.PAYLOAD_TOO_LARGE` は、同じHTTP 413を表す `CONTENT_TOO_LARGE` へ更新しました。
Commons Langの非推奨文字列API 6呼び出しも、大小文字区別を明示する `Strings.CS` / `Strings.CI` へ更新しました。
`JsonUtils` のpublic APIを維持するJackson 3移行方法は、ソース・依存関係の監査と
作業用コピーでの全284テストまで完了しています。既存Jackson 2 `TypeReference` はJava `Type` 経由で
Jackson 3 mapperへ橋渡しでき、Jackson 3の例外を従来の `IllegalArgumentException` へ包むことで
既存の失敗時契約も維持できることを確認しました。
その設計に沿って、`JsonUtils` の内部mapper、既存Jackson 2 `TypeReference` の橋渡し、
Jackson 3 `TypeReference` overload、変換例外の互換処理を本体へ適用しました。
`JsonUtilsTest` は11件から13件になり、Java 25の全286テストが成功しています。
DTO / enum / OpenAPIテストの直接mapper利用もJackson 3へ移し、`JsonNode#asText()` は
Jackson 3の `asString()` へ更新しました。annotation importは変更していません。
対象26テストと全286テストが成功し、Jackson由来の非推奨compile警告も残っていません。
`spring-boot-jackson2` も削除し、既存Jackson 2 `TypeReference` publicシグネチャに必要な
`com.fasterxml.jackson.core:jackson-core` だけを直接宣言しました。
Java 25の全286テスト、依存ツリー、Boot 4.0.7での通常起動とトップ画面HTTP 200を確認しています。
Commons IOは2021年版の2.11.0から2.22.0へ独立して更新し、ファイルコピー・削除・パス処理・
Markdown保存を含むJava 25の全286テストが成功しています。
詳細は [Spring Boot 4移行事前監査](docs/spring-boot-4-migration-readiness.md) と
[Jackson 3段階移行設計](docs/jackson-3-migration-design.md) を参照してください。

今回のJackson 3段階移行には含めない作業:

- `GhostPdfService` の機械的な `ServiceImpl` 化。
- npm / Vite へのFE移行。

これらは差分が大きく、今の安定化コミットと混ぜるとレビューしづらくなるため、別フェーズ・別ブランチで扱います。

### 今後の候補

- 画像Markdown下書き `POST /markdownDraftImage`（vision / OpenAI provider、既定無効）を追加済み。
  - `POST /markdownDraftPdf` に `mode=AUTO` を追加済み。文字レイヤーが無いページを画像化し、共有の画像変換器（OCR/vision）で補完する。`mode` 省略時は従来動作。
  - `mode=AUTO` のコストガードとして `ghost.ocr.pdf.max-pages`（既定20）と `ghost.ocr.pdf.render-dpi`（既定200）を追加済み。上限超過は1ページも変換せず400で拒否し、画像はページ単位で処理して溜めない。
  - provider `anthropic` / `openai` / `tesseract` を `ghost.ocr.provider` で切替。Tesseractはオフライン/バッチ用のローカル実装で、既定無効。
  - 設計は [画像Markdown下書きAPI設計](docs/image-markdown-draft-design.md) を参照。
- package renameは `pdfcontent` / `pdfcontent.dto` / `common.validation` / `common.utils` / `common.exceptions` の責務別構成へ整理済み。
  - 今後のpackage変更は、Boot upgradeやServiceImpl化とは混ぜず、必要になった責務境界だけを小さく扱う。
- Spring Bootは4.0.7、Springdocは3.0.3まで更新済み。
  - 今後の更新もSpring Boot / Spring Framework / Springdocの互換性を確認し、`mvn test` と `/v3/api-docs` テストを通してから採用する。
  - Boot 4移行はWeb MVC starter、test slice、Jackson 2互換を含む独立コミットで実施済み。
  - `spring-boot-jackson2` は削除済み。既存publicシグネチャ用Jackson 2 coreだけを明示している。
  - Jackson 3の段階移行設計、`JsonUtils` 内部実装、直接mapper利用テスト、依存module整理は完了済み。全286テストが成功している。
  - Jackson annotationはJackson 3でも `com.fasterxml.jackson.annotation` のまま使用する。
- npm / Vite 移行時のfrontend package構成整理
  - `main.html` 1画面とWebJar Vueで運用できる間は、Vite移行を急がない。
  - PDFテキスト抽出、Markdown保存、AI要約などはVite移行を前提にせず、まずBE APIと既存画面の最小UIで検証する。
  - Markdown管理、履歴、設定、レビューなどで画面が2〜3画面以上に増えたら移行タイミング。
  - TypeScript は API client、入力フォーム、エラー表示を型で守りたくなった段階で導入を検討する。
  - Vuetify などのUIライブラリは、Vite + TypeScript の足場が安定した後に検討する。
- `pdf.js` 導入は当分先にする。
  - 編集元PDFはブラウザ標準ビューアをiframeで表示しており、ページ番号とサムネイルはその機能で確認できる。
  - クリックでページ選択、範囲指定UI、テキストレイヤー、注釈表示が必要になった段階で検討する。
  - サムネイル一覧からのページ選択は、`PDFRenderer` で低DPIのページ画像を返すendpointでも実装できるため、pdf.jsは必須ではない。
- APIが増えた場合、`typingGame/src/utils/fetchClient.ts` / `apiErrorUtils.ts` を参考に、
  `HttpError` の導入を検討する。
  - 現状のGhost-PDF5はPDF API中心のため、`api/fetch-client.js` / `api/pdf-api-client.js` / `api/api-error-utils.js` の分離で十分。
  - 複数APIでエラー表示がさらに増えた時点で、例外型やHTTP status別メッセージへの変換を拡張する。
- JSON APIの成功レスポンス共通化は今すぐ導入しない。
  - PDF/ZIPなどのバイナリレスポンスは `ApiResult<T>` のような共通JSONで包まない。
  - 将来JSON APIが増え、`data` / `resultType` / `messageList` のような共通構造が必要になった時点で、名称は `CommonResponse` ではなく `ApiResult<T>` を候補にする。
  - 導入候補になる場面は、PDF処理履歴、ジョブ状態確認、Markdown変換結果、AI要約結果、設定保存結果、複数画面/APIで同じ成功メッセージを返したくなった時。
  - `ApiResult<T>` の雛形と構造案は [コーディング規約](docs/coding-guidelines.md) に将来案として記載する。
  - `JsonUtils` はJSON文字列変換・parse用の補助であり、APIレスポンス共通化とは責務を分ける。
- PDF基本API拡張は、資料のPhase 1に沿って `metadataPdf`、ページ抽出、PDF結合、1ページずつのPDF分割を追加済み。
  - 指定範囲ごとの分割は、画面入力とAPI仕様を整理してから検討する。
  - 既存の `/showPdf`、`/deletePdf`、`/insertPdf` は壊さず、新機能を横に追加する。
- Storage操作やブラウザ判定をさらに増やす場合、`typingGame/src/utils/gameUtils.ts` /
  `authTokenStorage.ts` を参考に、専用モジュールへ責務分離する。
  - 現状は `util.js` の互換維持を優先し、Storage APIへの入口だけを安全化する。
  - `isEmpty` は既存仕様維持のため、空白のみ文字列を空扱いする変更は別関数追加で検討する。
- 品質チェック用ライブラリ/Pluginの導入検討
  - 優先順位は「追加ライブラリ方針」を参照する。

詳細な判断基準は [コーディング規約](docs/coding-guidelines.md) を参照してください。

## セキュリティ

現在はlocal development / portfolio用途を前提としています。
信頼できないnetworkへそのままdeployせず、uploadするPDFやMarkdownに機密情報を含めないでください。
`POST /markdownDraftImage` の外部vision送信は任意機能で、既定は無効です。有効化すると画像が外部AIへ送信されるため、機密画像には使わず、APIキーは環境変数で扱います。詳細は [画像Markdown下書きAPI設計](docs/image-markdown-draft-design.md) と [SECURITY.md](SECURITY.md) を参照してください。
既知の制約と報告方法は [SECURITY.md](SECURITY.md) を参照してください。

## ライセンス

このprojectは [Apache License 2.0](LICENSE) で公開します。
