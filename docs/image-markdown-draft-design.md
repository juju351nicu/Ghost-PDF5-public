# 画像Markdown下書きAPI設計（vision）

作成日: 2026-09-06
状態: 実装完了（2026-09-06）。3 provider（anthropic / openai / tesseract）、画像PDFの `mode=AUTO` 補完、最小UI、外側フェンス除去まで実装済み。受け入れ確認（表スクショ → Markdown表 → プレビュー `<table>` → 印刷）も実測済み。実測の詳細は `../成果物/09_OCRエンジン評価結果.md` の実測節を参照。

## 1. 目的

アップロードされた画像（スクリーンショット等）から、外部 vision モデルで文字起こしした Markdown 下書きを JSON で返す。
利用者が確認・編集してから、既存の Markdown 保存・一覧・プレビュー・印刷の導線へ載せることを想定する。

Excel の表・チャット・ソースコードのスクリーンショットをローカル OCR（Tesseract）で文字起こしする案を先に検証したが、
日本語スクショに対する精度が実用ラインに達しなかった（`../成果物/09_OCRエンジン評価結果.md` 相当の評価）。
同じ画像を vision モデルに直接読ませると、表構造も識別子も保持したまま読める。このため第1実装は vision（anthropic）とし、以降 openai / tesseract を同じ interface で追加した。

## 2. スコープと実装状況

- 画像1枚を対象とする（複数画像の一括処理は将来拡張）。
- 変換結果の自動保存・自動プレビューは行わず、既存 Markdown 操作を利用者が明示的に行う。
- provider は anthropic / openai / tesseract を実装済みで、`ghost.ocr.provider` で切り替える。
- 画像PDFの文字が無いページへの適用は、別途 `POST /markdownDraftPdf` の `mode=AUTO` として実装済み（同じ変換器を共有する）。

## 3. API概要

| 項目 | 内容 |
| --- | --- |
| Method | `POST` |
| Path | `/markdownDraftImage` |
| Content-Type | `multipart/form-data` |
| Response Content-Type | `application/json` |
| 認証相当 | 既存と同じ `access-token` header と session token の照合 |
| ファイル項目名 | `imageFile` |
| 保存 | 行わない |

Path は既存 `/markdownDraftPdf` と対になる命名にする。OCR / vision という実装手段を API 名に出さないため、
裏の変換実装を vision から Tesseract へ差し替えても名前が矛盾しない。

## 4. Request

- Header: `access-token`（既存 `AccessTokenValidator`）。
- multipart: `imageFile`（binary、必須）。PNG / JPEG / GIF / WEBP を受け付ける。
- DTO `ImageMarkdownDraftRequest { MultipartFile imageFile }`（`@NotNull`）。

## 5. Response

```json
{
  "fileName": "shot.png",
  "fileSize": 123456,
  "markdown": "## 見出し\n\n本文..."
}
```

DTO `ImageMarkdownDraftResponse { String fileName; Long fileSize; String markdown; }`。
`markdown` は vision の出力を改行正規化（CRLF/CR → LF、末尾空白除去）したもの。

## 6. 変換の責務分割

- `ImageToMarkdownConverter` interface（`imagecontent.logic`）。
  - `boolean isEnabled()`
  - `String describe()`（ログ用。モデル名等。キーは含めない）
  - `String convert(byte[] imageBytes, String mediaType)`
- interface には `String provider()` を持たせ、providerで実装を選べるようにする。
- 実装1 `AnthropicImageToMarkdownConverter`（`provider=anthropic`）。Anthropic 公式 Java SDK（`com.anthropic:anthropic-java`）で
  画像を base64 の image content block として送る。
- 実装2 `OpenAiImageToMarkdownConverter`（`provider=openai`）。OpenAI 公式 Java SDK（`com.openai:openai-java`）で
  画像を data URI の image_url として chat completions へ送る。
- 実装3 `TesseractImageToMarkdownConverter`（`provider=tesseract`）。ローカルのTesseract CLIを
  `ProcessBuilder`（専用の `ProcessCommandRunner` に限定）で実行し、外部送信なしで文字起こしする。オフライン/バッチ用。
