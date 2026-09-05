# Spring Boot 4移行事前監査

更新日: 2026-08-01  
対象基準コミット: `460a605 Connect page Markdown draft UI`  
状態: 事前監査、platform更新、非推奨API整理、Jackson 3移行Phase 3、Commons IO更新まで完了

## 1. 目的

Ghost-PDF5をSpring Boot 3.5系から4.0系へ移行する前に、必要な変更、互換性上の注意、
移行時に分けるべき作業を確認する。

この監査ではリポジトリ本体の `pom.xml` やJavaソースを変更せず、作業用コピーだけで
Boot 4への試験更新、全テスト、アプリケーション起動を行った。

## 2. 更新前と更新後の環境

| 項目 | 更新前 | 更新後 |
| --- | --- | --- |
| Java | 25.0.2 | 25.0.2 |
| Spring Boot | 3.5.16 | 4.0.7 |
| Spring Framework | 6.2.19 | 7.0.8 |
| Springdoc OpenAPI | 2.8.17 | 3.0.3 |
| Embedded Tomcat | 10.1.55 | 11.0.22 |
| Hibernate Validator | 8.0.3.Final | 9.0.1.Final |
| Commons Lang | 3.17.0 | 3.19.0 |
| Javaテスト | 284件成功 | 284件成功 |

Spring Boot 4はJava 17以上、Jakarta EE 11、Servlet 6.1を基準とする。
Ghost-PDF5はJava 25と `jakarta.*` APIを既に利用しており、この入口では追加移行を必要としない。

## 3. 試験した最小変更

最初にparentとSpringdocだけを更新したところ、旧packageの `WebMvcTest` が見つからず、
`OpenApiDocumentationTest` のtest compileで停止した。

次の変更を作業用コピーへ適用すると、全284テストが成功した。

1. Spring Boot parentを `3.5.16` から `4.0.7` へ変更する。
2. `spring-boot-starter-web` を `spring-boot-starter-webmvc` へ変更する。
3. test scopeに `spring-boot-starter-webmvc-test` を追加する。
4. Springdocを `2.8.17` から `3.0.3` へ変更する。
5. `WebMvcTest` importを次のpackageへ変更する。

```java
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

`spring-boot-starter-test` はJUnit、Mockito、Spring Test等の共通テスト基盤として維持し、
MVC test slice用の `spring-boot-starter-webmvc-test` を追加する構成とした。

## 4. 試験結果

### 4.1 コンパイルとテスト

- main Java 52ファイルがコンパイル成功。
- test Java 31ファイルがコンパイル成功。
- `mvn test` は284件すべて成功。
- `ApplicationTests` のSpring Context起動に成功。
- `OpenApiDocumentationTest` はSpringdoc 3.0.3でも成功。
- PDFBox 3.0.7、Markdown保存・プレビュー、ページ単位Markdown下書きの既存テストは変更なしで成功。

以前の資料に記載した292件は、`target/surefire-reports` に残っていた旧レポートを含めた集計だった。
`clean` 後の現行テスト数は284件であり、以降はこちらを基準にする。

### 4.2 起動と設定

- Boot 4.0.7 / Java 25 / Tomcat 11.0.22でアプリケーション起動に成功。
- `spring-boot-properties-migrator` を試験時だけ追加して起動したが、`application.yml` の変更警告はなかった。
- `server.port`、`spring.servlet.multipart.*`、`springdoc.*`、`ghost.markdown.*` は現状のまま起動できた。
- properties migratorは診断専用であり、本移行完了後に残さない。

## 5. 必須変更

### 5.1 BootのWebモジュール再編

Boot 4ではWeb MVCのmoduleとstarterが明確に分かれた。
旧 `spring-boot-starter-web` は非推奨扱いのため、Ghost-PDF5では
`spring-boot-starter-webmvc` を使用する。

テスト側も `spring-boot-starter-webmvc-test` を明示し、`WebMvcTest` のpackageを更新する。
Ghost-PDF5で該当する旧importは `OpenApiDocumentationTest` の1箇所だけである。

### 5.2 Springdoc 3.x

Springdoc 2.xはSpring Boot 3向けであり、Boot 4では3.xへ更新する。
試験した3.0.3では既存OpenAPI JSON契約テストが成功したため、公開path、multipart schema、
response DTO、HTTP statusの既存仕様を維持できる見込みである。

## 6. Jackson移行上の注意

Boot 4の標準JSON基盤はJackson 3であり、試験環境には
`tools.jackson.core:jackson-databind:3.1.4` が入る。

一方、現行ソースにはJackson 2のdatabind/core APIを直接使う箇所がある。

- `JsonUtils`: `ObjectMapper`、`JsonProcessingException`、`TypeReference`
- `JsonUtilsTest`: `TypeReference`
- DTOテスト、enumテスト、OpenAPIテスト: `ObjectMapper` / `JsonNode`

`JsonProperty`、`JsonCreator`、`JsonValue` 等のannotation packageは、Boot 4試験でも
`com.fasterxml.jackson.annotation` のまま利用できた。

Springdoc 3.0.3のSwagger CoreがJackson 2を推移依存として持つため、何も明示しなくても試験は成功した。
しかし、アプリケーションの `JsonUtils` がSpringdocの推移依存へ暗黙に依存する状態は避ける。

初回Boot 4移行では、公開済みの `JsonUtils#tryParse(String, TypeReference)` 等を維持するため、
Boot公式の移行用 `spring-boot-jackson2` を明示的に追加する。この構成でも284テスト成功を確認した。
同moduleは将来削除予定の移行用機能なので、Boot更新後の独立フェーズでJackson 3対応を進める。

