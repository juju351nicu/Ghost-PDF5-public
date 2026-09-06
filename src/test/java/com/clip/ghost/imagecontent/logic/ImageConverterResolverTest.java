package com.clip.ghost.imagecontent.logic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.imagecontent.config.ImageOcrProperties;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;

/**
 * {@link ImageConverterResolver} のprovider選択を検証するテスト。
 */
class ImageConverterResolverTest {

	@Test
	@DisplayName("設定providerと一致する変換器を返す")
	void resolveReturnsMatchingProvider() {
		ImageToMarkdownConverter anthropic = stubConverter("anthropic");
		ImageToMarkdownConverter openai = stubConverter("openai");
		ImageOcrProperties properties = new ImageOcrProperties();
		properties.setProvider("openai");
		ImageConverterResolver resolver = new ImageConverterResolver(List.of(anthropic, openai), properties);

		assertSame(openai, resolver.resolve());
	}

	@Test
	@DisplayName("provider判定は大文字小文字を無視する")
	void resolveIgnoresCase() {
		ImageToMarkdownConverter anthropic = stubConverter("anthropic");
		ImageOcrProperties properties = new ImageOcrProperties();
		properties.setProvider("ANTHROPIC");
		ImageConverterResolver resolver = new ImageConverterResolver(List.of(anthropic), properties);

		assertSame(anthropic, resolver.resolve());
	}

	@Test
	@DisplayName("一致するproviderが無い場合は503相当の例外を投げる")
	void resolveThrowsForUnknownProvider() {
		ImageOcrProperties properties = new ImageOcrProperties();
		properties.setProvider("unknown");
		ImageConverterResolver resolver = new ImageConverterResolver(List.of(stubConverter("anthropic")), properties);

		assertThrows(OcrUnavailableException.class, resolver::resolve);
	}

	/**
	 * 指定providerを返すだけのテスト用変換器を生成する。
	 *
	 * @param provider provider識別子
	 * @return テスト用変換器
	 */
	private ImageToMarkdownConverter stubConverter(String provider) {
		return new ImageToMarkdownConverter() {
			@Override
			public boolean isEnabled() {
				return false;
			}

			@Override
			public String describe() {
				return provider;
			}

			@Override
			public String convert(byte[] imageBytes, String mediaType) {
				return "";
			}

			@Override
			public String provider() {
				return provider;
			}
		};
	}
}
