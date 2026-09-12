package com.clip.ghost.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.clip.ghost.common.converter.StringToCodeEnumConverterFactory;

/**
 * 区分値enumのリクエストbinding設定。
 * <p>
 * {@link StringToCodeEnumConverterFactory} をSpring MVCの変換に登録し、multipartフォームやquery parameterの
 * コード値をenumで受け取れるようにする。Controller単体テスト（standaloneSetup）からも同じ登録を使えるよう、
 * 登録処理はこのクラス1箇所に置く。
 */
@Configuration
public class CodeEnumWebMvcConfig implements WebMvcConfigurer {
	/**
	 * 区分値enumのConverterFactoryを登録する。
	 *
	 * @param registry Spring MVCの変換レジストリ
	 */
	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverterFactory(new StringToCodeEnumConverterFactory());
	}
}
