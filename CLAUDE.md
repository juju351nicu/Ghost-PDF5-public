# CLAUDE.md

Ghost-PDF5でAIエージェント（Claude Codeなど）が作業するときの指示。
詳細な判断基準は [コーディング規約](docs/coding-guidelines.md) が正本で、このファイルはそこから外しやすい点だけを抜き出したもの。
規約とこのファイルが食い違う場合は規約を優先し、このファイルを直す。

## 最優先: 独自実装を増やさない

標準API・導入済みライブラリで安全に書ける処理を、自前のif文やループで書き直さない。
「数行だから自分で書いたほうが速い」は理由にならない。null の扱いが箇所ごとにぶれ、後から差分でしか気付けなくなる。

### 文字列: commons-lang3

- 空判定は `String#isEmpty()` / `String#isBlank()` を直接呼ばず、`StringUtils.isEmpty` / `isNotEmpty` / `isBlank` / `isNotBlank` を使う。
- `str == null || str.isEmpty()` のような手書きのnull判定を書かない。`StringUtils.isEmpty(str)` で足りる。
- `str.trim()` は `StringUtils.trim(str)`、区切り文字での分割は `StringUtils.split(str, separator)` を使う。
- 既定値の補完は `StringUtils.defaultIfEmpty` / `defaultIfBlank` / 1引数の `StringUtils.defaultString` を使う。

### コレクション: commons-collections4

- 空判定は `Collection#isEmpty()` を直接呼ばず、`CollectionUtils.isEmpty` / `isNotEmpty` を使う。
- `if (list != null) { list.forEach(...); }` は `CollectionUtils.emptyIfNull(list).forEach(...)` にする。
- `Map` は `CollectionUtils` の対象外。`MapUtils.isEmpty` を使う。

### 非推奨APIは使わない

commons-lang3 3.19 で `StringUtils` の比較・検索・置換系は非推奨になった。後継は `org.apache.commons.lang3.Strings`。
**本番コードでもテストコードでも、非推奨APIを新しく書かない。**

- `StringUtils.equals` / `equalsIgnoreCase` / `equalsAny` → `Strings.CS.equals` / `Strings.CI.equals` / `Strings.CS.equalsAny`
- `StringUtils.contains` / `containsIgnoreCase` → `Strings.CS.contains` / `Strings.CI.contains`
- `StringUtils.startsWith` / `endsWith` → `Strings.CS.startsWith` / `Strings.CS.endsWith` / `Strings.CS.endsWithAny`
- `StringUtils.replace` / `removeStart` / `removeEnd` → `Strings.CS.replace` / `Strings.CS.removeStart` / `Strings.CS.removeEnd`
- 2引数の `StringUtils.defaultString(str, defaultStr)` → `StringUtils.defaultIfEmpty` / `defaultIfBlank`
- `ObjectUtils.defaultIfNull` → 三項演算子か `StringUtils.defaultIfBlank`
- `"literal".equals(str)` のように定数を左へ置く書き方も `Strings.CS.equals(str, "literal")` へそろえる。

`StringUtils.isEmpty` / `isBlank` / `trim` / `split` / `lowerCase` / `defaultIfBlank` は非推奨ではない。
`StringUtils` 全体を避けるという意味ではない。

### 置き換えないもの

型が違うため、次はそのままにする。

- `MultipartFile#isEmpty()`（アップロード内容が空かの判定）、`Optional#isEmpty()`
- `Path` / `Resource` / 数値ラッパーなど、文字列でもコレクションでもない型の `null` 判定
- null と空文字を区別する仕様の判定。`PdfMarkdownDraftService` の `content.convertedText() != null` は
  「画像変換を実行したか」の判定で、`StringUtils.isNotEmpty` にすると取得元が `OCR` から `TEXT` へ変わる。

## 自動検出

上のルールは `CodingConventionTest`（ArchUnit + ファイルスキャン）で機械的に落とす。
違反するとクラス名と行番号が出るので、メッセージのとおりに直す。

- `productionCodeUsesStringUtilsForStringEmptyChecks`
- `productionCodeUsesCollectionUtilsForCollectionEmptyChecks`
- `codeDoesNotCallDeprecatedCommonsLang3Apis`（本番・テスト両方が対象）

`CodingConventionTest` には他にも `@Autowired` field injection禁止、`System.out` / `printStackTrace` 禁止、
public宣言へのJavadoc必須などの規約が入っている。新しい規約を足す場合もここへ追加し、ドキュメントだけで終わらせない。

## ビルドとテスト

```bash
./mvnw test
```

- 変更前にベースラインを取り、前後の差分で判断する。総件数は変更のたびに増えるため絶対値で判断しない。
- `MarkdownDocumentServiceTest` のシンボリックリンクテスト3件は、一般ユーザー権限ではskipされる。これは正常。
- `-Pfast-test` は `context` / `openapi` / `ocr` タグを除外する。最終確認はタグ除外なしで行う。

## コードを書くときの前提

- Javaソースの改行コードはLF。`.gitattributes` でリポジトリ全体をLFに統一している。CRLFで書き戻さない。
- インデントはタブ、1行は120桁まで。
- コメントとJavadocは日本語。public / protected の宣言にはJavadocを付ける。
- 「なぜそうしたか」をコメントに残す。動作の言い換えだけのコメントは書かない。
