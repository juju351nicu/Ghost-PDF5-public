package com.clip.ghost.aicontent.logic;

import java.util.List;

import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import com.clip.ghost.aicontent.config.AiProperties;
import com.clip.ghost.aicontent.exception.AiUnavailableException;

import lombok.RequiredArgsConstructor;

/**
 * 設定されたproviderに対応するMarkdown AI変換器を選択する。
 * <p>
 * 登録済みの {@link MarkdownAiConverter} から、設定 {@code ghost.ai.provider} と一致するものを返す。
 * 一致するproviderが無い場合は利用不可として扱う。{@code imagecontent.logic.ImageConverterResolver} と同じ形にする。
 */
@Component
@RequiredArgsConstructor
public class MarkdownAiConverterResolver {
	private final List<MarkdownAiConverter> converters;
	private final AiProperties properties;

	/**
	 * 設定されたproviderに一致する変換器を返す。
	 *
	 * @return 選択された変換器
	 * @throws AiUnavailableException 一致するproviderが存在しない場合
	 */
	public MarkdownAiConverter resolve() {
		String provider = properties.getProvider();
		return converters.stream().filter(converter -> Strings.CI.equals(converter.provider(), provider)).findFirst()
				.orElseThrow(() -> new AiUnavailableException("未対応のprovider設定です。provider=" + provider));
	}
}
