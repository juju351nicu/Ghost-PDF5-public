# Ghost-PDF5 コーディング規約

この規約は、Ghost-PDF5 の保守・リファクタリング・AIレビューで判断がぶれないようにするためのルールです。
新規実装だけでなく、既存コードを修正する場合もこの方針に寄せます。

## 基本方針

- 既存の public メソッドシグネチャやURL、フォーム項目名は、互換性に影響するため原則変更しない。
- public API を分かりやすい名前に変えたい場合は、利用箇所と外部公開影響を確認する。利用がある場合は新しいメソッドを追加し、旧メソッドは `@Deprecated` で委譲して段階移行する。利用がなく、誤字や重複だけなら削除する。
- 例外を握りつぶして `null` を返す実装は避ける。既存仕様として `null` 返却が必要な場合は、Javadocに理由を書く。
- Javaでは `System.out.println` と `printStackTrace` は使用しない。SLF4J logger を使用する。
- 過去のデバッグ出力の意図を残したい場合は、`System.out.println` を復活させず、下記のようにメモまたは `LOGGER.debug` へ置き換える。
- JavaScriptでは `console.log` / `console.error` / `console.warn` / `debugger` を残さない。
  - 一時的な確認ログはコミット前に削除する。
  - 画面に出す必要があるエラーはモーダルや画面メッセージへ寄せる。
- JavaScriptでは `alert()` / `confirm()` / `prompt()` のようなブラウザ標準ダイアログを原則使わない。
  - 入力エラーやAPIエラーは、既存のモーダルまたは画面メッセージで表示する。
- JavaScriptの比較は `===` / `!==` を使用し、`==` / `!=` は使わない。
- JavaScriptで `localStorage` / `sessionStorage` を使う場合は、直接参照を増やさず、try-catch付きの取得関数に閉じる。
  - ブラウザ設定やプライベートブラウズでは、Storage参照だけで例外になることがある。
  - 既存の `util.js` では `getLocalStorageObject()` を入口にする。
- JavaScriptのブラウザ判定では、Edge / Opera / Samsung Internet などChrome文字列を含むブラウザを先に判定する。
  - `Chrome` の単純な文字列包含だけで判定しない。
- JavaScriptの空判定は既存仕様を確認してから変更する。
  - `isEmpty` が空白だけの文字列を空扱いしていない場合、安易に `trim()` 判定へ変えない。
  - 空白だけを空扱いしたい場合は、別関数追加やオプション化を検討する。
- JavaScriptで `window.open` を使う場合は、原則 `noopener` を明示する。
  - 別タブから元画面を操作されるリスクを避ける。
  - PDFのBlob URL表示など、既存仕様として別タブ表示が必要な箇所に限定する。
- JavaScriptのHTTP通信は `api/fetch-client.js` を入口にする。
  - `fetch` / `FormData` / `Headers` を各画面や各API clientに直接増やさない。
  - PDF API固有のpayload生成は `api/pdf-payload.js`、送信処理は `api/fetch-client.js` に分ける。
- JavaScriptのAPIエラー表示変換は `api/api-error-utils.js` に集約する。
  - BE共通エラー形式の `fieldErrors` を各API clientや画面で直接読まない。
  - 想定外エラーで例外の `message` を画面へ出さない。英語の内部表現（`Failed to fetch` など）が利用者に見えるため、日本語のメッセージへ変換する。
  - サーバーへ到達できなかった場合だけは専用メッセージを出す。判定は `TypeError` とメッセージパターンの両方で絞る（messageはブラウザごとに異なる）。
- File System Access API（`showSaveFilePicker` / `showOpenFilePicker` / `showDirectoryPicker`）は `api/file-response-handler.js` だけで使う。
  - Chrome / Edgeのみ対応で、Firefox / Safari / モバイルは未対応。分岐が散るとフォールバックの挙動が場所によってずれる。
  - 未対応ブラウザとキャンセルは別の状態として返す。同じ値へ寄せると、未対応ブラウザがキャンセル扱いになり何も起きなくなる。
  - transient activation（利用者操作の直後）を要求するため、ピッカーはクリック直後に呼ぶ。`fetch` の完了後では失効している。
- 機能拡張は当面 BE first で進める。
  - API / Service / Logic / DTO / JUnit / OpenAPI を先に固めてから FE を接続する。
  - FE先行で作り込むとAPI仕様変更の手戻りが増えやすいため、PDF処理ロジックを先に安定させる。
  - Markdown / AI系の拡張も、最初はVite移行を前提にせず、BE APIと既存画面の最小UIで検証する。
- FE は当面 WebJar Vue 3.2.37 を継続する。
  - 一般公開をまだ前提にしない間は、WebJar Vue のままで問題ない。
  - `main.html` 1画面で扱える規模の間は、npm / Vite 移行を急がない。
  - Vueは `/webjars/vue/3.2.37/dist/vue.global.js` をブラウザ配信する用途に限定し、`@vue/*` の推移WebJarへ依存しない。
  - npm / Node.js / TypeScript / Vite 移行は、画面数や状態管理が増えたタイミングで別フェーズとして扱う。
  - Vuetify などのUIライブラリは、Vite + TypeScript の足場が安定した後に検討する。
- `pdf.js` はページ単位の高度なプレビュー操作が必要になるまで導入しない。
  - ブラウザ標準PDF表示とPDF/ZIPレスポンスで足りる間は、PDF表示基盤を増やさない。
  - ページサムネイル、ページ単位選択、範囲指定UI、テキストレイヤー、注釈表示が必要になった段階で検討する。
