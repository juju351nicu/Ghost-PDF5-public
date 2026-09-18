# Webページ取り込み（HTML → Markdown下書き）設計

対象: Stage 1（`POST /markdownDraftHtml`、PR #18でmainへマージ済み）/ Stage 2（`POST /markdownDraftUrl`）
関連: [ページ単位Markdown下書きAPI設計](page-markdown-draft-api-design.md) / [画像Markdown下書きAPI設計](image-markdown-draft-design.md) / [Service / Logic構成判断](service-logic-structure.md)

## 1. 目的

手元にあるWebページのHTMLから本文を取り出し、Markdownメモの下書きにする。
既存の下書き系API（`markdownDraftPdf` / `markdownDraftImage` / `markdownDraftOffice`）が埋めていない
「HTML → Markdown」の空白を埋める。

出力はただのMarkdownなので、その後の整形・要約は既存の `POST /markdownAiTransform`、
PDF化は `POST /markdownPdf` にそのまま渡せる。この機能に専用のAI処理は入れない。
providerとプロンプトの分岐を2箇所に増やすことになるため。

## 2. 段階

| Stage | 内容 | ネットワーク | 状態 |
| --- | --- | --- | --- |
| Stage 1 | HTMLファイルをアップロード → 本文Markdown下書き（`POST /markdownDraftHtml`） | 不要 | **実装済み** |
| Stage 2 | URL入力 → サーバーが取得 → Stage 1 の変換へ流す（`POST /markdownDraftUrl`） | 必要（既定無効） | **実装済み** |
| Stage 3 | サイト構造・制作参考レポート（`mode=STRUCTURE`） | Stage 1 / 2 に準じる | 未着手 |

Stage 1 と Stage 2 を分けたのは、難所が別物だからである。
「HTMLから読めるMarkdownを作る」は出力品質の問題、「サーバーから任意のURLを叩く」はSSRFの問題で、
同時に持ち込むと、結果が雑なのか取得に失敗しているのかを切り分けられない。
Stage 1 はネットワークに出ないため、自作フィクスチャHTMLでテストが決定論的になる。

## 3. API

```text
POST /markdownDraftHtml   multipart/form-data   htmlFile（必須）, selector（任意）
POST /markdownDraftUrl    application/json      { "url": "...", "selector": "..."（任意） }
```

- `access-token` ヘッダーを既存の `AccessTokenValidator` で検証する。
- 成功時はどちらも `ApiResult<WebMarkdownDraftResponse>`（`markdown` / `title` / `sourceUrl` / `truncated`）。
- `sourceUrl` はアップロードでは常に `null`。URL取得ではリダイレクトを追い終えた**最終URL**が入る。
- `POST /markdownDraftUrl` は `ghost.web.fetch.enabled`（既定 `false`）が有効なときだけ動く。無効時は503。
- **サーバー側で自動保存しない。** 結果はMarkdownメモ欄へ返すだけで、保存は利用者が `POST /saveMarkdown` を
  実行したときだけ行う。中身を確認する前に他者のページの複製が手元へ残る状態を作らないため。

## 4. 変換対象

| HTML | Markdown |
| --- | --- |
| `h1`〜`h6` | `#`〜`######` |
| `p` | 段落 |
| `ul` / `ol` / `li` | `- ` / `1. `（入れ子は半角2桁インデント） |
| `table` / `tr` / `th` / `td` | GFMの表（`common.utils.MarkdownTableBuilder`） |
| `pre` / `code` | フェンス付きコードブロック（`class="language-*"` から言語を引き継ぐ） / インラインコード |
| `blockquote` | `> `（中の段落・見出しも引用として残す） |
| `strong` / `b` / `em` / `i` | `**` / `*` |
| `a[href]` | `[text](絶対URL)` |
| `img[src]` | `![alt](絶対URL)`。**画像は取得しない**（参照のみ） |
| `hr` | `---` |

上記以外のタグは、中にブロック要素があれば入れ物として掘り下げ、無ければ段落として扱う。
`div` しか使っていないページでも本文が落ちないようにするため。
対応表を固定しているのは、タグを見つけるたびに規則を足すと、同じページを取り込み直したときに
結果が変わってしまうため。

冒頭には必ず出典ヘッダーを付ける。

```markdown
# <title または h1>

- 取得元: <ファイル名 または URL>
- 取得日時: 2026-09-18 12:34:56
- 説明: <meta name="description">
```

## 5. 抽出方針

- `script` / `style` / `nav` / `header` / `footer` / `aside` / `form` / `noscript` / `iframe` / `svg` を除去する。
  除去はセレクタでの絞り込みより先に行う。絞り込み先の内側にもナビゲーションや広告枠は入り得るため。
