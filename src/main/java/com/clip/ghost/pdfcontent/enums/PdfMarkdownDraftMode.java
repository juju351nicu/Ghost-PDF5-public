package com.clip.ghost.pdfcontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * ページ単位Markdown下書きの変換モードを表すenum。
 * <p>
 * APIが受け取る文字列（{@code AUTO} / {@code VISION}）は従来どおりで、Javaコード上の分岐だけこのenumへ寄せる。
 * {@code PdfInsertOption} と同じ {@link CodeEnum} の形にそろえる。
 * <p>
 * modeを省略した場合の「文字レイヤーだけを使う従来動作」はenumの値として持たせない。値を足すと
 * APIが受け取れる文字列が増え、リクエスト契約が変わってしまうため。未指定の扱いはService側で決める。
 */
@AllArgsConstructor
public enum PdfMarkdownDraftMode implements CodeEnum<String> {
	/** 文字レイヤーを取得できないページだけを画像変換へ回す。 */
	AUTO("AUTO", "文字レイヤーが無いページだけを変換対象にします", false),

	/** 文字レイヤーの有無に関係なく全ページを画像変換へ回す。 */
	VISION("VISION", "文字レイヤーの有無に関係なく全ページを変換対象にします", true);

	private static final String INVALID_KEY_MESSAGE = "変換モードはAUTOまたはVISIONで指定してください。";
	private static final String CONVERSION_TARGET_FORMAT = "%sは%s";

	/** APIやフォームで扱う変換モードコード。 */
	private final String key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** 文字レイヤーの有無に関係なく全ページを変換対象にする場合true。 */
	private final boolean everyPage;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<String, PdfMarkdownDraftMode> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(PdfMarkdownDraftMode::getKey, mode -> mode));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return 変換モードコード
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
	 * 文字レイヤーの有無に関係なく全ページを変換対象にするか判定する。
	 * <p>
	 * 変換対象ページの決め方を呼び出し側のif文へ書かず、モード自身に持たせる。対象ページ数はそのまま
	 * 外部AIの課金額になるため、判定が複数箇所へ散ると上限チェックと実際の課金対象がズレる。
	 *
	 * @return 全ページが変換対象の場合true
	 */
	public boolean convertsEveryPage() {
		return everyPage;
	}

	/**
	 * コード値が不正な場合に利用者へ返す説明を取得する。
	 *
	 * @return 変換モードが不正な場合の説明
	 */
	@Override
	public String getInvalidKeyMessage() {
		return INVALID_KEY_MESSAGE;
	}

	/**
	 * 何を変換対象として数えるモードなのかを説明する文言を取得する。
	 * <p>
	 * ページ上限を超えた場合のエラーメッセージへ添える。同じページ数でもAUTOなら通りVISIONなら通らないため、
	 * 対象ページ数と上限だけでは利用者が理由を判断できない。
	 *
	 * @return 変換対象の説明
	 */
	public String describeConversionTarget() {
		return CONVERSION_TARGET_FORMAT.formatted(key, value);
	}

	/**
	 * キー値から変換モードを取得する。
	 * <p>
	 * 大文字小文字を無視する判定は、enum化前の {@code Strings.CI.equals} による判定をそのまま維持したもの。
	 * {@code mode=vision} のような小文字指定は従来どおり受け付ける。
	 *
	 * @param key 変換モードコード
	 * @return キー値に対応する変換モード
	 * @throws IllegalArgumentException 対応する変換モードが存在しない場合
	 */
	@JsonCreator
	public static PdfMarkdownDraftMode fromKey(String key) {
		return KEY_MAP.values().stream().filter(mode -> Strings.CI.equals(mode.getKey(), key)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(INVALID_KEY_MESSAGE));
	}
}
