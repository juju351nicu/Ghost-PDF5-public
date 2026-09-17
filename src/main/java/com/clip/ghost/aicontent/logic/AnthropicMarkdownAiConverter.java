package com.clip.ghost.aicontent.logic;

import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.clip.ghost.aicontent.config.AnthropicAiProperties;
import com.clip.ghost.aicontent.enums.AiTaskType;
import com.clip.ghost.aicontent.exception.AiProcessingException;
import com.clip.ghost.common.utils.MarkdownFenceUnwrapper;

import lombok.RequiredArgsConstructor;

/**
 * Anthropic（Claude）のモデルを使ってMarkdown本文を整形・要約する変換器。
 * <p>
 * Anthropic公式Java SDKを利用し、system/userプロンプトはテキストのみで送信する。外部AI依存の詳細は
 * このクラスに閉じ込める。APIキーは設定で指定した環境変数から実行時に読み取り、コード・ログに出さない。
 * 画像文字起こしの {@code AnthropicImageToMarkdownConverter} と同じ構造にする。
 */
@Component
@RequiredArgsConstructor
public class AnthropicMarkdownAiConverter implements MarkdownAiConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicMarkdownAiConverter.class);

	private final AnthropicAiProperties properties;

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
		return "anthropic(model=" + properties.getModel() + ")";
	}

	/**
	 * provider識別子 {@code anthropic} を返す。
	 *
	 * @return provider識別子
	 */
	@Override
	public String provider() {
		return "anthropic";
	}

	/**
	 * Markdown本文をAnthropicのモデルで整形・要約する。
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
			throw new AiProcessingException("AnthropicのAPIキーが設定されていません。");
		}
		LOGGER.info("AnthropicでMarkdown本文を変換します。model={}, task={}", properties.getModel(), taskType);
		try {
			AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
			MessageCreateParams params = buildParams(markdown, taskType);
			Message response = client.messages().create(params);
			String result = MarkdownFenceUnwrapper.unwrap(response.content().stream()
					.flatMap(block -> block.text().stream()).map(TextBlock::text).collect(Collectors.joining()));
			if (StringUtils.isBlank(result)) {
				throw new AiProcessingException("Anthropicから空の応答が返りました。");
			}
			return result;
		} catch (AiProcessingException e) {
			throw e;
		} catch (RuntimeException e) {
			// SDKやネットワーク由来の例外を、APIキーやMarkdown本文を露出させずに包む。
			throw new AiProcessingException("Anthropic Markdown変換に失敗しました。", e);
		}
	}

	/**
	 * Markdown変換リクエストを組み立てる。
	 *
	 * @param markdown 変換対象のMarkdown本文
	 * @param taskType 変換タスク（整形／要約）
	 * @return メッセージ生成パラメータ
	 */
	private MessageCreateParams buildParams(String markdown, AiTaskType taskType) {
		return MessageCreateParams.builder().model(properties.getModel())
				.maxTokens((long) properties.getMaxOutputTokens())
				.system(MarkdownAiPromptBuilder.buildSystemPrompt(taskType))
				.addUserMessage(MarkdownAiPromptBuilder.buildUserPrompt(markdown, taskType)).build();
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