- Vue template / HTMLでは、`button` に `type` を明示する。
- Vue template / HTMLでは、`v-for` に `:key` を明示する。
- Vue template / HTMLでは、inline `style` を増やさずCSSクラスへ切り出す。
  - 既存の外部サンプルHTMLなど、リファクタリング対象外の素材は例外として扱う。
- Vueで扱う画面状態やフォーカス制御は、DOM直接操作ではなくVueのstate / ref / class bindingへ寄せる。
  - `document.getElementById` / `document.querySelector` などで要素を直接探さない。
  - `innerHTML` / `outerHTML` / `insertAdjacentHTML` によるHTML直接挿入は使わない。
- ファイルパスやローカル環境に依存する絶対パスを本番コード・テストに埋め込まない。
- テストで必要なファイルは `src/test/resources` か classpath resource から読み込む。

### 過去メモ: PDF一時保存先 / tmp ディレクトリ確認用デバッグ出力

過去に `GhostPdfController` / `GhostPdfLogic` に残っていた標準出力は、PDFアップロード時に「Springのmultipart一時保存先」「JVMのデフォルトtmp」「アプリ側で生成したPDF一時ファイルパス」を確認する目的だった。

当時の主な確認内容:

```java
System.out.println("PATH_DIRECTORY:" + PATH_DIRECTORY);
System.out.println(System.getProperty("java.io.tmpdir"));
System.out.println("folderPath:" + folderPath);
System.out.println("fileName:" + fileName);
System.out.println("一時保存したファイルを削除する。" + originalFilePath);
System.out.println("一時保存したファイルを削除する。" + inputPath);
System.out.println("一時保存したファイルを削除する。" + insertPdfDto.getInsertPath());
```

現行実装では、`spring.servlet.multipart.location` は `application.yml` で `/tmp` を指定する。
`GhostPdfLogic` が設定値を `tmpDirectory` として保持し、`PdfTemporaryFileStorage#createTemporaryFilePath` が一時保存先パスを生成する。
同じ観点を確認したい場合は、標準出力ではなく次のように debug log にする。

```java
LOGGER.debug("JVMデフォルト一時ディレクトリを確認します。javaIoTmpDir={}",
		System.getProperty("java.io.tmpdir"));
LOGGER.debug("PDF一時保存先ディレクトリを確認します。tmpDirectory={}", tmpDirectory);
LOGGER.debug("PDF一時ファイルパスを作成しました。path={}", outputPath);
LOGGER.debug("PDF一時ファイルを削除します。path={}", path);
```

注意:

- `System.getProperty("java.io.tmpdir")` はJVMのデフォルトtmpであり、必ずしもSpring multipart設定の保存先とは一致しない。
- Ghost-PDF5のPDF一時保存先は、基本的に `spring.servlet.multipart.location` → `GhostPdfLogic#tmpDirectory` → `PdfTemporaryFileStorage#createTemporaryFilePath(...)` の流れで確認する。
- 本番コードへ一時確認ログを残す場合も `debug` にし、ローカルパスやファイル名を不要に `info` / `warn` へ出さない。

Redmine等の備考欄へ残す場合のメモ:

```text
Javaでシステム上の一時ディレクトリ/JVMデフォルトtmpを確認する場合は、
System.getProperty("java.io.tmpdir") を使用する。

ただし、これはJVMのデフォルトtmp確認用であり、Spring Bootのmultipartアップロード保存先とは
必ずしも同じではない。Ghost-PDF5のPDF一時保存先は application.yml の
spring.servlet.multipart.location、GhostPdfLogic の tmpDirectory、
PdfTemporaryFileStorage#createTemporaryFilePath(...) の順に確認する。
処理内の folderPath は、spring.servlet.multipart.location 由来の
PDF一時保存先ディレクトリを確認するための値で、PdfTemporaryFileStorage が
temporaryDirectory から Paths.get(temporaryDirectory) で生成する。
一時確認ログを入れる場合は System.out.println ではなく LOGGER.debug(...) を使用する。
```

## 命名ルール

- クラス名は責務が分かる名詞にする。
  - 例: `GhostPdfController`, `GhostPdfService`, `PdfProcessingException`
- メソッド名は動詞から始め、何をするか分かる名前にする。
  - 例: `showSamplePage`, `buildInsertPdfDtos`, `readBase64Resource`
- service層でDTOへ変換する場合は、処理本体へsetterを直接並べず、`private build〇〇` メソッドへ切り出す。
  - 例: `buildInsertPdfDto`, `buildInsertPdfDtos`
- typo を含む public メソッドは利用箇所を確認して整理する。
  - 利用がある場合は正しい名前のメソッドを追加し、typoメソッドは `@Deprecated` を付けて新メソッドへ委譲する。
  - 利用がなく、正しい名前の代替が既にある場合は削除する。
- boolean を返すメソッドは `is`, `has`, `can` などで始める。
- 一時変数も意味が分かる名前にする。
  - 悪い例: `list`, `map`, `data`
  - 良い例: `mergeSegments`, `remainingPages`, `insertPdfDtos`

## Javadoc / JSDoc / コメント

- public class / public method には Javadoc を書く。
- class のJavadocには、そのクラスが何を担当するか、どの層・用途で使うかを1〜2文で書く。
- `@author` はGit履歴と重複するため使わず、責務・境界・互換性の説明を優先する。
- private method でも、処理意図や仕様上の注意がある場合は Javadoc を書く。
- Javadoc には最低限、以下を含める。
  - 何をするか
  - `@param`
  - `@return`
  - 例外を投げる場合は `@throws`