Jackson 3対応では、既存public methodを直ちに削除せず、互換API維持またはoverload追加を先に検討する。
ソース・依存関係の詳細監査、互換方法、作業用コピーでの検証結果は
[Jackson 3段階移行設計](jackson-3-migration-design.md) に記録した。

## 7. Boot 4で見える非推奨API

`showDeprecation` を有効にしたBoot 4 compileで、次を確認した。

### 7.1 Spring Framework

- `HttpStatus.PAYLOAD_TOO_LARGE`
  - `GlobalExceptionErrorHandler` で使用。
  - Boot 4更新後、同じ413を表す `HttpStatus.CONTENT_TOO_LARGE` へ置き換え済み。
  - 例外ハンドラーとControllerテストで、HTTP status、JSON形式、既存エラーコードを維持している。

### 7.2 Commons Lang 3.19

次の6呼び出しが非推奨になったため、更新済み。

- `AccessTokenValidator`: `StringUtils.equals`
- `FileInfoUtils`: `StringUtils.contains` / `containsIgnoreCase`
- `PathUtils`: `StringUtils.equalsIgnoreCase` 2箇所
- `MarkdownDocumentService`: `StringUtils.equals`

大小文字を区別する `equals` / `contains` は `Strings.CS`、無視する `equalsIgnoreCase` /
`containsIgnoreCase` は `Strings.CI` へ置き換えた。既存テストにアクセストークンと本文検索の
大小文字区別を追記し、対象59テストでnull、空文字、拡張子判定、ファイル名検証を含む挙動を確認した。
Commons Lang自体は非推奨ではなく、比較条件を明示する新APIへの移行である。

### 7.3 Java 25 / Lombok

Lombok annotation processing時の `sun.misc.Unsafe::objectFieldOffset` 警告はBoot 4試験でも残る。
現時点ではコンパイル・テストを妨げないためBoot 4のblockerではない。Lombokの管理version更新時に再確認する。

## 8. 依存関係上の補足

- Commons IOは2.11.0から2.22.0へ独立コミットで更新した。
- Boot 4.0.7はCommons IOのversionを管理しないため、version指定を単純に削除するとPOMエラーになる。
- ファイルコピー、移動、削除、パス処理、Markdown保存を含むJava 25の全286テストが成功した。
- PDFBox 3.0.7、CommonMark 0.29.0、jsoup 1.22.2、Bouncy Castle 1.79、ArchUnit 1.4.2は試験上の失敗なし。
- Undertow、Spring Security、Spring Data、Actuator、Spring Sessionは現行依存にないため、今回の主要監査対象外。

