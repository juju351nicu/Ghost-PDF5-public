# Webページ取り込み（HTML → Markdown下書き）設計

対象コミット: `feature/web-markdown-draft`（Stage 1）
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
| Stage 1 | HTMLファイルをアップロード → 本文Markdown下書き（`POST /markdownDraftHtml`） | 不要 | **実装済み（本書の対象）** |
| Stage 2 | URL入力 → サーバーが取得 → Stage 1 の変換へ流す（`POST /markdownDraftUrl`） | 必要（既定無効） | 未実装 |
| Stage 3 | サイト構造・制作参考レポート（`mode=STRUCTURE`） | Stage 1 / 2 に準じる | 未着手 |

Stage 1 と Stage 2 を分けたのは、難所が別物だからである。
「HTMLから読めるMarkdownを作る」は出力品質の問題、「サーバーから任意のURLを叩く」はSSRFの問題で、
同時に持ち込むと、結果が雑なのか取得に失敗しているのかを切り分けられない。
Stage 1 はネットワークに出ないため、自作フィクスチャHTMLでテストが決定論的になる。

## 3. API

```text
POST /markdownDraftHtml   multipart/form-data   htmlFile（必須）, selector（任意）
```

- `access-token` ヘッダーを既存の `AccessTokenValidator` で検証する。
- 成功時は `ApiResult<WebMarkdownDraftResponse>`（`markdown` / `title` / `sourceUrl` / `truncated`）。
- `sourceUrl` はアップロードでは常に `null`。取得元URLを持つのは Stage 2 から。
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

## 8. 責務とクラス配置

| クラス | package | 責務 |
| --- | --- | --- |
| `WebMarkdownController` | `webcontent.controller` | token検証、multipartのvalidation、サイズ検証、Service委譲 |
| `WebMarkdownService` | `webcontent.service` | 拡張子・空ファイルの検証、出力上限、レスポンス組み立て |
| `WebPageExtractor` | `webcontent.logic` | HTML解析、不要要素の除去、セレクタ絞り込み、タイトル・説明の取り出し |
| `WebMarkdownBuilder` | `webcontent.logic` | 本文のMarkdown組み立て |
| `WebPageContent` | `webcontent.logic` | 抽出結果（タイトル・説明・本文の根） |
| `WebMarkdownProperties` | `webcontent.config` | `ghost.web.markdown.*` |

Controller → Service → Logic の一方向は `CodingConventionTest.webControllerServiceLogicDependenciesKeepDirection`
が固定する。表の組み立てとブロック連結はOffice側と共有するため `common.utils` へ移した
（`MarkdownTableBuilder` / `MarkdownBlockJoiner`）。取り込み元ごとに書き分けると、
「Wordの表は崩れないのにWebの表は崩れる」という食い違いを入力形式の数だけ抱えることになる。

## 9. Stage 2 で入れるSSRF対策（予告）

Stage 2（`POST /markdownDraftUrl`）は、次を満たさない限り入れない。
`ghost.web.fetch.enabled` の既定は `false` とし、無効時は503を返す。

1. スキームは `http` / `https` のみ、ポートは既定 `80` / `443` のみ。
2. 認証情報付きURL（`https://user:pass@host/`）は拒否。
3. `InetAddress.getAllByName` の**全件**を検査し、ループバック・プライベート・リンクローカル
   （`169.254.169.254` を含む）・ワイルドカード・マルチキャスト・IPv4射影IPv6を拒否。
4. リダイレクトは自前ループで追い、**毎ホップで 1〜3 をやり直す**。
5. `Content-Type` は `text/html` / `application/xhtml+xml` のみ。
6. サイズは `BoundedInputStream` で**実読み取りバイト数**を打ち切る（`Content-Length` の詐称・chunked対策）。
7. 接続・読み取りのタイムアウト、User-Agentの明示（偽装しない）、連続実行の間隔制限。

取得は `java.net.http.HttpClient` を `followRedirects(NEVER)` で使う。
jsoupの接続API（`connect`）は使わない。名前解決・リダイレクト追跡・取得がライブラリの内側で完結し、
接続先IPを検査する隙間が無いため。この線引きは
`CodingConventionTest.jsoupConnectIsNotUsedInProductionCode` が機械的に守る。

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
| `WebMarkdownControllerTest` | token検証、ファイル未指定、入力不正の400、サイズ超過の413 |

フィクスチャHTML（`src/test/resources/web/`）はすべて自作で、実在サイトのHTMLは持ち込まない。

## 13. 未実施事項

- Stage 2（URL取得）は未実装。
- 実データ（ブラウザで保存した実ページ）での出力品質確認は未実施。Stage 2 へ進む前に、
  2〜3本のHTMLをMarkdown化して品質を確かめる。取得できるのは同じHTMLなので、
  品質が実用に届いていない状態でURL取得を足しても価値は増えない。
