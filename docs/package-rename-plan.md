# Package Rename Record

## 結論

旧 `func` package から `funcs` への機械的な複数形化は行わず、責務が分かるpackage構成へ整理した。
PDF処理のURL、public method signature、フォーム項目名、PDFBox移行済み仕様は変更しない。

この記録で対象にしたrenameは、旧 `func` から `pdfcontent` / `common` への責務分割と、
`pdfcontent.model` から `pdfcontent.dto` への移動である。現行の `pdfcontent` / `markdowncontent` を
さらに `pdf` / `markdown` へ短縮することは必須ではなく、現状のままで問題ない。

## 実施条件

次の条件を確認してから、package rename専用ブランチで実施した。

1. PDFBox移行後のPDF仕様確認が完了している。
2. Controller / Service / Logic / Utils の主要リファクタリングが一段落している。
3. `mvn test` が継続してグリーンである。
4. ほかの大きなrenameやBoot upgradeと同時に行わない。
5. STS / Eclipse上でpackage移動後のimport整理を確認できる時間がある。

## 移行後package

`funcs` への単純変更ではなく、責務で分ける。

```text
com.clip.ghost.pdfcontent
com.clip.ghost.pdfcontent.dto
com.clip.ghost.common.utils
com.clip.ghost.common.validation
com.clip.ghost.common.exceptions
com.clip.ghost.architecture  // test only
```

最初の移行では深く分けすぎず、`pdfcontent` と `common` の境界を作るところに留めた。
その後、PDF機能のrequest / response / DTOは実体に合わせて `pdfcontent.dto` へ移動した。

## 実施内容

1. package rename専用ブランチを作る。
2. `git mv` でディレクトリ移動する。
3. package宣言とimportを機械的に更新する。
4. `CodingConventionTest` のpackage依存ルールを更新する。
5. README / docs のpackage名を更新する。
6. `mvn test` を実行する。
7. 差分確認では、実装変更が混ざっていないことを確認する。

## やらないこと

- PDFの挙動変更と同時にpackage renameしない。
- Spring Boot upgradeと同時にpackage renameしない。
- `func` を `funcs` にするだけのrenameはしない。
- URL、public method signature、フォーム項目名は変更しない。

## 検証観点

- `mvn test` が成功すること。
- 旧 `func` package参照が `src/main/java` / `src/test/java` に残っていないこと。
- `CodingConventionTest` の依存方向テストが新package構成で成功すること。
- STS / Eclipseで赤いコンパイルエラーが残っていないこと。
- Swaggerの `/v3/api-docs` テストが成功すること。

## コミット方針

package renameは差分が大きくなるため、専用コミットにする。

```text
refactor(package): 責務別package構成へ整理
```
