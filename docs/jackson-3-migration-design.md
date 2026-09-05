# Jackson 3段階移行設計

更新日: 2026-08-01

対象: Java 25 / Spring Boot 4.0.7 / Springdoc 3.0.3

状態: Phase 1からPhase 3まで完了。移行用依存module削除、全テスト、依存ツリー、通常起動を確認済み

## 1. 目的

Spring Boot 4の標準JSON基盤であるJackson 3へ、Ghost-PDF5固有コードを段階的に移行する。
移行中も既存publicメソッドシグネチャ、HTTP JSON契約、PDFBox移行済み仕様を維持し、
移行用の `spring-boot-jackson2` を安全に削除できる状態を作る。

今回の設計・監査ではリポジトリ本体の `pom.xml` とJavaソースを変更しない。
実装は後続コミットで、内部実装、テスト、依存変更を分けて行う。

## 2. 公式仕様から確認した前提

- Spring Boot 4はJackson 3を推奨し、Jackson 2互換用に `spring-boot-jackson2` を用意している。
- `spring-boot-jackson2` は移行用の非推奨moduleであり、将来削除予定のため恒久利用しない。
- Jackson 3のJava packageは原則 `com.fasterxml.jackson` から `tools.jackson` へ変わる。
- `jackson-annotations` は例外で、Jackson 3でも `com.fasterxml.jackson.annotation` を使用する。
- Jackson 2と3は異なるgroup/packageを持つため、移行期間中の同居が可能である。

