# Markdown本文AI整形・要約API設計（Phase E）

作成日: 2026-09-17
状態: 実装完了（2026-09-17）。`aicontent` package新設、Anthropic / OpenAI 2 provider、入力文字数のコストガード、
プロンプト集約、最小UIまで実装済み。**実APIでの確認はOpenAI providerのみ2026-09-17に実施済み**（第12節）。
初回確認でSUMMARIZEがシステム名を省略する事象が見つかり、system promptを調整（第6節）のうえ再検証まで完了した。
Anthropic providerでの実API確認は未実施。

## 1. 目的

保存済み/編集中のMarkdown本文を、外部AI（Anthropic / OpenAI）で「整形（REFINE）」または「要約（SUMMARIZE）」し、
結果のMarkdownをJSONで返す。既存の画像文字起こし（`/markdownDraftImage` 等）とは別APIで、既存の挙動には影響しない。

`docs/future-document-ai-roadmap.md` のPhase Eは「足場のみ」だった。足場（`imagecontent` のprovider抽象）は
画像バイト列専用のシグネチャ（`convert(byte[] imageBytes, String mediaType)`）で、テキスト入出力の整形・要約には
そのまま使えない。本設計は同じ構造をテキスト専用に作り直したものであり、画像文字起こしの実装・テスト・設定は変更しない。

## 2. スコープと実装状況

- 保存済みファイル名ではなく、Markdown本文（`content`）を直接受け取る。`POST /markdownPreview` と同じ形。
  保存前の下書き段階でも使え、Service層が読み込み用に `markdowncontent.service` へ依存する必要も生まれない。
- 整形（REFINE）と要約（SUMMARIZE）は1本のAPI + `task` enumにまとめる（provider選択・APIキー読み取り・
  エラー処理・入力上限チェックが両者で完全に同じで、変わるのはプロンプトだけのため）。
- providerはAnthropic / OpenAIの2つ。ローカルLLM（Tesseractに相当するローカル完結処理）は対象外。
- 結果は自動保存・自動上書きを行わない。既存のMarkdown保存/更新APIへ渡す判断は利用者に委ねる。
- Vector DB / embedding / chunking、矛盾検出、テスト観点生成、設計書Q&A（Phase F以降）は対象外。

## 3. API概要

| 項目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/markdownAiTransform` |
| Content-Type | `application/json` |
| Response Content-Type | `application/json` |
| 認証相当 | 既存と同じ `access-token` header と session token の照合 |
| 保存 | 行わない |

## 4. Request

```json
{
  "content": "# 設計メモ\n\n本文...",
  "task": "REFINE"
}
```

DTO `MarkdownAiTransformRequest { String content; AiTaskType task; }`。両方 `@NotNull`。
`task` は `SUMMARIZE` または `REFINE`（大文字小文字は無視）。

## 5. Response

```json
{
  "task": "REFINE",
  "markdown": "## 見出し\n\n整形後の本文...",
  "inputCharacterCount": 1234,
  "outputCharacterCount": 987
}
```

DTO `MarkdownAiTransformResponse { AiTaskType task; String markdown; Integer inputCharacterCount; Integer outputCharacterCount; }`。
要約・整形は原文の意味を変えていないかを機械的に保証できないため、入力・出力の文字数を必ず添え、
利用者が「極端に短くなっていないか」を一目で気づけるようにする。`markdown` は改行正規化（CRLF/CR → LF、末尾空白除去）済み。

## 6. 変換の責務分割

- `MarkdownAiConverter` interface（`aicontent.logic`）。`imagecontent.logic.ImageToMarkdownConverter` と同じ形で、
  引数だけ `byte[] imageBytes, String mediaType` → `String markdown, AiTaskType taskType` に変える。
  - `boolean isEnabled()`
  - `String describe()`（ログ用。モデル名等。キーは含めない）
  - `String transform(String markdown, AiTaskType taskType)`
  - `String provider()`
- 実装1 `AnthropicMarkdownAiConverter`（`provider=anthropic`）。Anthropic公式Java SDKでsystem/userプロンプトを
  テキストのみで送信する（`MessageCreateParams.Builder#system(String)` / `#addUserMessage(String)`）。
