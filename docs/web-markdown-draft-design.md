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
| Stage 3 | サイト構造・制作参考レポート（`mode=STRUCTURE`） | Stage 1 / 2 に準じる | **実装済み（最小3節）** |

Stage 1 と Stage 2 を分けたのは、難所が別物だからである。
「HTMLから読めるMarkdownを作る」は出力品質の問題、「サーバーから任意のURLを叩く」はSSRFの問題で、
同時に持ち込むと、結果が雑なのか取得に失敗しているのかを切り分けられない。
Stage 1 はネットワークに出ないため、自作フィクスチャHTMLでテストが決定論的になる。

## 3. API

```text
POST /markdownDraftHtml   multipart/form-data   htmlFile（必須）, selector（任意）, mode（任意）
POST /markdownDraftUrl    application/json      { "url": "...", "selector": "..."（任意）, "mode": "..."（任意） }
```

`mode`（`WebMarkdownDraftMode`）は出力の内容を切り替える。エンドポイントを増やさず `mode` にしたのは、
既存の `POST /markdownDraftPdf` が `mode=AUTO|VISION` で同じことをしているため。

| mode | 出力 |
| --- | --- |
| `ARTICLE`（未指定時） | 出典ヘッダー + 本文 |
| `STRUCTURE` | 出典ヘッダー + 構造レポート |
| `BOTH` | 出典ヘッダー + 構造レポート + 本文 |

未指定時の既定はenumの値にせずService側で決める。「未指定」をenumへ足すと、APIが受け取れる文字列が増える。

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
| `table` / `tr` / `th` / `td` | GFMの表（`common.utils.MarkdownTableBuilder`）。`caption` は表の直前に太字の1行 |
| `pre` / `code` | フェンス付きコードブロック（`class="language-*"` から言語を引き継ぐ） / インラインコード |
| `dl` / `dt` / `dd` | `- **用語**: 説明` の箇条書き（Markdownに定義リストの記法が無いため） |
| `blockquote` | `> `（中の段落・見出しも引用として残す） |
| `strong` / `b` / `em` / `i` | `**` / `*` |
| `a[href]` | `[text](絶対URL)` |
| `img[src]` | `![alt](絶対URL)`。**画像は取得しない**（参照のみ） |
| `hr` | `---` |

見出しの中ではリンク記法を作らず、テキストだけを残す。多くのサイトが見出しに「その見出しへのリンク」を
埋め込んでおり、そのまま写すと目次として読みたい見出しがすべてリンクになる。行き先は同じページの
同じ見出しなので、落としても情報は減らない。

インラインコードの中身は素のテキストだけを使う。コード記法の中では他の記法が働かないため、
`<code><a>…</a></code>` をそのまま写すとリンクともコードとも描画されない文字列になる。
ただしコード全体が1つのリンクの場合だけ、コードを包む形（``[`text`](url)``）へ入れ替える。

コードの中身にバッククォートやコードフェンスが含まれる場合は、囲みを1つ長くする。同じ長さだと、
コードの途中でコードが終わったと解釈され、以降の本文までコード扱いになる。

入れ物の直下に地の文がある場合（`<div>説明<p>…</p></div>`）、その文も出現順を保って段落にする。
子要素だけを掘ると地の文が落ちる。リスト項目の中の表・コードブロック・引用・定義リストも、
項目の続きとして1段深いインデントで出す。インライン整形では拾えないため、追わないと丸ごと落ちる。

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

- `script` / `style` / `nav` / `header` / `footer` / `aside` / `form` / `button` / `noscript` / `iframe` / `svg` を除去する。
  `button` は「ページをコピー」のような操作のラベルで、本文に混ざると独立した段落として残る。
  除去はセレクタでの絞り込みより先に行う。絞り込み先の内側にもナビゲーションや広告枠は入り得るため。
- 本文らしさをスコアリングするヒューリスティック（readability系）は入れない。閾値調整が終わらないうえ、
  なぜその出力になったのかを説明できなくなる。