参考:

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [FasterXML Jackson project](https://github.com/FasterXML/jackson)

## 3. 現行依存関係

Phase 3完了後のPOMは、Jackson 3をJSON処理本体として利用し、
既存publicシグネチャ用のJackson 2 coreだけを直接宣言している。

- `spring-boot-starter-webmvc` からBoot 4標準のJackson 3が入る。
  - `tools.jackson.core:jackson-databind:3.1.4`
  - `tools.jackson.core:jackson-core:3.1.4`
- `com.fasterxml.jackson.core:jackson-core:2.21.4` は、
  `JsonUtils` の既存Jackson 2 `TypeReference` publicシグネチャ維持用に直接宣言する。
- `com.fasterxml.jackson.core:jackson-annotations:2.21` はJackson 3 databindから入る。

さらにSpringdoc 3.0.3のSwagger CoreはJackson 2 databind、YAML、Java Time moduleを
推移依存として持つ。このため `spring-boot-jackson2` を削除しても、現時点では
Jackson 2 artifactが依存ツリーから完全には消えない。

移行の判定基準は「Jackson 2 artifactが0件」ではない。次を達成条件とする。

1. Ghost-PDF5自身のmapper実装と直接databind利用をJackson 3へ移す。
2. Jackson 2を使う理由を既存public API互換または外部ライブラリの推移依存に限定する。
3. Ghost-PDF5の実装がSpringdocの推移依存へ暗黙に依存しない。
4. 移行用 `spring-boot-jackson2` を削除する。

## 4. ソース利用箇所の監査

### 4.1 mainソース

`com.fasterxml.jackson` importを持つmain Javaファイルは23件ある。

- 22件はDTO、例外response、enumのannotationだけを使用する。
  - `JsonProperty`
  - `JsonCreator`
  - `JsonValue`
- databind/coreを直接使う実装は `common/utils/JsonUtils.java` の1件だけである。

annotation packageはJackson 3でも変わらないため、22件のimportは移行対象にしない。
機械的に `tools.jackson.annotation` へ変更すると誤りになる。

### 4.2 testソース

Jackson 2を直接importするテストは次の5件である。

- `JsonUtilsTest`
- `MarkdownContentDtoTest`
- `PdfContentDtoTest`
- `PdfInsertOptionTest`
- `OpenApiDocumentationTest`

DTO、enum、OpenAPIテストの `ObjectMapper` / `JsonNode` はJackson 3へ移行できる。
Jackson 3では `JsonNode#asText()` が非推奨のため、文字列取得は `asString()` へ更新する。

### 4.3 JsonUtilsの利用状況

現時点で `JsonUtils` を呼ぶproductionコードはない。Controllerテストのrequest JSON生成と
`JsonUtilsTest` が主な利用元である。このため内部mapperの移行範囲は狭いが、public APIは
将来の呼び出し元や外部利用を考慮して維持する。

## 5. 維持するpublic API

既存の次のメソッドは削除・改名・引数変更を行わない。

```java
tryToJson(T payload)
tryParse(String json, Class<T> clazz)
tryParse(String json, com.fasterxml.jackson.core.type.TypeReference<T> valueTypeRef)
tryConvertValue(Object payload, Class<T> clazz)
toJsonOrThrow(T payload)
parseOrThrow(String json, Class<T> clazz)
parseOrThrow(String json, com.fasterxml.jackson.core.type.TypeReference<T> valueTypeRef)
convertValueOrThrow(Object payload, Class<T> clazz)
```

特にJackson 2の `TypeReference` を引数に持つ2メソッドは、型そのものがpublicシグネチャの
一部である。既存メソッドをJackson 3型へ直接置き換えず、次の方法で互換を維持する。

```java
MAPPER.readValue(json, MAPPER.constructType(valueTypeRef.getType()))
```

Jackson 2の `TypeReference#getType()` からJavaの `Type` を取り出し、Jackson 3 mapperの
`constructType` でJackson 3 `JavaType` を生成する。この方法ならJSON処理本体はJackson 3、
既存メソッドの引数型はJackson 2のままにできる。

新規コード向けには、異なる型としてoverload可能なJackson 3版を追加する。

```java
tryParse(String json, tools.jackson.core.type.TypeReference<T> valueTypeRef)
parseOrThrow(String json, tools.jackson.core.type.TypeReference<T> valueTypeRef)
```

既存Jackson 2版を非推奨にする判断は今回行わない。利用状況と破壊的変更の方針を決める
将来フェーズまで両方を維持する。

## 6. 作業用コピーで確認した実装案

リポジトリ外の作業用コピーへ、次の候補変更を適用した。

1. `JsonUtils` の内部mapperをJackson 3へ変更した。

```java
private static final ObjectMapper MAPPER =
		JsonMapper.builderWithJackson2Defaults().build();
```

2. Jackson 2 `TypeReference` を前節の `getType()` / `constructType()` で橋渡しした。
3. Jackson 3 `TypeReference` overloadを追加した。
4. 直接mapperを使うテストを `tools.jackson` へ変更した。
5. テストの `JsonNode#asText()` を `asString()` へ変更した。
6. `spring-boot-jackson2` を外し、既存publicシグネチャ用のJackson 2 coreを明示した。

最初の全テストでは、`convertValue` の不正な数値変換がJackson 3の
`InvalidFormatException` を直接送出し、既存の失敗時契約と一致しないことが分かった。
Jackson 3の `JacksonException` はRuntimeExceptionで、Jackson 2の例外と継承関係が異なる。

`convertValueOrThrow` で `JacksonException` を捕捉して従来どおり
`IllegalArgumentException` に包むと、次の契約を維持できた。

- `tryConvertValue` は失敗時に `Optional.empty()` を返す。
- `convertValueOrThrow` は失敗時に `IllegalArgumentException` を送出する。
- warn/debugログの既存経路を維持する。

この互換処理後、Java 25の `mvn clean test` は284件すべて成功した。
したがって段階移行は技術的に成立する見込みである。

## 7. 推奨実施順序

### Phase 1: JsonUtils内部エンジン移行（完了）

対象を `JsonUtils` と `JsonUtilsTest` に限定して、2026-08-01に本体へ適用した。

- 内部mapperをJackson 3 `JsonMapper` へ変更した。
- `builderWithJackson2Defaults()` を使い、旧mapperに近い既定値から開始した。
- 既存Jackson 2 `TypeReference` メソッドを維持し、Java `Type` 経由で橋渡しした。
- Jackson 3 `TypeReference` overloadを追加した。
- 全変換失敗を既存どおり `IllegalArgumentException` へ統一した。
- Jackson 2版とJackson 3版のgeneric collection parseをテストした。
- `JsonUtilsTest` は11件から13件になり、Java 25の全286テストが成功した。
- `pom.xml` と `spring-boot-jackson2` は変更していない。

### Phase 2: 直接利用テストのJackson 3化（完了）

次の4領域をJackson 3 mapperへ移した。

- Markdown DTO JSON契約
- PDF DTO JSON契約
- `PdfInsertOption` JSON契約
- OpenAPI JSON検査

`JsonNode#asText()` は `asString()` へ変更した。`JsonProperty`、`JsonCreator`、
`JsonValue` のannotation importは変更していない。

- 対象26テストとJava 25の全286テストが成功した。
- deprecation表示付きtest compileでJackson由来の非推奨警告がないことを確認した。
- `pom.xml` と `spring-boot-jackson2` は変更していない。

### Phase 3: 移行module削除（完了）

- `spring-boot-jackson2` をPOMから削除した。
- 既存public `TypeReference` 用に `com.fasterxml.jackson.core:jackson-core` を直接宣言した。
- versionはSpring Bootのdependency managementへ任せ、2.21.4が解決された。
- `dependency:tree` でGhost-PDF5の直接依存とSpringdocの推移依存を確認した。
- Java 25の全286テストが成功した。
- Boot 4.0.7 / Tomcat 11.0.22で通常起動し、トップ画面のHTTP 200を確認した。
- 通常起動では設定どおりOpenAPI endpointが無効であり、OpenAPI契約はJUnitで確認した。
- HTTP JSON契約差がなかったため、`spring.jackson.use-jackson2-defaults=true` は追加していない。

### Phase 4: 長期整理

- Springdoc更新時にJackson 2推移依存の変化を再確認する。
- Jackson 2 `TypeReference` overloadを廃止できる破壊的変更方針が決まるまでは維持する。
- 旧overloadを廃止できた後にJackson 2 coreの直接依存を削除する。
- Commons IO、ServiceImpl、Vite、AI等の作業はJackson移行へ混ぜない。

## 8. 検証項目

各実装コミットでJava 25の `mvn clean test` を実行し、少なくとも次を確認する。

- `JsonUtils` のnull、壊れたJSON、自己参照、型変換失敗。
- Jackson 2 / 3両方の `TypeReference<List<T>>`。
- `try` 系の `Optional.empty()` と `OrThrow` 系の例外型。
- DTOのJSON property名、null、空文字、日本語、配列。
- `PdfInsertOption` のserialize / deserialize。
- Controllerのrequest / response JSON。
- `/v3/api-docs` のpath、schema、HTTP status。
- `ApplicationTests` のSpring Context起動。
- `mvn dependency:tree` のJackson 2 / 3経路。

停止条件:

- publicシグネチャを変更しないとcompileできない。
- HTTP JSON property名やenum値が変わる。
- OpenAPI schemaが意図せず変わる。
- PDF / Markdown既存テストに移行理由を説明できない差が出る。

該当時は依存削除を進めず、直前の小コミット単位で原因を切り分ける。

## 9. 今回の結論

Ghost-PDF5のJackson 3移行は、一括置換をせず3コミット程度へ分ければ安全に進められる。
最大の注意点は、annotation packageを変更しないこと、Jackson 2 `TypeReference` のpublic互換を
維持すること、Jackson 3の例外を既存例外契約へ包み直すことである。

Phase 1の `JsonUtils` 内部エンジン移行、Phase 2の直接mapper利用テスト移行、
Phase 3の `spring-boot-jackson2` 削除と既存publicシグネチャ用Jackson 2 coreの明示まで完了した。
Java 25の全286テスト、通常起動、トップ画面、依存ツリーを確認済みであり、
Jackson 3段階移行はここで一区切りとする。
