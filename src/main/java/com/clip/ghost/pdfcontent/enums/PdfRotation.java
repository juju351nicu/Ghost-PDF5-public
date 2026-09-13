package com.clip.ghost.pdfcontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * PDFページの回転角を表すenum。
 * <p>
 * PDFの仕様上、ページの回転角は90の倍数しか持てない。半端な角度を受け取っても表現できないため、
 * 値域を90 / 180 / 270の3つに絞り、{@code 0}（回転なし）は「回転APIを呼ばない」で表す。
 * {@link PdfInsertOption} と同じ {@link CodeEnum} の形にそろえる。
 */
@AllArgsConstructor
public enum PdfRotation implements CodeEnum<Integer> {
	/** 時計回りに90度回転する。 */
	CLOCKWISE_90(90, "時計回りに90度"),

	/** 180度回転する。 */
	UPSIDE_DOWN_180(180, "180度"),

	/** 反時計回りに90度（時計回りに270度）回転する。 */
	COUNTER_CLOCKWISE_270(270, "反時計回りに90度");

	private static final String INVALID_KEY_MESSAGE = "回転角は90、180、270のいずれかで入力してください。";

	/** 回転角を正規化する際の1周分の角度。 */
	private static final int FULL_TURN_DEGREES = 360;

	/** APIやフォームで扱う回転角コード。 */
	private final Integer key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<Integer, PdfRotation> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(PdfRotation::getKey, rotation -> rotation));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return 回転角コード
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
	 * コード値が不正な場合に利用者へ返す説明を取得する。
	 *
	 * @return 回転角コードが不正な場合の説明
	 */
	@Override
	public String getInvalidKeyMessage() {
		return INVALID_KEY_MESSAGE;
	}

	/**
	 * 現在の回転角へこの回転を加え、0以上360未満へ正規化した回転角を返す。
	 * <p>
	 * 回転は絶対角の指定ではなく現在角からの相対回転にする。利用者が見ているのは回転後の見た目であり、
	 * PDF内部の {@code /Rotate} 値ではないため、絶対角で受けると「90を2回指定しても180にならない」ことになる。
	 * <p>
	 * PDFBoxの {@code getRotation} は負の値や360以上を返し得るため、加算結果をそのまま設定しない。
	 *
	 * @param currentRotation 現在のページ回転角
	 * @return 正規化した回転後の角度
	 */
	public int applyTo(int currentRotation) {
		int rotated = (currentRotation + key) % FULL_TURN_DEGREES;
		return rotated < 0 ? rotated + FULL_TURN_DEGREES : rotated;
	}

	/**
	 * キー値から回転角を取得する。
	 *
	 * @param key 回転角コード
	 * @return キー値に対応する回転角
	 * @throws IllegalArgumentException 対応する回転角が存在しない場合
	 */
	@JsonCreator
	public static PdfRotation fromKey(Integer key) {
		if (Objects.isNull(key)) {
			throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
		}
		PdfRotation rotation = KEY_MAP.get(key);
		if (Objects.isNull(rotation)) {
			throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
		}
		return rotation;
	}
}