- `ImageConverterResolver` が設定 `ghost.ocr.provider` に一致する実装を選ぶ。一致が無ければ 503。
- system で「画像を Markdown へ文字起こしする。表は Markdown 表、コードはコードフェンス、推測で補完した箇所は明示する」旨を指示する。
- LLM系（anthropic / openai）は出力全体を ```` ```markdown … ``` ```` で包むことがある。system で「出力全体をフェンスで囲まない。フェンスは画像内のソースコードにだけ使う」旨も指示し、後処理 `MarkdownFenceUnwrapper` で外側フェンスだけを剥がす（```` ```java ```` 等の言語指定フェンスは残す）。
- Controller / Service は resolver と interface 越しに使い、単体テストでは mock する。

## 7. 設定

`ghost.ocr.anthropic.*`（`@ConfigurationProperties`）。

| キー | 既定 | 用途 |
| --- | --- | --- |
| `enabled` | `false` | false の間は API が 503 |
| `model` | 実装時点の最新の視覚対応モデル（例: `claude-opus-5`） | 使用モデル |
| `api-key-env` | `ANTHROPIC_API_KEY` | キーを読む環境変数名 |
| `timeout-seconds` | `60` | 1回の変換タイムアウト |
| `max-image-file-size` | 既存 PDF API と同じ境界 | サイズ上限（413） |
| `max-image-pixels` | `40000000` | 画素数上限（400） |
| `max-output-tokens` | `8000` | 応答上限 |

provider は `ghost.ocr.provider`（既定 `anthropic`、他に `openai` / `tesseract`）で選ぶ。OpenAI provider は `ghost.ocr.openai.*` に
同じ形の設定（`enabled` 既定 `false`、`model` 既定 `gpt-4o`、`api-key-env` 既定 `OPENAI_API_KEY`、timeout・上限・`max-output-tokens`）を持つ。
Tesseract provider は `ghost.ocr.tesseract.*`（`enabled` 既定 `false`、`command` 既定 `tesseract`、`tessdata-directory`、
`languages` 既定 `jpn+eng`、`psm` 既定 `6`、`preserve-interword-spaces` 既定 `true`、`timeout-seconds`）を持ち、APIキーは不要。

API キーはコード・`application.yml`・ログに出さない。SDK は環境変数から読む。選択した provider が無効、またはキー未設定なら 503。

## 8. HTTP status

| Status | 条件 | レスポンス |
| --- | --- | --- |
| `200 OK` | 変換成功 | `ImageMarkdownDraftResponse` |
| `400 Bad Request` | `imageFile` 未指定、画像として読めない、画素数超過 | `ErrorResponse` |
| `403 Forbidden` | token 不一致 | 既存仕様 |
| `413 Payload Too Large` | サイズ上限超過 | `ErrorResponse` |
| `500 Internal Server Error` | vision 呼び出し・変換失敗 | `ErrorResponse` |
| `503 Service Unavailable` | 機能無効、またはキー未設定 | `ErrorResponse` |

400 / 500 / 503 は既存 `ErrorResponse` の `fieldErrors` 形式にそろえ、FE のモーダル表示にそのまま乗せる。

## 9. 責務とクラス配置

```text
com.clip.ghost.imagecontent
  controller.ImageMarkdownDraftController  -> multipart受付, token検証, サイズ検証, OpenAPI, Service委譲
  service.ImageMarkdownDraftService        -> 有効性確認(503), 画像検証, converter呼び出し, 正規化, DTO組み立て(build〇〇)
  logic.ImageToMarkdownConverter (if)      -> 画像→Markdown の抽象(provider()を持つ)
  logic.AnthropicImageToMarkdownConverter  -> Anthropic SDK呼び出し(provider=anthropic)
  logic.OpenAiImageToMarkdownConverter     -> OpenAI SDK呼び出し(provider=openai)
  logic.TesseractImageToMarkdownConverter  -> Tesseract CLI呼び出し(provider=tesseract)
  logic.ProcessCommandRunner / CommandRunner / CommandResult -> 外部プロセス起動(ProcessBuilderはここだけ)
  logic.ImageConverterResolver             -> ghost.ocr.provider で実装を選択
  logic.MarkdownFenceUnwrapper             -> 出力全体を包む外側フェンスの除去
  dto.ImageMarkdownDraftRequest / ImageMarkdownDraftResponse
  exception.ImageInputException(400) / ImageProcessingException(500) / OcrUnavailableException(503)
  config.ImageOcrProperties(provider) / AnthropicProperties / OpenAiProperties / TesseractProperties
```

依存方向は既存と同じ Controller → Service → Logic。外部 AI SDK やOCRエンジンの詳細は各 provider の変換器に閉じ込める。
画像PDFの `markdownDraftPdf` の `mode=AUTO` は、機能横断で共有の `ImageConverterResolver` を `pdfcontent.service` から使う（`CodingConventionTest` の層ルールでその横断利用だけを許可）。

## 10. セキュリティ

- 既定無効。外部送信は明示的に有効化した場合のみ。
- API キーは環境変数。コード・設定・ログに出さない。
- 画像内容・変換結果本文をログに出さない（一時ファイルパスは debug のみ）。
- 画素数・サイズ上限、タイムアウト。
- `SECURITY.md` に「External AI Processing (Optional)」を追記済み。

## 11. テスト計画

- `ImageMarkdownDraftControllerTest`（standalone MockMvc、Mockito）: token 一致/不一致(403)、未指定(400)、サイズ上限(413)、無効時(503)、Content-Type。
- `ImageMarkdownDraftServiceTest`（converter を mock）: 有効性確認、正規化、DTO 組み立て、変換失敗(500)、無効(503)。
- `OpenApiDocumentationTest` に `/markdownDraftImage` の path / multipart request / 200 schema / 400 / 403 / 413 / 500 / 503 を追加。
- 実 API を叩く vision の自動テストは、コスト事故防止のため設けない（変換器を mock）。実 API 確認は手動運用（実測結果は `../成果物/09_OCRエンジン評価結果.md`）。
- Tesseract は実行を伴う統合テストを `@Tag("ocr")` とし、未導入環境では `Assumptions` で skip。`fast-test` の `excludedGroups` に `ocr` を追加。
- `MarkdownFenceUnwrapper` の除去ロジック（```` ```markdown ```` / ```` ```md ```` / 裸の ```` ``` ```` を剥がす、```` ```java ```` は残す、内側フェンス入りの扱い）を単体テスト。
- `ProcessBuilder` は `ProcessCommandRunner` だけに限定し、`CodingConventionTest` のソーススキャンで検出。
- `CodingConventionTest` に `imagecontent` の Controller → Service → Logic 依存方向ルールを追加（共有変換器のみ `pdfcontent.service` からの利用を許可）。

## 12. 実装完了条件

- 既存 API（`/markdownDraftPdf` 等）の契約・挙動が変わらない。
- 既定無効で、有効化には設定が必要。キーがコード・ログに出ない。
- 変換の外部依存が各 provider の変換器に閉じている。
- 400 / 403 / 413 / 500 / 503 が OpenAPI に現れる。
- `mvn test` が緑（tesseract 統合テストは未導入環境で skip）。

すべて達成済み。受け入れ確認（表 → Markdown表 → プレビュー `<table>` → 印刷）の実測は `../成果物/09_OCRエンジン評価結果.md` の実測節に記録している。
