package com.clip.ghost.imagecontent.logic;

import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.imagecontent.config.OpenAiProperties;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import lombok.RequiredArgsConstructor;

/**
 * OpenAI（ChatGPT系）のvision対応モデルを使って画像をMarkdownへ文字起こしする変換器。
 * <p>
 * OpenAI公式Java SDKを利用し、画像をdata URI形式のimage_urlとして送信する。外部AI依存の詳細は
 * このクラスに閉じ込める。APIキーは設定で指定した環境変数から実行時に読み取り、コード・ログに出さない。
 */
@Component
@RequiredArgsConstructor
public class OpenAiImageToMarkdownConverter implements ImageToMarkdownConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiImageToMarkdownConverter.class);
	private static final String SYSTEM_PROMPT = "あなたは画像内のテキストを忠実にMarkdownへ文字起こしします。表はMarkdownの表、コードはコードフェンスで囲みます。読み取れない箇所や推測で補った箇所は明示します。説明や前置きは書かず、文字起こし結果のMarkdownだけを返します。";
	private static final String USER_PROMPT = "この画像を文字起こししてMarkdownで返してください。";

	private final OpenAiProperties properties;

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
	 * 画像バイト列をOpenAIのvision対応モデルでMarkdownへ文字起こしする。
	 *
	 * @param imageBytes 画像のバイト列
	 * @param mediaType  画像のMIMEタイプ
	 * @return 文字起こし結果のMarkdown
	 * @throws ImageProcessingException APIキー未設定、空応答、または呼び出し失敗の場合
	 */
	@Override
	public String convert(byte[] imageBytes, String mediaType) {
		String apiKey = resolveApiKey();
		if (StringUtils.isBlank(apiKey)) {
			throw new ImageProcessingException("OpenAIのAPIキーが設定されていません。");
		}
		LOGGER.info("OpenAIで画像を文字起こしします。model={}", properties.getModel());
		try {
			OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
			ChatCompletion completion = client.chat().completions().create(buildParams(imageBytes, mediaType));
			String markdown = completion.choices().stream().findFirst().flatMap(choice -> choice.message().content())
					.orElse("");
			if (StringUtils.isBlank(markdown)) {
				throw new ImageProcessingException("OpenAIから空の応答が返りました。");
			}
			return markdown;
		} catch (ImageProcessingException e) {
			throw e;
		} catch (RuntimeException e) {
			// SDKやネットワーク由来の例外を、APIキーや画像内容を露出させずに包む。
			throw new ImageProcessingException("OpenAI文字起こしに失敗しました。", e);
		}
	}

	/**
	 * 画像を含むchat completionリクエストを組み立てる。
	 *
	 * @param imageBytes 画像バイト列
	 * @param mediaType  画像のMIMEタイプ
	 * @return chat completion生成パラメータ
	 */
	private ChatCompletionCreateParams buildParams(byte[] imageBytes, String mediaType) {
		String dataUri = "data:" + normalizeMediaType(mediaType) + ";base64,"
				+ Base64.getEncoder().encodeToString(imageBytes);
		ChatCompletionContentPart textPart = ChatCompletionContentPart
				.ofText(ChatCompletionContentPartText.builder().text(USER_PROMPT).build());
		ChatCompletionContentPart imagePart = ChatCompletionContentPart.ofImageUrl(ChatCompletionContentPartImage
				.builder().imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url(dataUri).build()).build());
		return ChatCompletionCreateParams.builder().model(properties.getModel())
				.maxCompletionTokens((long) properties.getMaxOutputTokens()).addSystemMessage(SYSTEM_PROMPT)
				.addUserMessageOfArrayOfContentParts(List.of(textPart, imagePart)).build();
	}

	/**
	 * data URIに使うMIMEタイプを整える。jpgはjpegへ寄せる。
	 *
	 * @param mediaType 画像のMIMEタイプ
	 * @return 正規化したMIMEタイプ
	 */
	private String normalizeMediaType(String mediaType) {
		String normalized = StringUtils.lowerCase(StringUtils.defaultString(mediaType));
		return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
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