- `//` コメントは「なぜそうしているか」を書く。コードを読めば分かる処理説明だけのコメントは増やしすぎない。
- 単純な代入、return、並び替えなど、コードを読めば分かる処理には `//` コメントを付けない。
- Javaファイル全体をコメントアウトして残さない。使わない設定クラスや旧実装は、Git履歴で追えるため削除する。
- AIレビュー対策として、互換維持・既存仕様・1始まり/0始まり変換など、誤解されやすい仕様はコメントに残す。

## Lombok / DTO / enum のルール

- PDF機能の request / response / DTO は `pdfcontent.dto` に配置し、旧 `pdfcontent.model` package は使わない。
- `@Data` は使わない。必要な機能だけを `@Getter` / `@Setter` / `@NoArgsConstructor` / `@AllArgsConstructor` で明示する。
  - `@Data` は `equals` / `hashCode` / `toString` まで生成するため、意図しない比較・ログ出力を避ける。
- Controller / Service / Component の依存注入は、依存フィールドを `final` にし、原則 `@RequiredArgsConstructor` を使用する。
  - 現場の実装スタイルと合わせ、constructor injectionを短く保つ。
  - コンストラクタ内で検証・変換・初期化処理が必要な場合のみ、明示コンストラクタを書く。
  - field injection の `@Autowired` は使わない。
- 内部クラスでも、`final` フィールドを引数からそのまま設定するだけの場合は `@RequiredArgsConstructor` を使用する。
- 状態を持たず、明示的な空コンストラクタだけが必要な内部クラスは `@NoArgsConstructor` を使用する。
- 値の正規化、派生オブジェクト生成、親クラスコンストラクタ呼び出しが必要な場合は明示コンストラクタを維持する。
- request / response / DTO の public フィールド名や型は、既存API互換に影響するため慎重に変更する。
- service層でrequestからDTOへ変換する場合は、`private build〇〇` メソッドにデフォルト値や変換ルールを集約する。
- ServiceImpl化は現場ルールや複数実装が必要な場合に検討する。単一実装だけの段階では、interface + implを機械的に増やさない。
- LogicクラスはPDFBoxなど外部ライブラリ依存の詳細を閉じ込める層として扱い、分割する場合も既存public APIをFacadeとして維持する。
- request / form / DTO のフィールドに `Optional<T>` は原則使わない。
  - 特に `Optional<MultipartFile>` はSpring MVC bindingやOpenAPI schemaが分かりづらくなるため使わない。
  - 未指定を表す場合は `null` / `isEmpty()` をservice層で明示的に扱う。
- 数値や文字列の区分値が複数箇所に出る場合は、enum化を検討する。
  - 既存APIが数値/文字列を受け取っている場合、外向き仕様は維持し、Java内部の分岐だけenumへ寄せる。
  - annotation の `min` / `max` などコンパイル時定数が必要な箇所では、互換用定数を残してよい。
- enum化する場合は `CodeEnum<T>` を実装し、参考BEと同じ考え方で `key` と `value` を持たせる。
  - `key`: API、フォーム、DBなど外向きに使うコード値。
  - `value`: 画面表示、ログ、説明に使うラベル。
  - `getKey()` には必要に応じて `@JsonValue` を付け、JSONでは既存のコード値として扱えるようにする。
  - `fromKey(...)` には必要に応じて `@JsonCreator` を付け、コード値からenumへ変換できるようにする。
  - `KEY_MAP` を用意し、分岐ごとに `if` / `switch` でコード値を直接比較しない。
  - `null` や未定義値は `IllegalArgumentException` など明確な例外にする。

## StringUtils / CollectionUtils の使用ルール

### StringUtils

`org.apache.commons.lang3.StringUtils` を使うことで null 安全に書ける箇所は、意味が変わらない範囲で置き換える。

- `str != null && !str.isEmpty()` は `StringUtils.isNotEmpty(str)` を使う。
- `str == null || str.isEmpty()` は `StringUtils.isEmpty(str)` を使う。
- `str != null && !str.isBlank()` は `StringUtils.isNotBlank(str)` を使う。
- `str == null || str.isBlank()` は `StringUtils.isBlank(str)` を使う。
- 文字列比較は null 安全のため `StringUtils.equals` / `StringUtils.equalsIgnoreCase` を優先する。
- 大文字小文字を無視した包含判定は `StringUtils.containsIgnoreCase` を使う。

注意:

- `isNotEmpty` は `null` と空文字だけを除外する。空白だけの文字列は true。
- `isNotBlank` は `null`、空文字、空白だけの文字列を除外する。
- 空白だけを有効値として扱う仕様の場合、`isNotBlank` に置き換えない。

### CollectionUtils

`org.apache.commons.collections4.CollectionUtils` を使うことで null 安全に書ける箇所は、意味が変わらない範囲で置き換える。

- `list != null && !list.isEmpty()` は `CollectionUtils.isNotEmpty(list)` を使う。
- `list == null || list.isEmpty()` は `CollectionUtils.isEmpty(list)` を使う。
- null を空コレクションとして扱ってループしたい場合は `CollectionUtils.emptyIfNull(collection)` を使う。

注意:

- null と空リストの扱いを区別する仕様では、安易に `emptyIfNull` に置き換えない。
- コレクションを変更する可能性がある処理では、`emptyIfNull` の戻り値を変更しない。

## JsonUtils の使用ルール

JSON変換では、失敗時の扱いが呼び出し側から分かるメソッドを選ぶ。
Ghost-PDF5では `JsonUtils` に失敗時null返却のpublicメソッドを増やさない。

- 失敗しても処理を継続したい場合は `Optional` を返す `try` 系を使用する。
  - `tryToJson(...)`
  - `tryParse(...)`
  - `tryConvertValue(...)`