- うまく取れない場合は、利用者が `selector`（`article` / `main` / `#content` など）で絞り込む。
  一致が複数あるときは最初の1件だけを使う。連結すると同じセレクタでも本文量がページごとに変わる。
- 文字コードはjsoupに委ねる（`Jsoup.parse(InputStream, null, baseUri)`）。BOM → `<meta charset>` → 既定UTF-8の
  順で判定される。自前で判定すると、判定規則がブラウザと食い違ったときに文字化けの原因を追えなくなる。
- **本文が空かどうかは抽出器では判定しない。** 空を許さないかは「何を出力するか」で決まるため、
  判定はService層が行う（`mode=STRUCTURE` では本文が空でも結果を返す）。
- 出典ヘッダーの説明（`meta description`）は200文字で切る。索引やナビの文字列をそのまま
  `description` へ入れているサイトがあり、放っておくと出典だけで数百文字になる。

## 6. 上限

`ghost.web.markdown.max-output-characters`（既定100,000）を超えた分は切り落とし、
`truncated=true` と `ApiResult` のWARNINGメッセージ（`webMarkdownTruncated`）で通知する。
黙って切ると、利用者は後半が無いことに気付かないままMarkdownメモを完成品として扱ってしまう。

切る位置が文字の途中（UTF-16のサロゲートペアの間）になる場合は1つ手前で切る。そのまま切ると、
壊れた文字が末尾に残る。

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
| `WebPageContent` | `webcontent.logic` | 抽出結果（タイトル・説明・本文の根・除去前のDOM） |
| `WebStructureReportBuilder` | `webcontent.logic` | 構造レポートの組み立て |
| `WebMarkdownDraftMode` | `webcontent.enums` | 出力モード（ARTICLE / STRUCTURE / BOTH） |
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
| 0 | URLをASCIIへそろえる（日本語のパスやフラグメントをUTF-8のパーセントエンコードへ、スキームを小文字へ）。ブラウザのアドレス欄から貼り付けた形を弾かないため | `WebAddressValidator` | — |
| 1 | スキームは `http` / `https` のみ（`file` / `ftp` / `jar` / `data` / `gopher` は拒否） | `WebAddressValidator` | `WebInputException`（400） |
| 2 | ポートは `ghost.web.fetch.allowed-ports`（既定80 / 443）のみ | `WebAddressValidator` | `WebInputException`（400） |
| 3 | ユーザー情報付きURL（`user:pass@host`）を拒否 | `WebAddressValidator` | `WebInputException`（400） |
| 4 | 名前解決した**全アドレス**を検査。ループバック（`allow-loopback` 無効時）・プライベート・リンクローカル（`169.254.169.254` を含む）・ワイルドカード・マルチキャスト・IPv6ユニークローカルを拒否。IPv4射影IPv6は射影元のIPv4で判定 | `WebAddressValidator` | `WebFetchBlockedException`（400） |
| 5 | リダイレクトは自前ループで `max-redirects`（既定3）まで。**毎ホップで 1〜4 をやり直す** | `WebPageFetcher` | `WebFetchException`（502） |
| 6 | `Content-Type` は `text/html` / `application/xhtml+xml` のみ。`Content-Encoding` が付く（圧縮された）応答も拒否 | `WebPageFetcher` | `WebFetchException`（502） |
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

`Accept-Encoding` を送らないため通常は非圧縮で返るが、それを無視して圧縮を返す取得先もある。
JDKのHTTPクライアントは展開しないため、そのまま解析すると圧縮データをHTMLとして読み、
意味の無いMarkdownが正常な結果として返る。展開を実装せず、取り込めないことを伝えて
「ブラウザで保存したHTMLを取り込む」へ案内する。

応答が宣言する文字コード名は、この環境で扱えるものだけを解析へ渡す。綴りの誤りや独自表記を
そのまま渡すと解析側が例外になり、「HTMLは取れているのに500」という分かりにくい失敗になる。

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

