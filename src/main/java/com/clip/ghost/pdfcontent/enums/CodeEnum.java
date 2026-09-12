package com.clip.ghost.pdfcontent.enums;

import org.apache.commons.lang3.ArrayUtils;

/**
 * APIや画面で扱うコード値と、説明用ラベルを持つenumの共通インターフェース。
 *
 * @param <T> コード値の型
 */
public interface CodeEnum<T> {
	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return コード値
	 */
	T getKey();

	/**
	 * 画面表示やログ説明に使うラベルを取得する。
	 *
	 * @return 表示ラベル
	 */
	String getValue();

	/**
	 * コード値が不正な場合に利用者へ返す説明を取得する。
	 * <p>
	 * 区分値の値域を知っているのはenum自身なので、説明もenumに持たせる。
	 * リクエストの型変換に失敗した場合、Springが組み立てるメッセージは英語の内部表現になるため、
	 * 画面へそのまま出さずにこの説明へ差し替える。
	 *
	 * @return 不正なコード値を指定された場合の説明
	 */
	String getInvalidKeyMessage();

	/**
	 * enumの型から、コード値が不正な場合の説明を取得する。
	 * <p>
	 * 型変換に失敗した時点ではインスタンスが無いため、定数の1つから説明だけを取り出す。
	 * 区分値enum以外の型を渡された場合は説明を持たないため {@code null} を返す。
	 *
	 * @param codeEnumType 判定対象の型
	 * @return 不正なコード値を指定された場合の説明。区分値enumでない場合は {@code null}
	 */
	static String describeInvalidKey(Class<?> codeEnumType) {
		if (codeEnumType == null || !CodeEnum.class.isAssignableFrom(codeEnumType)) {
			return null;
		}
		Object[] constants = codeEnumType.getEnumConstants();
		return ArrayUtils.isEmpty(constants) ? null : ((CodeEnum<?>) constants[0]).getInvalidKeyMessage();
	}
}