- 失敗をバグまたは異常系として扱い、呼び出し元で止めたい場合は `OrThrow` 系を使用する。
  - `toJsonOrThrow(...)`
  - `parseOrThrow(...)`
  - `convertValueOrThrow(...)`
- `strFormatByJson(...)` / `jsonParse(...)` / `convertValue(...)` のような失敗時null返却メソッドは新規追加しない。
- `*JsonBytes` のように、戻り値がStringなのかbyte[]なのか読み違えやすい名前は使わない。
- `Optional` は戻り値として失敗可能性を表す用途に限定し、request / form / DTO のフィールドには使わない。
- 変換失敗の握りつぶしが必要な場合も、warn/debugログに失敗理由を残す。

## 保存先ディレクトリのルール

- 利用者の成果物（保存したMarkdownなど）を `java.io.tmpdir` 配下へ置かない。
  - OSが自動削除する。Windowsのストレージセンサーは既定で有効で、空き容量が少ないほど積極的に消す。
  - 既定値はホーム配下（`${user.home}/ghost-pdf5/...`）にし、環境変数で上書きできるようにする。
- 一方、処理途中の一時ファイル（アップロードPDF、加工後PDF、ZIP）は一時ディレクトリでよい。
  - `spring.servlet.multipart.location` 配下に置き、処理後に削除する既存の流れを維持する。
- 設定ファイルにローカル絶対パス（`C:\pr-work\...` など）を書かない。
  - 公開リポジトリのため、他のPCで動かなくなるうえディレクトリ構成が読み取れてしまう。
  - 環境依存の値はプロパティのプレースホルダ（`${user.home}` など）と環境変数で解決する。
- 既定値が2箇所（`application.yml` と `@Value` のfallback）にある場合は両方そろえる。
  - 片方だけ直すと、設定を書かない環境で古い既定値に戻る。

## ファイル操作 / Utils のルール

ファイル操作は `java.nio.file.Path` / `java.nio.file.Files` を基本にする。
ディレクトリコピーなど Commons IO が読みやすく安全な箇所では `commons-io` を使う。

- 新規コードでは `String` パスより `Path` を優先する。
- パス文字列、拡張子、ファイル名の処理は `PathUtils` を使う。
- ファイル/ディレクトリの作成、コピー、移動、削除は `FileOperationUtils` を使う。
- ファイル一覧、件数、サイズ、行数、内容検索は `FileInfoUtils` を使う。
- 旧Facadeの `StorageUtils` は削除済み。新規コードでは責務別Utilsを使用する。
- ディレクトリ作成は `Files.createDirectories` を使い、親ディレクトリもまとめて作る。
- `Files.lines` や `Files.walk` は try-with-resources で閉じる。
- 削除処理は対象パスを明確にし、広すぎるパスを削除しない。
- `FileOperationUtils` では、コピー元・移動元・削除対象が存在しない場合は原則no-opにし、繰り返し実行しても壊れにくい操作にする。
- コピー先・移動先のように処理継続に必須のパスが `null` の場合は、`IllegalArgumentException` で早めに止める。
- 再帰的なディレクトリ削除は `commons-io FileUtils.deleteDirectory(...)` など読みやすい既存APIを優先し、独自再帰処理を増やさない。

### Markdown / AI / CSV 拡張時のUtils利用

将来、PDFテキスト抽出、Markdown保存、AI要約、Vector DB投入、CSV/Excel/Word出力を追加する場合も、PDF編集コアへすべての責務を混ぜない。

- PDF加工そのものは `pdfcontent` の責務に閉じる。
- PDFテキスト抽出、Markdown保存、AI要約の最初の実装は、Vite移行を前提にせずBE側から小さく追加する。
  - `POST /textPdf` はPDFBoxで抽出できる素のテキストを返す薄いAPIとして扱い、Markdown化やAI整形を混ぜない。
  - `POST /saveMarkdown` はMarkdown本文の保存だけを扱い、PDF抽出、Markdown整形、AI要約を混ぜない。
- Markdown保存、一覧、プレビュー、編集はPDF加工とは別責務として扱う。
- Markdownライブラリは保存だけの段階では追加しない。HTMLプレビューやMarkdown->PDFなど、具体的な表示/変換要件が出た段階で選ぶ。
- PDF API以外の画面連動APIでも、同じ画面セッションの `access-token` 検証は `AccessTokenValidator` に集約する。
- AI連携はClaude / OpenAI / ローカル処理を直接Controllerへ書かず、AI用のServiceやAdapterへ閉じ込める。
- CSV / openCsv は、学習目的だけでGhostPdf本流へ入れない。
- CSVを使う場合は、文書メタ情報、変換結果、AI処理結果、テスト観点、Vector DB投入状況などの入出力・レポート用途に限定して検討する。
- CSV処理を追加する場合は、PDF編集APIと混ぜず、CSV専用のService/UtilsとJUnitを用意する。
- `PathUtils` はPDFだけでなく、Markdown / CSV / Excel / Word の拡張子判定や保存ファイル名生成にも利用する。
- `FileInfoUtils` は保存済みMarkdown一覧、ファイルサイズ、行数、内容検索、Vector DB投入対象の列挙に利用する。

詳細な将来方針は `docs/future-document-ai-roadmap.md` を参照する。

Utils整理:

- `String` パス引数だけの互換Facadeは増やさない。必要な箇所で `Path` に変換し、責務別Utilsを呼ぶ。
- typo メソッドは互換目的でも増やさない。発見した場合は利用箇所を正しい名前へ寄せ、未使用なら削除する。
- 削除済みの `StorageUtils` は復活させない。必要な処理は `PathUtils` / `FileOperationUtils` / `FileInfoUtils` に追加する。
- Utilsを新規作成する場合は、標準API / commons-io / commons-lang3 / commons-collections4 で足りないプロジェクト固有ルールがある場合に限定する。
- UUID生成のように `java.util.UUID.randomUUID()` で十分明確な処理は、専用Utilsで包まない。

## ライブラリ追加判断

現時点では、PDF処理・文字列/コレクション処理・ファイル操作に必要な主要ライブラリは導入済み。
新しいライブラリは「既存の標準機能や導入済みライブラリで安全に書けない理由」がある場合に追加する。

既存利用を優先するもの:

- 文字列: `commons-lang3`
- コレクション: `commons-collections4`
- ファイル操作: `java.nio.file` / `commons-io`
- PDF処理: PDFBox
- テスト: `spring-boot-starter-test` に含まれる JUnit / Mockito / AssertJ
  - Java 25ではMockito inline mock makerを自己attachさせず、Surefireの `argLine` で `mockito-core` をjavaagentとして指定する。
- Lombokを使う場合は、Java 25コンパイルでgetter/setter生成が抜けないよう、Maven Compiler Pluginの `annotationProcessorPaths` へ `lombok` を明示する。
- OpenAPI UI: `springdoc-openapi-starter-webmvc-ui` を追加する場合は、Spring Boot / Spring Framework と互換のあるバージョンを選ぶ。
  - Ghost-PDF5はJava 25 / Spring Boot 4.0.7 / Spring Framework 7.0.x / Springdoc 3.0.3 の組み合わせで検証する。
  - Spring Boot 4.xではSpringdoc 3.xを使い、`OpenApiDocumentationTest` で公開API契約を確認する。
  - `LiteWebJarsResourceResolver` の `NoClassDefFoundError` が出る場合は、SpringdocとSpring Frameworkの互換性を疑う。

Jackson利用ルール:

- Spring Boot 4の新規mapper / databind / core実装は `tools.jackson` のJackson 3 APIを使う。
- `JsonProperty` / `JsonCreator` / `JsonValue` 等のannotationは、Jackson 3でも
  `com.fasterxml.jackson.annotation` を使う。annotation importを機械的に `tools.jackson` へ変更しない。
- `JsonUtils` の既存Jackson 2 `TypeReference` public overloadは、破壊的変更方針を決めるまで維持する。
- 新規generic parseにはJackson 3 `TypeReference` overloadを使い、Jackson 2 overloadを増やさない。
- Jackson 3の例外階層をそのまま外へ漏らさず、`JsonUtils` の既存 `IllegalArgumentException` 契約を維持する。
- `spring-boot-jackson2` は使用しない。既存Jackson 2 `TypeReference` publicシグネチャ用の
  `com.fasterxml.jackson.core:jackson-core` だけを直接依存として維持する。
- 詳細は `docs/jackson-3-migration-design.md` を参照する。

追加候補の優先度:

1. ArchUnit
   - `@Data` 禁止、controller/service/logic の依存方向、DTO変換ルールなどをテストで自動検出したい場合。
   - Java 25のclass file major version 69を扱える1.4.2以降を使う。
2. JaCoCo Maven Plugin
   - カバレッジを継続的に見たい場合。
3. Spotless Maven Plugin または formatter plugin
   - フォーマット差分を自動整形したい場合。
4. MapStruct
   - DTO変換が増え、手書きの `build〇〇` が大きくなった場合。
   - ただし現状の小さな変換では追加しない。
5. Testcontainers
   - DB、S3、外部ミドルウェアを本物に近い形でテストする必要が出た場合。

## OpenAPI / Swagger のルール

- 将来REST化するPDF APIは、Springdocで確認できるように `@Operation` / `@ApiResponse` を付ける。
- PDF APIのような `multipart/form-data` + `application/pdf` レスポンスは、自動生成だけでは意図が伝わりにくいため、summary / description / media typeを明示する。
- `access-token` ヘッダー、multipartフォーム項目、共通エラーレスポンスは、Swagger UIで確認しやすいように `@Parameter` / `@Schema` / `@ApiResponse` で説明を補う。
- `413` をOpenAPIに定義するPDF APIは、Controller入口で対象MultipartFileのサイズ検証を行い、Serviceへ到達する前に `MultipartException` で止める。
- Thymeleafの画面表示用Controllerやサンプル用Controllerは、REST API仕様と混ざらないよう `@Hidden` でSwaggerから隠す。
- Swagger UIでは `/swagger-ui.html`、OpenAPI JSONでは `/v3/api-docs` を確認する。
- Swaggerの公開/非公開対象、ヘッダー、requestBody、response、DTO schemaは、可能な範囲で `/v3/api-docs` をMockMvcで確認するJUnitを追加する。
- 通常起動では `springdoc.api-docs.enabled=false` / `springdoc.swagger-ui.enabled=false` を明示し、必要なJUnitや手動確認時だけ有効化する。

## JSONレスポンス共通化の判断

- JSON成功レスポンスは共通ラッパー `ApiResult<T>`（`com.clip.ghost.common.response`）で包む。**導入済み**。
  - 導入理由は「成功したが伝えたいことがある」を返す場所を作ること。OCR / visionを入れて部分的成功が起きる機能になったため必要になった。
  - `CommonResponse` は意味が広すぎるため採用しない。
  - `ApiResponse<T>` はSpringdocの `io.swagger.v3.oas.annotations.responses.ApiResponse` と紛らわしいため避ける。