- 実装2 `OpenAiMarkdownAiConverter`（`provider=openai`）。OpenAI公式Java SDKで同様にテキストのみ送信する
  （`ChatCompletionCreateParams.Builder#addSystemMessage(String)` / `#addUserMessage(String)`）。
- `MarkdownAiConverterResolver` が設定 `ghost.ai.provider` に一致する実装を選ぶ。一致が無ければ503。
- プロンプト文言は `MarkdownAiPromptBuilder`（`aicontent.logic`）に集約する。各providerのconverterは
  組み立て済みのsystem/userプロンプトを受け取るだけにし、providerごとにプロンプトがずれて
  「Anthropicで整形した結果とOpenAIで整形した結果の書式が違う」という気づきにくい差分が生まれないようにする。
  実AI呼び出しなしで固定入力によりテストできる（`MarkdownAiPromptBuilderTest`）。
  - REFINE: 意味を変えずに誤字脱字・表記ゆれ・Markdown構文の乱れ（見出しレベル、リストの混在等）を直す。原文の情報を削らない。
  - SUMMARIZE: 原文の要点を落とさず短くする。数値・結論に加え、人名・製品名・システム名/プロジェクト名・組織名などの
    固有名詞は、本文中に1回しか登場しない場合でも省略せず保持する（実API確認でシステム名の欠落が見つかり調整した。第12節）。
  - 両方とも「説明や前置きを書かず結果のMarkdownだけを返す」「出力全体をコードフェンスで囲まない」制約を持つ
    （`AnthropicImageToMarkdownConverter` と同じ制約）。
- `MarkdownFenceUnwrapper` による外側フェンス除去は画像文字起こしと共有する（第9節）。
- **出力の途中切断（truncation）の検出。** Anthropic/OpenAIとも、応答が`max-output-tokens`に達した場合、
  エラーにはならず途中までの内容で正常終了する（Anthropic: `stopReason=MAX_TOKENS`、OpenAI:
  `finishReason=LENGTH`）。REFINEは出力が入力とほぼ同じ長さになり得るうえ、結果は編集欄を自動上書きせず
  確認用の別領域に出す設計（第13節）のため、気づかずに採用すると原文の後半が消えたMarkdownを正しい結果として
  扱ってしまう。両converterはこの終了理由を見て打ち切りを検出し、`AiProcessingException`（500）として扱う
  （`AnthropicMarkdownAiConverter#isTruncated` / `OpenAiMarkdownAiConverter#isTruncated`、
  それぞれ固定入力で単体テスト済み）。文字数表示（第5節）だけでは長文の末尾欠落は拾えないため、この検出は
  文字数チェックを補う形で必須にした。

## 7. 設定

`ghost.ai` prefixで新設する。APIキー用の環境変数名は画像Markdown下書き（`ghost.ocr.*`）と共用できる
（同じAnthropic/OpenAIアカウントの想定）。

| キー | 既定 | 用途 |
| --- | --- | --- |
| `ghost.ai.provider` | `anthropic` | 使用するprovider（`anthropic` / `openai`） |
| `ghost.ai.markdown.max-input-characters` | `24000` | 入力文字数上限。超過は変換器を呼ばず400 |
| `ghost.ai.anthropic.enabled` | `false` | falseの間はAPIが503 |
| `ghost.ai.anthropic.model` | `claude-opus-5` | 使用モデル |
| `ghost.ai.anthropic.api-key-env` | `ANTHROPIC_API_KEY` | キーを読む環境変数名 |
| `ghost.ai.anthropic.timeout-seconds` | `60` | 1回の変換タイムアウト |
| `ghost.ai.anthropic.max-output-tokens` | `16000` | 応答上限 |
| `ghost.ai.openai.enabled` | `false` | falseの間はAPIが503 |
| `ghost.ai.openai.model` | `gpt-4o` | 使用モデル |
| `ghost.ai.openai.api-key-env` | `OPENAI_API_KEY` | キーを読む環境変数名 |
| `ghost.ai.openai.timeout-seconds` | `60` | 1回の変換タイムアウト |
| `ghost.ai.openai.max-output-tokens` | `16000` | 応答上限 |

APIキーはコード・`application.yml`・ログに出さない。SDKは環境変数から読む。選択したproviderが無効、
またはキー未設定なら503。

