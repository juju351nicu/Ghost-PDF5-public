package com.clip.ghost.pdfcontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * PDF差し込み方法を表すenum。
 * <p>
 * フロントエンド/既存APIでは {@code 1}、{@code 2}、{@code 3} の数値を使い、
 * Javaコード上ではこのenumで扱う。リクエスト仕様を変えずに、分岐条件の意味を明確化する。
 */
@AllArgsConstructor
public enum PdfInsertOption implements CodeEnum<Integer> {
	/** 対象ページの後に差し込む。 */
	INSERT(PdfConstants.OPTION_INSERT, "対象ページの後に差し込む"),

	/** 対象ページを差し込みPDFで差し替える。 */
	REPLACE(PdfConstants.OPTION_REPLACE, "対象ページと差し替える"),

	/** 編集元PDFの最後のページに差し込む。 */
	LAST_INSERT(PdfConstants.OPTION_LAST_INSERT, "最後のページに差し込む");

	private static final String INVALID_KEY_MESSAGE = "PDF差し込み方法は1、2、3のいずれかで入力してください。";

	/** APIやフォームで扱う差し込み方法コード。 */
	private final Integer key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<Integer, PdfInsertOption> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(PdfInsertOption::getKey, insertOption -> insertOption));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return 差し込み方法コード
	 */
	@JsonValue
	@Override
	public Integer getKey() {
		return key;
	}

	/**
	 * 画面表示やログ説明に使うラベルを取得する。
	 *
	 * @return 表示ラベル
	 */
	@Override
	public String getValue() {
		return value;
	}

	/**
	 * キー値からPDF差し込み方法を取得する。
	 *
	 * @param key 差し込み方法コード
	 * @return キー値に対応するPDF差し込み方法
	 * @throws IllegalArgumentException 対応するPDF差し込み方法が存在しない場合
	 */
	@JsonCreator
	public static PdfInsertOption fromKey(Integer key) {
		if (Objects.isNull(key)) {
			throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
		}
		PdfInsertOption insertOption = KEY_MAP.get(key);
		if (Objects.isNull(insertOption)) {
			throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
		}
		return insertOption;
	}
}