- 包む対象はJSON成功レスポンスだけ。
  - PDF/ZIP/CSVなどのファイルレスポンスは、Content-Type / Content-Dispositionを明示し、JSON共通ラッパーで包まない。
- ファイルレスポンスは `ResponseEntity<Resource>` でストリームとして返し、`byte[]` に載せない。
  - 出力サイズに比例してヒープを消費するため。27ページ・20.5MBのPDFを1ページずつ分割すると、埋め込みフォントがページごとに複製され出力ZIPは約192MBになる（実測、9.4倍）。これを `byte[]` へ読み込むと、一時ファイルとヒープ上のコピーが同時に存在する。
  - Content-Lengthは維持する（`Resource#contentLength()`）。付けないとブラウザの進捗表示が消え、FEから見た挙動が変わる。
  - サイズ取得でストリームを消費しないリソースを使う。`InputStreamResource` はContent-Length取得のために内容を読み切ってしまうため、ファイルベースのリソースを使う。
  - 一時ファイルの削除はレスポンス送信の完了後に行う。ストリームで返す場合、Serviceのメソッドを抜けた時点ではまだ送信中で、その場で削除するとレスポンスが壊れる。Springは本文を書き終えた後に入力ストリームを閉じるため、削除は `close()` に寄せる（`PdfTemporaryFileResource`）。
  - `StreamingResponseBody` でも削除位置は明示できるが、レスポンスが非同期になり既存のMockMvcテスト全体に `asyncStarted()` / `asyncDispatch()` が必要になるため採用しない。
  - エラー経路で `close()` が呼ばれず一時ファイルが残る可能性はゼロにできない。対象は一時ディレクトリ配下であり、稀な残留は許容する（利用者の成果物とは扱いが異なる。「保存先ディレクトリのルール」参照）。
  - エラーは既存の `ErrorResponse`（`fieldErrors` 形式）に任せ、`ApiResult<T>` にERRORを混ぜない。
- ラップはservice層で行い、controllerでは包まない。DTO生成は `private build〇〇` に集約したまま、`return ResponseEntity.ok(ApiResult.of(build〇〇(...)))` の形で包む。
- `ApiResult<T>` の構造は次のとおり。

```java
@Schema(description = "JSON APIの成功レスポンス共通ラッパー。")
@Getter
public class ApiResult<T> {
    @Schema(description = "レスポンスデータ。データなしの場合のみnull。")
    private final T data;

    @Schema(description = "結果種別。通常はINFO、注意喚起を含む成功時はWARNING。")
    private final ApiResultType resultType;

    @Schema(description = "画面表示用メッセージ。未指定時は空リスト。")
    private final List<ApiMessage> messageList;

    private ApiResult(T data, ApiResultType resultType, List<ApiMessage> messageList) {
        this.data = data;
        this.resultType = resultType;
        this.messageList = List.copyOf(messageList);
    }

    public static <T> ApiResult<T> of(T data) {
        return new ApiResult<>(data, ApiResultType.INFO, List.of());
    }

    public static <T> ApiResult<T> of(T data, List<ApiMessage> messageList) {
        return new ApiResult<>(data, ApiResultType.INFO, messageList);
    }

    public static <T> ApiResult<T> warning(T data, List<ApiMessage> messageList) {
        if (messageList.isEmpty()) {
            throw new IllegalArgumentException("messageList must not be empty for WARNING.");
        }
        return new ApiResult<>(data, ApiResultType.WARNING, messageList);
    }

    public static ApiResult<Void> empty() {
        return new ApiResult<>(null, ApiResultType.INFO, List.of());
    }
}

public enum ApiResultType {
    INFO,
    WARNING
}

public record ApiMessage(String code, String message) {
}
```

- `ApiResult<T>` を扱う際の注意:
  - public setterは持たせず、`of(...)` / `warning(...)` / `empty()` のfactoryで生成する。
  - `messageList` はnullにせず、未指定時は空リストにする。コンストラクタで `List.copyOf` して防御的にコピーする。
  - `WARNING` は `warning(data, messageList)` で生成する。何を注意すべきか伝えられないWARNINGは役に立たないため、メッセージ空での生成は `IllegalArgumentException` で弾く。
  - メッセージコード体系が必要になるまでは、`ApiMessage` は最小の `code` / `message` に留める。
  - Springdocのgeneric schema表現は崩れやすいため、`/v3/api-docs` のJUnit（`OpenApiDocumentationTest`）で `ApiResult〇〇` のschema名と `data` の中身を固定する。
  - **200の `@ApiResponse` に `content = @Content(...)` を書かない。** 書くと戻り型からのschema推論が上書きされ、200のschemaが空になる。`description` だけを指定すれば、Springdocが `ApiResult〇〇` を生成して `$ref` を張る。エラーcodeの `@ApiResponse` は従来どおり `schema = @Schema(implementation = ErrorResponse.class)` を明示する。
