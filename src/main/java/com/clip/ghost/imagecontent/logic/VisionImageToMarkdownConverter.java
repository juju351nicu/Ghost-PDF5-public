package com.clip.ghost.imagecontent.logic;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.clip.ghost.imagecontent.config.VisionProperties;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;

import lombok.RequiredArgsConstructor;

/**
 * 外部visionモデルを使って画像をMarkdownへ文字起こしする変換器。
 * <p>
 * Anthropic公式Java SDKを利用し、画像をbase64のimageブロックとして送信する。外部AI依存の詳細は
 * このクラスに閉じ込める。APIキーは設定で指定した環境変数から実行時に読み取り、コード・ログに出さない。
 */
@Component
@RequiredArgsConstructor
public class VisionImageToMarkdownConverter implements ImageToMarkdownConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(VisionImageToMarkdownConverter.class);
	private static final String SYSTEM_PROMPT = "あなたは画像内のテキストを忠実にMarkdownへ文字起こしします。表はMarkdownの表、コードはコードフェンスで囲みます。読み取れない箇所や推測で補った箇所は明示します。説明や前置きは書かず、文字起こし結果のMarkdownだけを返します。";
	private static final String USER_PROMPT = "この画像を文字起こししてMarkdownで返してください。";

	private final VisionProperties properties;

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
		return "vision(model=" + properties.getModel() + ")";
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
	 * 画像バイト列をvisionモデルでMarkdownへ文字起こしする。
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
			throw new ImageProcessingException("visionのAPIキーが設定されていません。");
		}
		LOGGER.info("visionで画像を文字起こしします。model={}", properties.getModel());
		try {
			AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
			MessageCreateParams params = buildParams(imageBytes, mediaType);
			Message response = client.messages().create(params);
			String markdown = response.content().stream().flatMap(block -> block.text().stream()).map(TextBlock::text)
					.collect(Collectors.joining());
			if (StringUtils.isBlank(markdown)) {
				throw new ImageProcessingException("visionから空の応答が返りました。");
			}
			return markdown;
		} catch (ImageProcessingException e) {
			throw e;
		} catch (RuntimeException e) {
			// SDKやネットワーク由来の例外を、APIキーや画像内容を露出させずに包む。
			throw new ImageProcessingException("vision文字起こしに失敗しました。", e);
		}
	}

	/**
	 * 画像を含むvisionリクエストを組み立てる。
	 *
	 * @param imageBytes 画像バイト列
	 * @param mediaType  画像のMIMEタイプ
	 * @return メッセージ生成パラメータ
	 */
	private MessageCreateParams buildParams(byte[] imageBytes, String mediaType) {
		Base64ImageSource source = Base64ImageSource.builder().mediaType(toMediaType(mediaType))
				.data(Base64.getEncoder().encodeToString(imageBytes)).build();
		ImageBlockParam imageBlock = ImageBlockParam.builder().source(source).build();
		return MessageCreateParams.builder().model(properties.getModel())
				.maxTokens((long) properties.getMaxOutputTokens()).system(SYSTEM_PROMPT)
				.addUserMessageOfBlockParams(List.of(ContentBlockParam.ofImage(imageBlock),
						ContentBlockParam.ofText(TextBlockParam.builder().text(USER_PROMPT).build())))
				.build();
	}

	/**
	 * MIMEタイプをSDKの画像メディアタイプへ変換する。
	 *
	 * @param mediaType 画像のMIMEタイプ
	 * @return SDKの画像メディアタイプ
	 * @throws ImageProcessingException 対応していない形式の場合
	 */
	private Base64ImageSource.MediaType toMediaType(String mediaType) {
		String normalized = StringUtils.lowerCase(StringUtils.defaultString(mediaType));
		return switch (normalized) {
		case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
		case "image/jpeg", "image/jpg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
		case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
		case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
		default -> throw new ImageProcessingException("対応していない画像形式です。mediaType=" + normalized);
		};
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