- 本文らしさをスコアリングするヒューリスティック（readability系）は入れない。閾値調整が終わらないうえ、
  なぜその出力になったのかを説明できなくなる。
- うまく取れない場合は、利用者が `selector`（`article` / `main` / `#content` など）で絞り込む。
  一致が複数あるときは最初の1件だけを使う。連結すると同じセレクタでも本文量がページごとに変わる。
- 文字コードはjsoupに委ねる（`Jsoup.parse(InputStream, null, baseUri)`）。BOM → `<meta charset>` → 既定UTF-8の
  順で判定される。自前で判定すると、判定規則がブラウザと食い違ったときに文字化けの原因を追えなくなる。

## 6. 上限

`ghost.web.markdown.max-output-characters`（既定100,000）を超えた分は切り落とし、
`truncated=true` と `ApiResult` のWARNINGメッセージ（`webMarkdownTruncated`）で通知する。
黙って切ると、利用者は後半が無いことに気付かないままMarkdownメモを完成品として扱ってしまう。

アップロードサイズの上限はPDFと同じ `PdfConstants.MAX_PDF_FILE_SIZE_BYTES`（20MB）で、超過は413。
種類ごとに上限を変えると、どの上限が適用されたのかを利用者が判断できなくなるため。

## 7. 例外とHTTP status

| 例外 | ステータス | 契機 |
| --- | --- | --- |
| `WebInputException` | 400 | 対応していない拡張子、中身が空、セレクタの書式不正、セレクタ該当0件、本文が空 |
| `WebProcessingException` | 500 | アップロードファイルの読み取り失敗、HTMLの解析失敗 |
| `MultipartException` | 413 | アップロードサイズが上限以上 |

`WebInputException` は「何を直せば通るか」が原因ごとに違うため、例外が持つ説明文をそのまま画面へ返す。
この説明は固定文言で組み立てており、取り込んだHTMLの内容は含めない。

URL取得（Stage 2）で増える例外（`WebFetchException` / `WebFetchBlockedException` / `WebUnavailableException`）は
§9.3 を参照。

## 8. 責務とクラス配置

| クラス | package | 責務 |
| --- | --- | --- |
| `WebMarkdownController` | `webcontent.controller` | token検証、multipartのvalidation、サイズ検証、Service委譲 |
| `WebMarkdownService` | `webcontent.service` | 拡張子・空ファイルの検証、出力上限、レスポンス組み立て |
| `WebPageExtractor` | `webcontent.logic` | HTML解析、不要要素の除去、セレクタ絞り込み、タイトル・説明の取り出し |
| `WebMarkdownBuilder` | `webcontent.logic` | 本文のMarkdown組み立て |
| `WebPageContent` | `webcontent.logic` | 抽出結果（タイトル・説明・本文の根） |
| `WebMarkdownProperties` | `webcontent.config` | `ghost.web.markdown.*` |
| `WebPageFetcher` | `webcontent.logic` | URL取得。HTTPクライアントを持つ唯一のクラス |
| `WebAddressValidator` | `webcontent.logic` | 宛先の検査（スキーム・ポート・ユーザー情報・IP範囲） |
| `HostAddressResolver` / `SystemHostAddressResolver` | `webcontent.logic` | 名前解決。テストで差し替えるための境界 |
| `FetchedWebPage` | `webcontent.logic` | 取得結果（最終URL・文字コード・本文） |
| `WebFetchProperties` | `webcontent.config` | `ghost.web.fetch.*` |

Controller → Service → Logic の一方向は `CodingConventionTest.webControllerServiceLogicDependenciesKeepDirection`
が固定する。任意の宛先へ接続できる経路が増えないことは
`CodingConventionTest.httpClientIsLimitedToWebPageFetcher` が守る。

表の組み立てとブロック連結はOffice側と共有するため `common.utils` へ移した
（`MarkdownTableBuilder` / `MarkdownBlockJoiner`）。取り込み元ごとに書き分けると、
「Wordの表は崩れないのにWebの表は崩れる」という食い違いを入力形式の数だけ抱えることになる。

## 9. Stage 2: URL取得とSSRF対策

`POST /markdownDraftUrl` は、サーバーが利用者の指定した宛先へ接続する唯一の経路。
取得は `WebPageFetcher`、宛先の検査は `WebAddressValidator` に閉じ込め、
抽出とMarkdown組み立ては Stage 1 と同じものを使う。同じページなら、ファイルから読んでも
URLから取っても同じMarkdownになる。