- `WARNING` を返す条件は次のとおり。**導入済み**（`mode=AUTO` の一部ページ変換失敗、`POST /saveMarkdown` の上書き）。
  - `WARNING` は成功時だけに使う。処理を完了できなかった場合はHTTP statusとエラーJSONで返し、200 + `WARNING` にしない。
  - 部分的な失敗は「1件以上成功した」場合だけ `WARNING` にする。**全滅は警告ではなく失敗**として、従来どおり例外で止める。
    - 対象0件（そもそも失敗しうる処理をしていない）と全滅を混同しない。文字レイヤーだけのPDFは変換対象0ページのため常に成功。
    - 全滅時は独自例外へ包み直さず、最初の失敗をそのまま伝播させる。包み直すとHTTP statusが変わる。
  - 部分的な失敗を許す処理は、失敗した対象を記録して残りを続ける。1件の失敗で全体を捨てると、外部AIへ課金して得た成功分まで失われる。
  - 失敗した対象は、成功して結果が空だった場合と区別できる値で返す（例: `source = FAILED`）。区別できないと利用者が失敗に気付けない。
  - 例外の内容はSLF4Jの `warn` でログへ出し、画面へは出さない。メッセージは `ApiMessage` の `code` / `message` だけで伝える。
  - 上書きのような「利用者の意図どおりだが伝えるべきこと」は、挙動を変えずに事後通知する。禁止して操作を止めない。
- フロントエンドはラッパーの構造（`data` / `resultType` / `messageList`）を `api/api-result-utils.js` だけで解釈する。
  - api clientごとにラッパーを直接読むと、構造変更時の修正漏れが起きるため。`CodingConventionTest` のソーススキャンで他ファイルからの参照を検出する。
  - `messageList` の表示は**モーダルではなくインライン**にする（`components/api-message-list.js`）。モーダルはエラー用で、処理が終わった後の通知で操作を止めても利用者にできることが増えない。
    - 表示位置は対応する操作の近く（Markdown下書き・保存はMarkdownメモパネル内、編集欄の上）。
    - 見た目は `main.css` の `.api-message-list` に置く。inline styleは使わない。
    - 通知は次の操作で消す。`this.errorMessages = []` と同じ位置で `clearApiMessages()` を呼ぶ。
    - 表示側はメッセージの内容を解釈せず並べるだけにする。`WARNING` を返すAPIが増えても画面を触らずに済ませるため。
    - 接続は `FrontendApiMessageContractTest` のソーススキャンで固定する（JSのテストランナーが無い構成のため）。
- `ErrorResponse` は既存のエラーJSON仕様として維持し、成功レスポンス共通化と同時に置き換えない。
- `JsonUtils` はJSON文字列変換・parse・オブジェクト変換の補助であり、APIレスポンス構造を定義するクラスではない。
  - Controllerの通常JSONレスポンスはSpring MVC / Jacksonに任せ、`JsonUtils.toJsonOrThrow(...)` で手動JSON文字列を作って返さない。
  - `ApiResult<T>` も `JsonUtils` へ依存させず、通常のResponse DTOとしてSpring MVC / Jacksonに任せる。

## PDF処理のルール

- PDFBox 3.x を使用する。
- PDF読み込みは `Loader.loadPDF` を使用する。
- `PDDocument` は try-with-resources で必ず close する。
- ページ番号は画面・リクエストでは1始まり、PDFBox内部では0始まりであることをコメントに残す。
- OpenPDF / `com.lowagie` 系 import は追加しない。
- 結合、挿入、置換、末尾挿入、ページ削除の既存仕様を変更しない。

## PDF分割範囲の入力形式

- 範囲ごとの分割（`POST /splitPdf` の `splitRanges`）は `List<String>`（`["1-5", "6-12", "13"]`）で受け取る。
  - `List<Integer>`（`extractPages` と同じ形式）では範囲を表現できない。`[1,2,3,4,5]` からは「1-5を1ファイル」と「1ページずつ5ファイル」を区別できないため。
  - 区切りページ指定（`[6, 13]`）は「1-5と13-20だけ欲しい（6-12は不要）」を表現できず、後で作り直しになる。
  - 1つの文字列（`"1-5, 6-12"`）ではエラー箇所の特定が粗くなる。1件ごとに検証・エラー返却できる形式を採る。
  - `POST /extractPdf` の `extractPages` は `List<Integer>` のまま変えない。ページ集合の指定であり、範囲という単位を必要としない。
- 範囲の重複は**禁止**する。同じページが複数ファイルへ入ると、どちらを使うべきか利用者が判断できない。
- 範囲の件数には上限を設ける（`@CheckPageRangeList` の `max`、既定50件）。範囲の件数は出力ファイル数、つまりZIPサイズに直結する。1ページずつの分割は範囲未指定で行えるため、範囲指定に大きな上限は要らない。
- 検証は形式とページ数で層を分ける。
  - 形式・大小関係・重なり・件数はannotation（`@CheckPageRangeList`）で見る。PDFを開かずに判定できる。
  - 総ページ数との突き合わせはPDFを開く層（`PdfPageOperationLogic`）で行う。総ページ数はPDFを開くまで分からない。
  - どちらも400（`fieldErrors` 形式）で返し、**PDFを1回も加工せずに止める**。総ページ数との突き合わせはZIPを書き始める前に行う。書き始めてから弾くと、中身の無いZIPが一時ファイルとして残る。
  - 総ページ数超過は `PdfSplitRangeException`（400）で表す。`PdfProcessingException` は500のため、利用者が直せる入力エラーには使わない。
- 範囲未指定は「1ページずつ分割」として従来どおり動かす。ZIP内の命名も従来の `split-001.pdf` を変えず、範囲分割は `pages_1-5.pdf` と別系統にする。
- ダウンロードファイル名（`split.zip`）は範囲指定の有無で変えない。保存先と保存名は画面のピッカーで利用者が選べ、どの範囲のPDFかはZIP内のファイル名で分かる。

## JUnit / テスト方針

