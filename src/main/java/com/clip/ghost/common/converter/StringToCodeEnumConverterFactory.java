package com.clip.ghost.common.converter;

import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import com.clip.ghost.pdfcontent.enums.CodeEnum;

import lombok.RequiredArgsConstructor;

/**
 * multipartフォームやquery parameterの文字列を、{@link CodeEnum} のコード値でenumへ変換するConverterFactory。
 * <p>
 * フォームのbindingはJacksonを通らないため、{@code @JsonCreator} を付けた {@code fromKey} が呼ばれない。
 * Spring標準の変換は {@code Enum.valueOf} 相当で定数名と完全一致する必要があり、{@code insertOption=1} や
 * {@code mode=vision} は変換できない。APIが受け取るコード値を変えずにenumで受け取るため、変換をここへ1箇所に置く。
 * <p>
 * enumのinterfaceに対して登録するため、Springは変換先の型階層で {@code Enum} より先にこのFactoryを見つける。
 * 区分値enumが増えても、Controllerごとに {@code @InitBinder} を足す必要はない。
 */
public class StringToCodeEnumConverterFactory implements ConverterFactory<String, CodeEnum<?>> {
	/**
	 * 変換先のenum型に対応するConverterを取得する。
	 *
	 * @param <T>        変換先の区分値enum型
	 * @param targetType 変換先の型
	 * @return コード値でenumへ変換するConverter
	 */
	@Override
	public <T extends CodeEnum<?>> Converter<String, T> getConverter(Class<T> targetType) {
		return new StringToCodeEnum<>(targetType);
	}

	/**
	 * 1つのenum型について、コード値の文字列をenumへ変換するConverter。
	 *
	 * @param <T> 変換先の区分値enum型
	 */
	@RequiredArgsConstructor
	private static final class StringToCodeEnum<T extends CodeEnum<?>> implements Converter<String, T> {
		/** 変換先の区分値enum型。 */
		private final Class<T> targetType;

		/**
		 * コード値の文字列をenumへ変換する。
		 * <p>
		 * 未入力は「未指定」を表すため {@code null} を返す。Spring標準のenum変換と同じ扱いで、
		 * 未指定時の既定値はService層が決める。
		 * <p>
		 * 大文字小文字は無視する。enum化前の {@code Strings.CI.equals} による判定を維持するため。
		 *
		 * @param source フォームやquery parameterで受け取ったコード値
		 * @return コード値に対応するenum。未入力の場合は {@code null}
		 * @throws IllegalArgumentException コード値に対応するenumが存在しない場合
		 */
		@Override
		public T convert(String source) {
			if (StringUtils.isBlank(source)) {
				return null;
			}
			T[] constants = targetType.getEnumConstants();
			if (ArrayUtils.isEmpty(constants)) {
				throw new IllegalArgumentException("区分値enumではないため変換できません。type=" + targetType.getName());
			}
			String key = StringUtils.trim(source);
			return Arrays.stream(constants)
					.filter(constant -> Strings.CI.equals(String.valueOf(constant.getKey()), key)).findFirst()
					.orElseThrow(() -> new IllegalArgumentException(CodeEnum.describeInvalidKey(targetType)));
		}
	}
}