## 10. Stage 3: 構造レポート（`mode=STRUCTURE` / `BOTH`）

### 10.1 目的と分担

本文を読める形にするのが Stage 1 / 2、**ページの組み立て方を観察するのが Stage 3**。
調査結果のまとめ・PDF化・要約は既存機能（Markdownメモ / `POST /markdownPdf` / `POST /markdownAiTransform`）で
足りるため、新しく作るのは「分析の抽出項目」だけに絞る。

出すのは**観察した事実と数値だけ**で、評価も改善提案も書かない。判断が要るときは出来上がったMarkdownを
`POST /markdownAiTransform` へ渡す。この分担を崩すと、抽出器の中にAIの都合（プロンプト、トークン上限、
provider差）が混ざり始める。

### 10.2 節（3つだけ）

| 節 | 内容 |
| --- | --- |
| 文書メタ | `title` / `lang` / `canonical` / favicon / `meta`（description・robots・viewport・theme-color・author・keywords）/ OGP 6種。**指定が無い場合は「（指定なし）」と書く**（未設定であることも事実） |
| 見出しアウトライン | `h1`〜`h6` をレベルどおりのインデントで並べ、レベルが飛んだ箇所（h2→h4 など）に注記を付ける |
| ランドマーク構成 | `header` / `nav` / `main` / `section` / `article` / `aside` / `footer` を入れ子のまま並べ、`aria-label` / `id` / `class` があれば見分けの手がかりとして添える |

抽出できる項目は他にもあるが（ナビ、フォーム、リンク、外部依存、画像、アクセシビリティ、スタイル）、
**使ってみて「これが欲しい」と分かってから足す**。使う前に項目を増やすと、読まれない節の維持費だけが残る。

`div` のような装飾用の入れ物では階層を深くしない。実装の都合で段が深くなると、元の文書構造より
マークアップの事情が前に出てしまう。

### 10.3 レポートは除去前のDOMを見る

本文からは外す `nav` / `header` / `footer` / `aside` こそ、「このサイトがページをどう組み立てているか」を
見るときの対象になる。そのため `WebPageExtractor` は、不要要素の除去を**複製に対して**行い、
`WebPageContent` へ「本文用に削ったroot」と「削る前のdocument」の両方を持たせる。
複製の大きさは入力サイズの上限（アップロード20MB / URL取得2MB）で頭打ちになる。

### 10.4 取れないもの

jsoupはJavaScriptを実行せずCSSも評価しないため、次は取れない。取れないものをそれらしく埋めない。

- 算出後のスタイル（実際の文字サイズ・色・余白）。
- 外部CSSファイルの中身。読み込んでいるファイルの存在までしか分からない。
- JavaScriptで描画される要素。SPAをURLから取ると、ほぼ空のDOMしか返らない。
  **この用途では Stage 1 が本命**で、ブラウザで「名前を付けて保存」した描画後のHTMLを渡せば構造を観察できる。

外部CSSを取りに行くかは保留（設計メモ34 §5.5）。`WebPageFetcher` のSSRF検査は再利用できるので技術的な壁は
低いが、必要性が実証されていない。「色と余白が取れないせいで使えない」と実際に感じてから検討する。

### 10.5 権利

レポートに他社のHTML / CSSの断片を長く貼らない。出力するのは観察した事実であって、複製可能な素材ではない。
生成したレポートはリポジトリへコミットせず、保存先は `ghost.markdown.storage-directory` のままにする。
参考にするのは構造と設計判断（章立て、ナビの分類、必須項目の決め方）であって、文言・デザイン・
マークアップそのものではない。

## 11. 範囲外

