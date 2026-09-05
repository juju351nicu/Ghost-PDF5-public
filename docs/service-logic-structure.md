# Service / Logic Structure Decision

## 結論

現時点では、`GhostPdfService` を `GhostPdfService` interface + `GhostPdfServiceImpl` に分ける必要は薄い。
`GhostPdfLogic` は既存public APIを保つFacadeとして維持し、独立度の高い責務から段階的に分割する。

2026-08-01時点では、メタデータ取得とテキスト抽出を `PdfDocumentAnalysisLogic` へ、
一時ファイル操作を `PdfTemporaryFileStorage` へ、ページ複製処理を `PdfPageCopySupport` へ、
ページ削除・抽出・結合・分割を `PdfPageOperationLogic` へ、差し込み・置換・末尾挿入を `PdfInsertLogic` へ分離済み。

ただし、現場の標準が「Service interface + ServiceImpl」で統一されている場合は、将来の独立フェーズで合わせる。
その場合も、PDF仕様変更やpackage renameと同じコミットに混ぜない。

## 現在の責務分担

### Controller

- HTTP request / responseを扱う。
- token / session / validation entry pointを扱う。
- PDF処理の詳細はServiceへ委譲する。

### Service

- Controllerのフォームを業務処理用に整える。
- `MultipartFile` をLogicへ渡し、一時保存パスへ変換する。
- 差し込みフォームから `GhostPdfDto` を組み立てる。
- Logicの処理結果をHTTP responseへ変換する。

### Logic

- `GhostPdfLogic` は既存public APIと入力一時ファイルのライフサイクルを維持する。
- `PdfDocumentAnalysisLogic` はPDFBoxを使うメタデータ取得とテキスト抽出を担当する。
- `PdfTemporaryFileStorage` は一時PDFの保存、読み込み、パス生成、削除を担当する。
- `PdfPageCopySupport` はページ辞書、表示領域、継承リソースの複製を担当する。
- `PdfPageOperationLogic` はページ削除、抽出、結合、分割を担当する。
- `PdfInsertLogic` は差し込み、置換、末尾挿入を担当する。

## ServiceImpl化しない理由

現時点の `GhostPdfService` は実装が1つだけで、外部切替や複数実装の予定がない。
この状態でinterfaceとimplを作ると、次のコストが増える。

- ファイル数が増える。
- 追跡するクラスが増える。
- テストのmock対象やimportが増える。
- DTO/package整理後も差分が大きくなる。

そのため、今は `GhostPdfService` class のままでよい。

## ServiceImpl化を検討する条件

次のどれかに当てはまる場合は、`GhostPdfService` interface + `GhostPdfServiceImpl` を検討する。

1. 現場ルールとしてServiceImplが必須。
2. PDF処理の実装を複数切り替える予定がある。
3. 外部API用Serviceと画面用Serviceを分ける必要が出た。
4. service contractを明示した方がSwagger/REST化後の境界が分かりやすくなる。
5. 専用フェーズでService構成も一緒に整理する判断になった。

## Logicクラスを段階的に分けた理由

分割前の `GhostPdfLogic` は大きかったが、public APIは少なく、PDFBox移行後の仕様も安定していた。
責務を一度に分割すると、一時ファイル削除やPDFBox document closeの責務が散り、バグを入れやすかった。

そのため、次の理由から独立度の高い責務順に分割した。

- PDFBox移行済み仕様を変えないことが優先。
- 結合、挿入、置換、末尾挿入、ページ削除の挙動を守る必要がある。
- `mvn test` が安定している状態を崩さない方がよい。
- Logic分割はpackage renameと同様、差分が大きくなりやすい。

## 現在のLogic構成

`GhostPdfLogic` のpublic APIと一時ファイルのライフサイクルを維持し、内部処理を次の構成に分けている。

```text
PdfDocumentAnalysisLogic
  - PDFメタデータ取得
  - PDFテキスト抽出

PdfTemporaryFileStorage
  - MultipartFileの一時保存
  - 一時ファイル削除
  - PDF byte読み込み

PdfPageCopySupport
  - ページ辞書の複製
  - 表示領域と回転の複製
  - 継承リソースの複製

PdfPageOperationLogic
  - ページ削除
  - ページ抽出
  - PDF結合
  - PDF分割

PdfInsertLogic
  - 差し込み
  - 置換
  - 末尾挿入

GhostPdfLogic
  - 既存public APIを維持するFacade
```

今後も既存public method signatureは維持し、Facadeとして `GhostPdfLogic` を残す。

## 推奨タイミング

1. 現在のBE/FE安定化を完了する。完了済み。
2. package renameを責務別package構成として実施する。完了済み。
3. Swagger / OpenAPI / controller-serviceテストを安定させる。完了済み。
4. 現場ルールに合わせる必要がある場合、ServiceImpl化を専用コミットで行う。
5. メタデータ取得とテキスト抽出を、既存public APIを維持したまま分離する。完了済み。
6. 一時ファイル操作を、既存public APIと設定キーを維持したまま分離する。完了済み。
7. 共通のページ複製処理を、既存のPDFBox処理を維持したまま分離する。完了済み。
8. ページ削除・抽出・結合・分割を、一時ファイルのライフサイクルをFacadeに残して分離する。完了済み。
9. 差し込み・置換・末尾挿入を、一時ファイルのライフサイクルをFacadeに残して分離する。完了済み。

## Javadoc / コメント方針

- 内部Logicクラスには、担当責務と一時ファイルのライフサイクル境界をJavadocで記載する。
- package-privateメソッドには、引数、戻り値、例外を含むJavadocを記載する。
- privateメソッドにも、ページ番号変換やPDFBox固有処理など意図を読み取りにくい場合はJavadocを記載する。
- `//` コメントは処理内容の読み替えではなく、親参照除去、クローン共有、後方削除など「その実装にする理由」を記載する。

## Lombok方針

- `final` フィールドを引数からそのまま設定するだけのコンストラクタは `@RequiredArgsConstructor` を使用する。
- 状態を持たず、明示的な空コンストラクタだけが必要な内部クラスは `@NoArgsConstructor` を使用する。
- 値の正規化、派生オブジェクト生成、親クラスコンストラクタ呼び出しが必要な場合は明示コンストラクタを維持する。
- Lombok化のためにSpring Bean化や依存関係の公開範囲を変更しない。

## package名の判断

- 現行の `pdfcontent` / `markdowncontent` はJava規約どおり小文字で、責務名としても利用可能。
- `PdfContents` / `MarkDownContents` のようなCamelCaseや複数形には変更しない。
- `pdf` / `markdown` へ短縮する場合も、Logic分割やBoot upgradeと混ぜず、専用コミットで扱う。
- 現時点では40以上のファイルと多数のpackage/import参照へ波及するため、短縮だけを目的としたrenameは行わない。

## やらないこと

- 今すぐ `ServiceImpl` を作るだけの変更はしない。
- Logic分割とPDF仕様変更を同時にしない。
- DTO/package整理、ServiceImpl化、Boot upgradeを同じコミットに混ぜない。
- URL、public method signature、フォーム項目名は変更しない。

## コミット方針

ServiceImpl化する場合:

```text
refactor(service): Service interfaceと実装クラスを分離
```

Logic分割する場合:

```text
refactor(pdf): PDF一時ファイル操作と加工ロジックを分離
```