### 既定値の根拠

- `max-output-tokens`（16,000、両provider共通）: REFINEは原文の情報を削らない指示のため、出力が入力とほぼ
  同じ長さになり得る（SUMMARIZEより長くなり得る）。画像文字起こし（`ghost.ocr.*.max-output-tokens=8000`）より
  高めにした。OpenAI（`gpt-4o`）の完了トークン上限は実質16,384のため、そこに収まる値として16,000を選んだ。
- `max-input-characters`（24,000文字）: **`max-output-tokens`から独立に決めていない。** REFINEは出力が
  入力とほぼ同じ長さになり得るため、2つの値を別々に決めると、入力上限を通った本文の出力が出力トークン上限を
  超え、**気づかれないまま途中で切れたMarkdownを正常応答として返してしまう事故**が構造的に起きる
  （実際にAnthropic/OpenAIとも、出力上限に達してもエラーにはならず正常終了する）。
  第12節の実測で、751文字の日本語Markdown（表・コードブロック含む）に対するREFINE出力は約0.43トークン/文字
  だった。実運用の文書は表・コードブロックの比率や英数字の混在度で変動するため、この実測値そのままではなく
  安全側に倍程度の余裕（約0.67トークン/文字を仮定）を見て、`16,000 ÷ 0.67 ≈ 24,000`文字を既定にした。
  この見積もりが外れて実際に出力が打ち切られた場合に備え、`AnthropicMarkdownAiConverter` /
  `OpenAiMarkdownAiConverter`はAPIの終了理由（Anthropic: `stopReason=MAX_TOKENS`、OpenAI: `finishReason=LENGTH`）
  を見て打ち切りを検出し、`AiProcessingException`（500）として扱う。途中で切れたMarkdownを200として
  返すことはない（第6節・第9節）。
- 実運用でのより正確な比率は、多様な文書（コード比率の高いもの・英語比率の高いものを含む）での追加実測に
  応じて見直す。見直した場合はこの節を更新する。

## 8. 例外とHTTP status

`aicontent.exception` に3種類を追加し、`GlobalExceptionErrorHandler` へハンドラーを足した。
`imagecontent` の3例外（`ImageInputException` / `ImageProcessingException` / `OcrUnavailableException`）と
同じ役割分担にする。

| Status | 条件 | 例外 | レスポンス |
| --- | --- | --- | --- |
| `200 OK` | 変換成功 | - | `MarkdownAiTransformResponse` |
| `400 Bad Request` | `content` / `task` 未指定、入力文字数が上限超過 | `AiInputException`（文字数超過時） | `ErrorResponse` |
| `403 Forbidden` | token不一致 | - | 既存仕様 |
| `500 Internal Server Error` | AI呼び出し・変換失敗、空応答 | `AiProcessingException` | `ErrorResponse` |
| `503 Service Unavailable` | 機能無効、またはキー未設定 | `AiUnavailableException` | `ErrorResponse` |

`AiInputException` は入力文字数と上限をメッセージへ含める（`PdfPageLimitExceededException` と同じ考え方。
文字数は文書の内容ではないため、レスポンス・ログへ出しても情報漏洩にならない）。
400 / 500 / 503は既存 `ErrorResponse` の `fieldErrors` 形式にそろえ、FEの既存エラー表示にそのまま乗る。
FEのエラーコード対応表（`api-error-utils.js`）にも `aiInputError` / `aiProcessingError` / `aiUnavailable` を追加し、
`aiInputError` / `aiUnavailable` は利用者が自分で対処できる失敗（`RECOVERABLE_ERROR_CODES`）として扱う。

## 9. 責務とクラス配置