### 9.1 検査一覧

| # | 検査 | 実装 | 落ちたときの例外 |
| --- | --- | --- | --- |
| 1 | スキームは `http` / `https` のみ（`file` / `ftp` / `jar` / `data` / `gopher` は拒否） | `WebAddressValidator` | `WebInputException`（400） |
| 2 | ポートは `ghost.web.fetch.allowed-ports`（既定80 / 443）のみ | `WebAddressValidator` | `WebInputException`（400） |
| 3 | ユーザー情報付きURL（`user:pass@host`）を拒否 | `WebAddressValidator` | `WebInputException`（400） |
| 4 | 名前解決した**全アドレス**を検査。ループバック（`allow-loopback` 無効時）・プライベート・リンクローカル（`169.254.169.254` を含む）・ワイルドカード・マルチキャスト・IPv6ユニークローカルを拒否。IPv4射影IPv6は射影元のIPv4で判定 | `WebAddressValidator` | `WebFetchBlockedException`（400） |
| 5 | リダイレクトは自前ループで `max-redirects`（既定3）まで。**毎ホップで 1〜4 をやり直す** | `WebPageFetcher` | `WebFetchException`（502） |
| 6 | `Content-Type` は `text/html` / `application/xhtml+xml` のみ | `WebPageFetcher` | `WebFetchException`（502） |
| 7 | 本文は `BoundedInputStream` で**実読み取りバイト数**を `max-bytes`（既定2MB）に抑える | `WebPageFetcher` | `WebFetchException`（502） |
| 8 | 接続・読み取りタイムアウト `timeout-seconds`（既定10秒） | `WebPageFetcher` | `WebFetchException`（502） |
| 9 | User-Agent を `user-agent`（既定 `Ghost-PDF5`）で明示。ブラウザを偽装しない | `WebPageFetcher` | — |
| 10 | 直近の取得から `min-interval-millis`（既定1000）未満なら拒否 | `WebPageFetcher` | `WebFetchException`（502） |

リダイレクトをHTTPクライアントに任せない（`followRedirects(NEVER)`）のは、任せると転送先の
名前解決と接続がライブラリの内側で完結し、ホップごとに検査を挟む隙間が無いため。公開ドメインから
内部アドレスへ1回転送するだけで検査を素通りできてしまう。

サイズ上限は「超えたら切り詰める」ではなく「超えたら失敗」にした。途中までのHTMLを正常な取得結果として
返すと、本文が欠けたMarkdownが出来上がり、利用者はそれが欠けていることに気付けない。
`Content-Length` は読まない。宣言値を信じる実装は、小さく詐称した応答やchunked応答で素通りする。

### 9.2 設定

```yaml
ghost:
  web:
    fetch:
      enabled: ${GHOST_WEB_FETCH_ENABLED:false}
      timeout-seconds: ${GHOST_WEB_FETCH_TIMEOUT_SECONDS:10}
      max-bytes: ${GHOST_WEB_FETCH_MAX_BYTES:2097152}
      max-redirects: ${GHOST_WEB_FETCH_MAX_REDIRECTS:3}
      min-interval-millis: ${GHOST_WEB_FETCH_MIN_INTERVAL_MILLIS:1000}
      user-agent: ${GHOST_WEB_FETCH_USER_AGENT:Ghost-PDF5}
      allow-loopback: ${GHOST_WEB_FETCH_ALLOW_LOOPBACK:false}
      allowed-ports: ${GHOST_WEB_FETCH_ALLOWED_PORTS:80,443}
```

`allow-loopback` と `allowed-ports` は設計の一部であって、テストのための抜け道ではない。
ループバックでHTTPサーバーを起動する自動テストは、標準ポートを使えず、宛先もループバックになる。
この2つが無いと、SSRF検査に自分のテストが落とされて取得の検証自体が書けない。既定値のままなら
外から見える挙動は変わらない。

### 9.3 エラーとHTTP status

| 例外 | ステータス | 契機 |
| --- | --- | --- |
| `WebInputException` | 400 | URLの書式・スキーム・ポート・ユーザー情報・名前解決失敗 |
| `WebFetchBlockedException` | 400 | 宛先IPが禁止範囲 |
| `WebFetchException` | 502 | 取得失敗、タイムアウト、200以外、Content-Type不一致、サイズ超過、リダイレクト過多、間隔制限 |
| `WebUnavailableException` | 503 | `ghost.web.fetch.enabled=false` の状態で `/markdownDraftUrl` を呼んだ |

