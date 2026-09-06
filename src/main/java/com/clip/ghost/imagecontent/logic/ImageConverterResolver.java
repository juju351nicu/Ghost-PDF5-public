package com.clip.ghost.imagecontent.logic;

import java.util.List;

import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import com.clip.ghost.imagecontent.config.ImageOcrProperties;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;

import lombok.RequiredArgsConstructor;

/**
 * 設定されたproviderに対応する画像→Markdown変換器を選択する。
 * <p>
 * 登録済みの {@link ImageToMarkdownConverter} から、設定 {@code ghost.ocr.provider} と一致するものを返す。
 * 一致するproviderが無い場合は利用不可として扱う。
 */
@Component
@RequiredArgsConstructor
public class ImageConverterResolver {
	private final List<ImageToMarkdownConverter> converters;
	private final ImageOcrProperties properties;

	/**
	 * 設定されたproviderに一致する変換器を返す。
	 *
	 * @return 選択された変換器
	 * @throws OcrUnavailableException 一致するproviderが存在しない場合
	 */
	public ImageToMarkdownConverter resolve() {
		String provider = properties.getProvider();
		return converters.stream().filter(converter -> Strings.CI.equals(converter.provider(), provider)).findFirst()
				.orElseThrow(() -> new OcrUnavailableException("未対応のprovider設定です。provider=" + provider));
	}
}
