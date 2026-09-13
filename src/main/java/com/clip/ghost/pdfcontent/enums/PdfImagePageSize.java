package com.clip.ghost.pdfcontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * 画像からPDFを作るときのページサイズの決め方を表すenum。
 * <p>
 * 「用紙に合わせる」と「画像に合わせる」は要求が正反対になる。印刷したい人はA4に揃えたく、
 * 図面やスクリーンショットを残したい人は余白も再サンプリングも入れたくない。どちらかに決め打ちせず選ばせる。
 */
@AllArgsConstructor
public enum PdfImagePageSize implements CodeEnum<String> {
	/** A4に収める。画像の縦横比は保ち、余った部分は余白になる。縦長・横長は画像の向きに合わせる。 */
	A4("A4", "A4に収める（縦横比は保持）"),

	/** 画像の画素寸法をそのままページサイズにする。余白も拡大縮小も発生しない。 */
	FIT("FIT", "画像サイズに合わせる（余白なし）");

	private static final String INVALID_KEY_MESSAGE = "ページサイズはA4またはFITで指定してください。";

	/** APIやフォームで扱うページサイズコード。 */
	private final String key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<String, PdfImagePageSize> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(PdfImagePageSize::getKey, pageSize -> pageSize));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return ページサイズコード
	 */
	@JsonValue
	@Override
	public String getKey() {
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
	 * @return ページサイズコードが不正な場合の説明
	 */
	@Override
	public String getInvalidKeyMessage() {
		return INVALID_KEY_MESSAGE;
	}

	/**
	 * ページサイズを画像の画素寸法に合わせるか判定する。
	 *
	 * @return 画像サイズをそのままページサイズにする場合true
	 */
	public boolean fitsPageToImage() {
		return this == FIT;
	}

	/**
	 * キー値からページサイズを取得する。
	 * <p>
	 * 大文字小文字は無視する。{@code pageSize=a4} のような小文字指定を受け付けるため。
	 *
	 * @param key ページサイズコード
	 * @return キー値に対応するページサイズ
	 * @throws IllegalArgumentException 対応するページサイズが存在しない場合
	 */
	@JsonCreator
	public static PdfImagePageSize fromKey(String key) {
		return KEY_MAP.values().stream().filter(pageSize -> Strings.CI.equals(pageSize.getKey(), key)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(INVALID_KEY_MESSAGE));
	}
}