`WebFetchBlockedException` は、**解決済みIPも、どの禁止範囲だったかも**返さない。
「そのホスト名がどのIPへ解決されたか」は内部ネットワークの構成そのもので、返すとこの機能が
内部の名前解決結果を読み出す道具になる。ログにも同じ理由でホスト名までしか残さない。

### 9.4 DNS rebindingに対する限界

検査は「名前解決 → IP判定」で行い、その後の接続はホスト名で行う。したがって、検査時と接続時で
解決結果が入れ替わる攻撃（TOCTOU）は防げない。完全に塞ぐには検査で得たIPへ直接接続し、
TLSのホスト名検証とHostヘッダーを自前で手当てする必要があり、HTTPクライアントの内部へ踏み込む実装になる。

この機能は「利用者が自分のために1ページ取り込む」個人用ツールの範囲で、既定が無効、
攻撃者が任意のURLを送り込める立場にもない。動かない完璧さより、限界が書いてある実装を選ぶ。
この判断は `WebPageFetcher` のJavadocと `SECURITY.md` にも同じ内容で残す。

## 10. 範囲外

| やらないこと | 理由 |
| --- | --- |
| 再帰クロール / サイト全体の取得 | 「利用者がブラウザで開くのを代行する」範囲を超える。負荷と権利の両面で線を越える |
| ログイン・Cookie・認証の代行 | 認証情報をサーバーが預かることになる。ログイン後のページはブラウザで保存して Stage 1 へ渡す |
| JavaScript実行（ヘッドレスブラウザ） | 重い依存が増える。SPAは「ブラウザで保存したHTML」で回避できる |
| 画像の取得・埋め込み | 帯域と権利の問題が増える。Markdownには参照URLだけ残す |
| robots.txt の自動解釈 | Stage 2 でも1URL・非再帰・UA明示・間隔制限で実害を塞ぎ、判断は画面の注意書きで利用者へ返す |
| Web取り込み専用のAI要約 | `POST /markdownAiTransform` で足りる |

## 11. 既知の限界

- SPAなど、JavaScriptで本文を描画するページは、保存前のHTMLに本文が無いため取り込めない。
  この場合はブラウザで表示してから「名前を付けて保存」したHTMLを渡す。
- 相対URLの絶対化は、アップロードでは HTML内の `<base href>` がある場合だけ効く。
  取得元URLを基準にできるのは Stage 2 から。
- 入れ物要素に直接書かれたテキスト（`<article>本文<p>…</p></article>` の「本文」）は、
  同じ要素内にブロック要素があると落ちる。ブロック単位で走査しているため。
- 表の結合セル（`colspan` / `rowspan`）は再現しない。Office側の表変換と同じ制限。
- タイトルに使った `<h1>` は本文側にも残るため、見出しが重複することがある。

## 12. テスト

| テスト | 観点 |
| --- | --- |
| `WebPageExtractorTest` | 不要要素の除去、セレクタ絞り込み・該当0件・書式不正、本文が空、タイトル・説明、Shift_JISの文字化け |
| `WebMarkdownBuilderTest` | 出典ヘッダー、見出しレベル、入れ子リスト、表、コードブロック、引用、水平線、相対URLの絶対化、画像参照 |
| `WebMarkdownServiceTest` | 拡張子判定、空ファイル、セレクタのtrim、出力上限での切り落としとWARNING |
| `WebMarkdownControllerTest` | token検証、ファイル未指定、入力不正の400、サイズ超過の413、URL経路の400 / 502 / 503 |
| `WebAddressValidatorTest` | スキーム・ポート・ユーザー情報・禁止IP範囲（11種）・複数解決結果・loopback許可の切り替え |
| `WebPageFetcherIntegrationTest` | 正常取得、リダイレクト上限、相対Location、Location欠落、Content-Type、200以外、サイズ上限（chunked / 宣言あり）、タイムアウト、間隔制限、loopback拒否 |

フィクスチャHTML（`src/test/resources/web/`）はすべて自作で、実在サイトのHTMLは持ち込まない。
取得のテストは `com.sun.net.httpserver.HttpServer` をループバックで起動して行う。外部サイトを相手にすると、
相手の都合で結果が変わり、リダイレクト回数やContent-Typeのような条件を狙って作れない。
SSRF検査のテストは名前解決を差し替えて行う。実DNSに依存させると、実行環境のネットワーク設定で結果が変わる。

## 13. 未実施事項

- 実データ（ブラウザで保存した実ページ、実URL）での出力品質確認は最小限。
  取り込み対象を広げる前に、数本のページで抽出品質を確かめる。
- Stage 3（`mode=STRUCTURE`、サイト構造・制作参考レポート）は未着手。
