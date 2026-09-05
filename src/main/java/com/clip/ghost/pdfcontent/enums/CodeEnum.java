package com.clip.ghost.pdfcontent.enums;

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
}
