package com.clip.ghost.webcontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.clip.ghost.pdfcontent.enums.CodeEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * Webページ取り込みの出力モードを表すenum。
 * <p>
 * 出力の性質が変わるだけで、取得と解析は同じ。エンドポイントを増やさず {@code mode} で切り替えるのは、
 * 既存の {@code POST /markdownDraftPdf} が {@code mode=AUTO|VISION} で同じことをしているため。
 * <p>
 * 未指定時の既定（{@link #ARTICLE}）はenumの値として持たず、Service側で決める。
 * 「未指定」という値をenumへ足すと、APIが受け取れる文字列が増えてリクエスト契約が変わってしまう。
 */
@AllArgsConstructor
public enum WebMarkdownDraftMode implements CodeEnum<String> {
	/** 本文だけを出す。未指定時の既定。 */
	ARTICLE("ARTICLE", "本文のMarkdownだけを出力します", true, false),

	/** 構造レポートだけを出す。 */
	STRUCTURE("STRUCTURE", "ページ構造のレポートだけを出力します", false, true),

	/** 構造レポートと本文の両方を出す。 */
	BOTH("BOTH", "ページ構造のレポートと本文の両方を出力します", true, true);

	private static final String INVALID_KEY_MESSAGE = "出力モードはARTICLE、STRUCTURE、BOTHのいずれかで指定してください。";

	/** APIやフォームで扱う出力モードコード。 */
	private final String key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** 本文を出力する場合true。 */
	private final boolean article;

	/** 構造レポートを出力する場合true。 */
	private final boolean structure;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<String, WebMarkdownDraftMode> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(WebMarkdownDraftMode::getKey, mode -> mode));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return 出力モードコード
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
	 * 本文を出力するモードかを判定する。
	 * <p>
	 * 何を出すかの判定を呼び出し側のif文へ散らさず、モード自身に持たせる。
	 *
	 * @return 本文を出力する場合true
	 */
	public boolean outputsArticle() {
		return article;
	}

	/**
	 * 構造レポートを出力するモードかを判定する。
	 *
	 * @return 構造レポートを出力する場合true
	 */
	public boolean outputsStructure() {
		return structure;
	}

	/**
	 * コード値が不正な場合に利用者へ返す説明を取得する。
	 *
	 * @return 出力モードが不正な場合の説明
	 */
	@Override
	public String getInvalidKeyMessage() {
		return INVALID_KEY_MESSAGE;
	}

	/**
	 * キー値から出力モードを取得する。
	 * <p>
	 * 大文字小文字は無視する。既存の {@code PdfMarkdownDraftMode} が {@code mode=vision} を
	 * 受け付けるため、同じAPI群で扱いを変えない。
	 *
	 * @param key 出力モードコード
	 * @return キー値に対応する出力モード
	 * @throws IllegalArgumentException 対応する出力モードが存在しない場合
	 */
	@JsonCreator
	public static WebMarkdownDraftMode fromKey(String key) {
		return KEY_MAP.values().stream().filter(mode -> Strings.CI.equals(mode.getKey(), key)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(INVALID_KEY_MESSAGE));
	}
}
