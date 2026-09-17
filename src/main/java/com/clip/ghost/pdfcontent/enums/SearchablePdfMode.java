package com.clip.ghost.pdfcontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * 検索可能PDF生成（OCRサンドイッチPDF）で、OCR対象にするページを決める変換モードを表すenum。
 * <p>
 * {@code PdfMarkdownDraftMode}（Markdown下書きの{@code AUTO}/{@code VISION}）と同じ二値の考え方だが、
 * 対象が「Markdown下書き」ではなく「PDF自体の書き換え」であり契約を混ぜないため別のenumにする。
 */
@AllArgsConstructor
public enum SearchablePdfMode implements CodeEnum<String> {
	/** 文字レイヤーが無い/薄いページだけをOCR対象にする。既存の文字レイヤーは変更しない。 */
	AUTO("AUTO", "文字レイヤーが無いページだけをOCR対象にします", false),

	/** 文字レイヤーの有無に関係なく全ページをOCR対象にする。既存の文字レイヤーが化けている場合の救済用。 */
	FORCE_OCR("FORCE_OCR", "文字レイヤーの有無に関係なく全ページをOCR対象にします", true);

	private static final String INVALID_KEY_MESSAGE = "modeはAUTOまたはFORCE_OCRで指定してください。";
	private static final String CONVERSION_TARGET_FORMAT = "%sは%s";

	/** APIやフォームで扱う変換モードコード。 */
	private final String key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** 文字レイヤーの有無に関係なく全ページを対象にする場合true。 */
	private final boolean everyPage;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<String, SearchablePdfMode> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(SearchablePdfMode::getKey, mode -> mode));

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
	 * 文字レイヤーの有無に関係なく全ページをOCR対象にするか判定する。
	 * <p>
	 * OCR対象ページの決め方を呼び出し側のif文へ書かず、モード自身に持たせる。対象ページ数はそのまま
	 * OCR呼び出し回数（コストガードの対象）になるため、判定が複数箇所へ散ると上限チェックと
	 * 実際の処理対象がズレる。
	 *
	 * @return 全ページが対象の場合true
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
	 * 何を対象として数えるモードなのかを説明する文言を取得する。
	 * <p>
	 * ページ上限を超えた場合のエラーメッセージへ添える。同じページ数でもAUTOなら通りFORCE_OCRなら通らないため、
	 * 対象ページ数と上限だけでは利用者が理由を判断できない。
	 *
	 * @return 対象の説明
	 */
	public String describeConversionTarget() {
		return CONVERSION_TARGET_FORMAT.formatted(key, value);
	}

	/**
	 * キー値から変換モードを取得する。
	 *
	 * @param key 変換モードコード
	 * @return キー値に対応する変換モード
	 * @throws IllegalArgumentException 対応する変換モードが存在しない場合
	 */
	@JsonCreator
	public static SearchablePdfMode fromKey(String key) {
		return KEY_MAP.values().stream().filter(mode -> Strings.CI.equals(mode.getKey(), key)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(INVALID_KEY_MESSAGE));
	}
}