```text
com.clip.ghost.aicontent
  controller.MarkdownAiController        -> JSON受付, token検証, OpenAPI, Service委譲
  service.MarkdownAiService              -> 有効性確認(503), 入力文字数上限チェック(400), converter呼び出し, 正規化, DTO組み立て(build〇〇)
  logic.MarkdownAiConverter (if)         -> Markdown本文の整形・要約の抽象(provider()を持つ)
  logic.AnthropicMarkdownAiConverter     -> Anthropic SDK呼び出し(provider=anthropic)
  logic.OpenAiMarkdownAiConverter        -> OpenAI SDK呼び出し(provider=openai)
  logic.MarkdownAiConverterResolver      -> ghost.ai.provider で実装を選択
  logic.MarkdownAiPromptBuilder          -> タスクごとのsystem/userプロンプトの集約
  dto.MarkdownAiTransformRequest / MarkdownAiTransformResponse
  enums.AiTaskType                       -> SUMMARIZE / REFINE（CodeEnum<String>）
  exception.AiInputException(400) / AiProcessingException(500) / AiUnavailableException(503)
  config.AiProperties(provider) / AiMarkdownProperties(max-input-characters) / AiAnthropicProperties / AiOpenAiProperties
```

依存方向はController → Service → Logic。既存の `imageControllerServiceLogicDependenciesKeepDirection` は
`imagecontent.logic` を共有する `pdfcontent.service` からの横断利用を `optionalLayer` で許可しているが、
`aicontent` では同じ横断利用を作らなかった。共有ロジック `MarkdownFenceUnwrapper` を
`imagecontent.logic` から `com.clip.ghost.common.utils` へ移し、画像文字起こしとMarkdown本文AI変換の
両方から使えるようにしたため、`aicontent` の依存方向は `pdfControllerServiceLogicDependenciesKeepDirection` と
同じ単純な3層（`optionalLayer` なし）で固定できる（`CodingConventionTest#aiControllerServiceLogicDependenciesKeepDirection`）。
`MarkdownFenceUnwrapper` の移動に伴い、`AnthropicImageToMarkdownConverter` / `OpenAiImageToMarkdownConverter` の
import先を変更した。ロジック自体・テスト内容は変更していない。

## 10. セキュリティ

- 既定無効。外部送信は明示的に有効化した場合のみ。
- APIキーは環境変数。コード・設定・ログに出さない。
- Markdown本文・AI応答本文をログに出さない（`AnthropicImageToMarkdownConverter` のログ方針と同じ。
  ログにはmodel名・task種別のみを出す）。
- 入力文字数上限、タイムアウト。

## 11. テスト計画

- `AiTaskTypeTest`: `fromKey` / `KEY_MAP` / 大文字小文字無視 / 不正値の例外 / Jackson変換（`PdfMarkdownDraftModeTest` と同じ形）。
- `MarkdownAiPromptBuilderTest`: 実AI呼び出しなしで固定入力によりsystem/userプロンプトの内容を検証。
- `MarkdownAiConverterResolverTest`: 設定providerに応じたconverter選択、大文字小文字無視、未対応providerで503相当の例外。
- `AnthropicMarkdownAiConverterTest` / `OpenAiMarkdownAiConverterTest`: 無効時の`isEnabled`、`describe`のモデル名、`provider`
  （実APIは呼ばない。画像文字起こしの`AnthropicImageToMarkdownConverterTest`と同じ方針）。加えて
  `isTruncated`（打ち切り検出）を、SDKの終了理由の定数（`StopReason.MAX_TOKENS` / `FinishReason.LENGTH`）
  だけを使い、ネットワーク呼び出しなしで検証する。
- `MarkdownAiServiceTest`（converterをmock）: REFINE / SUMMARIZE それぞれの正常系、入力文字数超過時に
  converterが1度も呼ばれず400、無効時503、変換失敗の伝播（本文・APIキーを含まない）、空応答時の失敗伝播。
- `MarkdownAiControllerTest`（standalone MockMvc、Mockito）: token一致/不一致(403)、`content`/`task`未指定(400)、無効時(503)、
  ApiResult共通ラッパー形式。
- `CodingConventionTest#aiControllerServiceLogicDependenciesKeepDirection`: Controller → Service → Logic依存方向。
- `MarkdownFenceUnwrapperTest`は`common.utils`パッケージへ移動し、内容は変更していない。
- 実APIを叩く自動テストは、コスト事故防止のため設けない（converterはinterfaceをmock）。実API確認は手動運用（第12節）。
- 既存の`/markdownDraftImage` / `/markdownPreview` / `/saveMarkdown`等のテストは変更しておらず、全て緑のまま。

## 12. 実API確認