- バグ修正やリファクタリングをしたら、対応するJUnitを追加・更新する。
- controller/service/logic/utils の単位で、失敗しやすい境界値をテストする。
- controller テストでは `HttpSession` が必要な場合、`MockHttpSession` などで明示する。
- `MockMvcBuilders.standaloneSetup(...)` で十分なcontroller単体テストには `@SpringBootTest` を付けない。SpringContextが必要なテストだけ `@SpringBootTest` / `@AutoConfigureMockMvc` を使う。
- `@Mock` / `@InjectMocks` 中心のservice/controller単体テストにも `@SpringBootTest` を付けない。Mockitoだけで十分な場合は `MockitoExtension` を使う。
- Spring Context内のBean差し替えには、Spring Boot側で非推奨になった `@MockBean` ではなく、Spring Frameworkの `@MockitoBean` を使う。
- `@SpringBootTest` を使うテストには `@Tag("context")` を付ける。
  - Spring Context起動は重いため、通常の単体テストと区別できるようにする。
  - OpenAPI定義確認のように用途が明確なものは、追加で `@Tag("openapi")` なども付ける。
- 普段の軽量確認では `mvn test -Pfast-test` を使い、push前や大きめの変更後は `mvn test` で全件確認する。`fast-test` profileは `context,openapi` タグを除外する。
- PDFテストではローカル絶対パスを使わない。`src/test/resources/pdf` または classpath resource を使う。
- ファイル操作テストでは `@TempDir` を使う。
- 例外処理を変更した場合、HTTP status、response body、ログが過剰に出ないことを確認する。
- `mvn test` を最終確認として実行する。
- コーディング規約のうち自動検出できるものは `CodingConventionTest` に追加する。
  - クラス依存で検出できる規約はArchUnitで検証する。
  - `@Data` のようにSOURCE retentionでコンパイル後に消えるものや、固定パス文字列はJUnitのソーススキャンで検証する。
  - field injection の `@Autowired` はJUnitのソーススキャンで検証する。
  - JavaScriptのStorage直接参照やdeprecated utility aliasなど、文字列として検出しやすい規約もJUnitのソーススキャンで検証する。
  - JavaScriptの `fetch` / `FormData` / `Headers` 直接利用は、FetchClient迂回としてJUnitのソーススキャンで検証する。
  - File System Access APIの呼び出しが `api/file-response-handler.js` の外へ漏れていないかもJUnitのソーススキャンで検証する。
  - JavaScriptの `fieldErrors` 直接参照は、APIエラー表示変換の分散としてJUnitのソーススキャンで検証する。
  - JavaScriptのDOM直接操作やHTML直接挿入など、Vueのstate管理から外れやすい実装もJUnitのソーススキャンで検証する。
  - `window.open` を使う場合は、`noopener` 漏れもJUnitのソーススキャンで検証する。
  - Spring Boot側の非推奨 `@MockBean` の再混入もJUnitのソーススキャンで検証する。
  - springdocの `/v3/api-docs` / `/swagger-ui.html` が通常起動で公開されない設定もJUnitのソーススキャンで検証する。
  - ルールが増える場合も、ArchUnitで見られるものとソーススキャンが必要なものを分ける。

## フェーズ管理 / 大きな変更の分離

今回のPDFBox移行後安定化は、BE/PDF中核のテスト固定、FE Phase 6棚卸し、docs/README/規約整理まで完了済みとして扱う。
package renameは `pdfcontent` と `common` の責務別package構成へ整理済みとして扱う。
`GhostPdfLogic` の段階的分割も完了し、既存public APIと一時ファイルのライフサイクルを維持するFacadeとして扱う。
次の作業は差分が大きくなりやすいため、同じコミットや同じ流れに混ぜない。

- Spring Boot 4.x / Springdoc 3.x へのメジャーアップグレード。
- `GhostPdfService` の機械的な `ServiceImpl` 化。
- npm / Vite へのFE移行。
  - ただし、BE の PDF API が安定し、画面が増えた段階で別フェーズとして検討する。

上記を行う場合は、専用ブランチまたは専用コミットで扱い、先に `mvn test` が安定している状態を確認する。

## レビュー前チェックリスト

- [ ] public API / URL / フォーム項目名を不用意に変えていない。
- [ ] Controller / Service / Component の依存注入は `final` field + `@RequiredArgsConstructor` を基本にしている。
- [ ] 単純なコンストラクタはLombokを使い、変換・派生生成が必要な場合だけ明示実装している。
- [ ] field injection の `@Autowired` を使っていない。
- [ ] `System.out.println` / `printStackTrace` が残っていない。
- [ ] JavaScriptに `console.*` / `debugger` が残っていない。
- [ ] JavaScriptで `alert()` / `confirm()` / `prompt()` を使っていない。
- [ ] JavaScriptで `==` / `!=` を使っていない。
- [ ] JavaScriptでStorageを直接参照せず、専用の取得関数やutilityに閉じている。
- [ ] JavaScriptのHTTP通信は `api/fetch-client.js` を入口にしている。
- [ ] JavaScriptで `window.open` を使う場合は `noopener` を明示している。
- [ ] JavaScriptでDOM直接操作やHTML直接挿入を増やしていない。
- [ ] Vue template / HTMLの `button` に `type` がある。
- [ ] Vue template / HTMLの `v-for` に `:key` がある。
- [ ] Vue template / HTMLに不要な inline `style` が増えていない。
- [ ] OpenPDF / `com.lowagie` が残っていない。
- [ ] ローカル絶対パスが残っていない。
- [ ] `StringUtils` / `CollectionUtils` への置き換えで意味が変わっていない。
- [ ] ファイル操作は `Path` / `Files` / Commons IO を優先している。
- [ ] Javadoc / コメントで仕様上の注意点を説明している。
- [ ] 変更に対応するJUnitがある。
- [ ] `mvn test` が成功している。