| やらないこと | 理由 |
| --- | --- |
| 再帰クロール / サイト全体の取得 | 「利用者がブラウザで開くのを代行する」範囲を超える。負荷と権利の両面で線を越える |
| ログイン・Cookie・認証の代行 | 認証情報をサーバーが預かることになる。ログイン後のページはブラウザで保存して Stage 1 へ渡す |
| JavaScript実行（ヘッドレスブラウザ） | 重い依存が増える。SPAは「ブラウザで保存したHTML」で回避できる |
| 画像の取得・埋め込み | 帯域と権利の問題が増える。Markdownには参照URLだけ残す |
| robots.txt の自動解釈 | Stage 2 でも1URL・非再帰・UA明示・間隔制限で実害を塞ぎ、判断は画面の注意書きで利用者へ返す |
| Web取り込み専用のAI要約 | `POST /markdownAiTransform` で足りる |

## 12. 既知の限界

- SPAなど、JavaScriptで本文を描画するページは、保存前のHTMLに本文が無いため取り込めない。
  この場合はブラウザで表示してから「名前を付けて保存」したHTMLを渡す。
- 相対URLの絶対化は、アップロードでは HTML内の `<base href>` がある場合だけ効く。
  取得元URLを基準にできるのは Stage 2 から。
- 表の結合セル（`colspan` / `rowspan`）は再現しない。Office側の表変換と同じ制限。
- タイトルに使った `<h1>` は本文側にも残るため、見出しが重複することがある。

## 13. テスト

| テスト | 観点 |
| --- | --- |
| `WebPageExtractorTest` | 不要要素の除去、セレクタ絞り込み・該当0件・書式不正、本文が空、タイトル・説明、Shift_JISの文字化け |
| `WebMarkdownBuilderTest` | 出典ヘッダー、見出しレベル、入れ子リスト、表、コードブロック、引用、水平線、相対URLの絶対化、画像参照 |
| `WebMarkdownServiceTest` | 拡張子判定、空ファイル、セレクタのtrim、出力上限での切り落としとWARNING |
| `WebMarkdownControllerTest` | token検証、ファイル未指定、入力不正の400、サイズ超過の413、URL経路の400 / 502 / 503 |
| `WebAddressValidatorTest` | スキーム・ポート・ユーザー情報・禁止IP範囲（11種）・複数解決結果・loopback許可の切り替え |
| `WebPageFetcherIntegrationTest` | 正常取得、リダイレクト上限、相対Location、Location欠落、Content-Type、200以外、サイズ上限（chunked / 宣言あり）、タイムアウト、間隔制限、loopback拒否 |
| `WebStructureReportBuilderTest` | 文書メタ、指定なしの表示、見出しアウトライン、レベルの飛びの注記、ランドマークの入れ子、装飾divで深くしないこと |
| `WebMarkdownDraftModeTest` | コード値変換、大文字小文字、不正値の説明、各モードの出力内容 |
| `FrontendWebMarkdownContractTest` | モードの選択肢とenumの一致、URLとendpointの一致、責務別JS境界の経由、URL取得の既定無効、画面の注意書き、ファイル名の置き換え規則の一致 |

フィクスチャHTML（`src/test/resources/web/`）はすべて自作で、実在サイトのHTMLは持ち込まない。
取得のテストは `com.sun.net.httpserver.HttpServer` をループバックで起動して行う。外部サイトを相手にすると、
相手の都合で結果が変わり、リダイレクト回数やContent-Typeのような条件を狙って作れない。
SSRF検査のテストは名前解決を差し替えて行う。実DNSに依存させると、実行環境のネットワーク設定で結果が変わる。

## 14. 未実施事項

- 実URLでの品質確認は10サイト（jsoup / CommonMark / Maven / MDN / PostgreSQL / Python / W3C /
  example.com / React（SSR） / デジタル庁 / IPA）。ニュース系・有料記事・社内Wikiの保存HTMLは未確認。
- 構造レポートの抽出項目は3節のみ。ナビ・フォーム・リンク・外部依存・画像・アクセシビリティ・スタイルは、
  使って必要性が分かってから足す。
- 外部CSSの取得（Stage 3.5）は保留。