**OpenAI providerで2026-09-17に実施済み。** `gpt-4o`で日本語設計メモ（表・見出し・コードブロック・数値・
固有名詞を含む751文字）に対しREFINE/SUMMARIZEを実行し、`POST /markdownAiTransform`のHTTP応答・変換結果・
コスト（実測トークン数）を確認した。詳細・全文結果は `../成果物/30_実API確認結果_PhaseE_AI整形要約.md` を参照。

要点:

- REFINE: 見出し・箇条書き・表・コードブロック・数値・固有名詞・結論を含め、原文の情報を完全に保持した
  （プロンプト調整後の再検証でも回帰なしを確認）。
- SUMMARIZE（初回）: 数値・結論・人名・製品名・承認者名は保持されたが、システム名（本文中1箇所だけの固有名詞）が
  2回とも省略された。「固有名詞を保持する」という抽象的な指示だけでは完全ではないことが分かった。
- SUMMARIZE用system promptを調整した（第6節）。固有名詞の種類（人名・製品名・システム名/プロジェクト名・組織名）を
  具体的に列挙し、「本文中に1回しか登場しない場合でも省略しない」ことを明示する指示へ変更した。
- 調整後に同じ入力でSUMMARIZEを2回再実行し、**いずれもシステム名は保持された。** ただし2回目は文体が変わり
  人名が省略される揺らぎが見られた。LLM出力の実行ごとの揺らぎ自体は解消できるものではなく、
  自動保存しない設計（本文冒頭・第2節）で人間確認を前提にしていることの重要性を裏付ける結果になった。
- コスト: REFINE 979トークン（約$0.0057）、SUMMARIZE 823トークン（約$0.0043、調整前プロンプトでの実測）。
  gpt-4o料金（$2.50/1M input、$10.00/1M output）で計算。第7節の`max-output-tokens=16000`はこの入力規模に対して
  十分な余裕があることを確認した。
- **出力トークン上限に対して独立に決めていた`max-input-characters`（当初60,000文字）を24,000文字へ見直した**
  （第7節）。今回の実測（751文字→出力428トークン、約0.43トークン/文字）を基に、安全側の余裕を見て
  `max-output-tokens`から逆算した値。上限付近（24,000文字規模）での実測はまだ行っておらず、
  この見積もりが妥当かは今後の追加実測課題として残る。

**Anthropic providerでの実API確認は未実施。** このセッションではAnthropicのAPIキーが用意できなかったため。
`ghost.ai.provider=anthropic`と`ANTHROPIC_API_KEY`を設定し、同じ手順（`成果物/30`参照）で確認できる。
providerの切り替えだけで動く構造であることは`MarkdownAiConverterResolver`の単体テストで担保しているが、
実際のAnthropicモデルでの品質・コストは別途確認が必要。

## 13. FE最小UI

既存Markdownメモカード（`main.html` / `pdf-app.js`）に「AI整形」「AI要約」ボタンを追加した。

- 実行前に「AI整形・AI要約は、入力中のMarkdown本文を外部AI（Anthropic / OpenAI）へ送信します。」という
  インライン注意書きを常時表示する（`mode=VISION`の費用注意と同じ`markdown-draft-mode__notice`スタイルを流用）。
- 結果は既存のMarkdown編集欄を自動上書きせず、確認用の別領域（`markdown-panel__ai-result`）に
  タスク種別・入力/出力文字数とともに表示する。利用者が「編集欄へ採用」を押した場合だけ編集欄へ反映し、
  「破棄」で確認欄を閉じる。
- HTTPは`fetch-client.js`経由の`MarkdownApiClient.transformMarkdownAi(content, task)`のみ。
- `access-token`検証・処理状態パネル（`ProcessState.PROCESS_LABEL.AI_TRANSFORM`）は既存の他API呼び出しと同じ
  仕組みに乗せている。

ブラウザでの動作確認: Spring Bootアプリを起動し、Markdownメモタブで本文を入力して「AI整形」を押すと、
機能無効（既定設定）時は503相当のエラーが利用者が対処できる失敗として画面上部に表示され、「閉じる」で
消せることを確認した。実際の変換結果表示・採用/破棄の確認は、第12節の実APIキー設定後に行う。
