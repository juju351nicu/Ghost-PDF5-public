package com.clip.ghost.aicontent.logic;

import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.aicontent.config.OpenAiAiProperties;
import com.clip.ghost.aicontent.enums.AiTaskType;
import com.clip.ghost.aicontent.exception.AiProcessingException;
import com.clip.ghost.common.utils.MarkdownFenceUnwrapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import lombok.RequiredArgsConstructor;

/**
 * OpenAI（ChatGPT系）のモデルを使ってMarkdown本文を整形・要約する変換器。
 * <p>
 * OpenAI公式Java SDKを利用し、system/userプロンプトはテキストのみで送信する。外部AI依存の詳細は
 * このクラスに閉じ込める。APIキーは設定で指定した環境変数から実行時に読み取り、コード・ログに出さない。
 * 画像文字起こしの {@code OpenAiImageToMarkdownConverter} と同じ構造にする。
 */
@Component
@RequiredArgsConstructor
public class OpenAiMarkdownAiConverter implements MarkdownAiConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiMarkdownAiConverter.class);

	private final OpenAiAiProperties properties;

	/**
	 * 機能が有効かつAPIキーが設定されている場合に利用可能と判定する。
	 *
	 * @return 利用可能な場合はtrue
	 */
	@Override
	public boolean isEnabled() {
		return properties.isEnabled() && StringUtils.isNotBlank(resolveApiKey());
	}

	/**
	 * 使用モデル名を含む識別文字列を返す。APIキーは含めない。
	 *
	 * @return 実装を説明する文字列
	 */
	@Override
	public String describe() {
		return "openai(model=" + properties.getModel() + ")";
	}

	/**
	 * provider識別子 {@code openai} を返す。
	 *
	 * @return provider識別子
	 */
	@Override
	public String provider() {
		return "openai";
	}

	/**
	 * Markdown本文をOpenAIのモデルで整形・要約する。
	 *
	 * @param markdown 変換対象のMarkdown本文
	 * @param taskType 変換タスク（整形／要約）
	 * @return 変換結果のMarkdown
	 * @throws AiProcessingException APIキー未設定、空応答、または呼び出し失敗の場合
	 */
	@Override
	public String transform(String markdown, AiTaskType taskType) {
		String apiKey = resolveApiKey();
		if (StringUtils.isBlank(apiKey)) {
			throw new AiProcessingException("OpenAIのAPIキーが設定されていません。");
		}
		LOGGER.info("OpenAIでMarkdown本文を変換します。model={}, task={}", properties.getModel(), taskType);
		try {
			OpenAIClient client = buildClient(apiKey);
			ChatCompletion completion = client.chat().completions().create(buildParams(markdown, taskType));
			ChatCompletion.Choice choice = completion.choices().stream().findFirst()
					.orElseThrow(() -> new AiProcessingException("OpenAIから空の応答が返りました。"));
			if (isTruncated(choice.finishReason())) {
				throw new AiProcessingException("OpenAIの応答が出力上限で途中終了しました。");
			}
			String result = MarkdownFenceUnwrapper.unwrap(choice.message().content().orElse(""));
			if (StringUtils.isBlank(result)) {
				throw new AiProcessingException("OpenAIから空の応答が返りました。");
			}
			return result;
		} catch (AiProcessingException e) {
			throw e;
		} catch (RuntimeException e) {
			// SDKやネットワーク由来の例外を、APIキーやMarkdown本文を露出させずに包む。
			throw new AiProcessingException("OpenAI Markdown変換に失敗しました。", e);
		}
	}

	/**
	 * 設定のタイムアウトを適用したクライアントを生成する。
	 * <p>
	 * タイムアウトを渡さないとSDKの既定値で待ち続ける。応答が返らないまま利用者のリクエストを
	 * 占有し続けないよう、設定値で打ち切る。
	 *
	 * @param apiKey APIキー
	 * @return OpenAIクライアント
	 */
	private OpenAIClient buildClient(String apiKey) {
		return OpenAIOkHttpClient.builder().apiKey(apiKey)
				.timeout(Duration.ofSeconds(properties.getTimeoutSeconds())).build();
	}

	/**
	 * Markdown変換リクエストを組み立てる。
	 *
	 * @param markdown 変換対象のMarkdown本文
	 * @param taskType 変換タスク（整形／要約）
	 * @return chat completion生成パラメータ
	 */
	private ChatCompletionCreateParams buildParams(String markdown, AiTaskType taskType) {
		return ChatCompletionCreateParams.builder().model(properties.getModel())
				.maxCompletionTokens((long) properties.getMaxOutputTokens())
				.addSystemMessage(MarkdownAiPromptBuilder.buildSystemPrompt(taskType))
				.addUserMessage(MarkdownAiPromptBuilder.buildUserPrompt(markdown, taskType)).build();
	}

	/**
	 * 応答が出力トークン上限で打ち切られたかを判定する。
	 * <p>
	 * 打ち切られた場合、OpenAIはエラーを返さず途中までの内容で正常終了する。REFINEは出力が入力とほぼ
	 * 同じ長さになり得るため、気づかずに採用すると原文の後半が消えたMarkdownを正しい結果として扱ってしまう。
	 * 途中終了を検出し、正常応答として返さないようにする。
	 *
	 * @param finishReason 応答の終了理由
	 * @return 出力上限による打ち切りの場合はtrue
	 */
	boolean isTruncated(ChatCompletion.Choice.FinishReason finishReason) {
		return ChatCompletion.Choice.FinishReason.LENGTH.equals(finishReason);
	}

	/**
	 * 設定で指定された環境変数からAPIキーを読み取る。
	 *
	 * @return APIキー。未設定の場合はnull
	 */
	private String resolveApiKey() {
		return System.getenv(properties.getApiKeyEnv());
	}
}