## 9. 推奨する実施順序

### Phase 1: 事前監査

今回の文書追加。依存versionや実装は変更しない。

### Phase 2: platform更新（完了）

事前監査どおり、次だけを同じ作業単位で変更した。

- Spring Boot 4.0.7
- `spring-boot-starter-webmvc`
- `spring-boot-starter-webmvc-test`
- Springdoc 3.0.3
- 移行用 `spring-boot-jackson2`
- `WebMvcTest` import 1箇所

確認項目:

- Java 25で `mvn clean test`
- 実行件数284、failure/error 0
- 通常起動
- `main.html` 表示
- sample PDF表示
- PDFテキスト抽出とMarkdown下書き
- Markdown保存・一覧・読込・プレビュー・更新・削除
- OpenAPI契約テスト

### Phase 3: 非推奨API整理（完了）

Springの413定数とCommons Langの文字列比較・検索APIを、挙動を変えず独立コミットで更新した。

### Phase 4: Jackson 3移行設計（完了）

`JsonUtils` のpublic API、23件のmain import、5件のtest import、Jackson 2 / 3依存ツリーを監査した。
作業用コピーでは既存Jackson 2 `TypeReference` をJava `Type` 経由でJackson 3 mapperへ橋渡しし、
Jackson 3の変換例外を既存の `IllegalArgumentException` へ包むことで、全284テストが成功した。

### Phase 5: Jackson 3実更新（完了）

最初の作業として `JsonUtils` の内部mapperと専用テストだけをJackson 3へ移した。
既存Jackson 2 `TypeReference` public overloadを維持し、Jackson 3 overloadを追加した状態で、
Java 25の全286テストが成功している。POMと移行用 `spring-boot-jackson2` は変更していない。

直接mapperを使うDTO / enum / OpenAPIテストもJackson 3へ更新した。
対象26テストとJava 25の全286テストが成功し、Jackson由来の非推奨compile警告も解消した。
最後に移行用 `spring-boot-jackson2` を削除し、既存publicシグネチャ用の
`com.fasterxml.jackson.core:jackson-core` だけを直接宣言した。
Java 25の全286テスト、依存ツリー、Boot 4.0.7 / Tomcat 11.0.22での通常起動、
トップ画面HTTP 200を確認した。Springdoc由来のJackson 2 databindは外部推移依存として残る。

### Phase 6: Commons IO更新（完了）

Boot 4更新とJackson 3移行を完了した後、Commons IOを2.11.0から2.22.0へ単独で更新した。
Ghost-PDF5が利用する `FileUtils` / `FilenameUtils` のpublic APIは変更せず、
`mvn dependency:tree` で2.22.0の直接依存を確認した。
Java 25の `mvn clean test` は全286件成功した。

## 10. 結論

Ghost-PDF5のBoot 4 platform更新は完了した。

main実装の大規模修正は不要で、platform更新はPOMとテストimportを中心とした小さな差分に収めた。
Jackson 2のpublic API互換とBoot 4標準のJackson 3は別問題として扱い、段階的に移行した。
Spring / Commons Langの非推奨API整理、Jackson 3移行設計、`JsonUtils` 内部実装、
直接mapper利用テストのJackson 3化、移行module削除まで完了した。
Commons IO 2.22.0への更新も別の独立作業として完了した。

## 11. 参照資料

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [FasterXML Jackson project](https://github.com/FasterXML/jackson)
- [springdoc-openapi v3 documentation](https://springdoc.org/v4/index.html)
- [Apache Commons Lang Strings API](https://commons.apache.org/proper/commons-lang/apidocs/org/apache/commons/lang3/Strings.html)
- [Apache Commons Lang deprecated API list](https://commons.apache.org/proper/commons-lang/apidocs/deprecated-list.html)
- [Apache Commons IO Release Notes](https://commons.apache.org/proper/commons-io/changes.html)
- [Apache Commons IO Security Reports](https://commons.apache.org/proper/commons-io/security.html)
