package com.clip.ghost.common.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * Markdown本文・抽出テキストの改行をAPI契約に合わせて正規化するユーティリティ。
 * <p>
 * CRLFとCRをLFへ統一し、末尾の空白文字だけを除去する。本文途中の空白は残す。Markdownでは
 * 行末の半角空白2つが改行を表すなど、途中の空白そのものが意味を持つため。
 * <p>
 * PDF・画像・AI変換のいずれも、レスポンスへ載せる直前に同じ正規化を通す。取り込み元ごとに
 * 書き写すと、同じ本文でも取得経路によって末尾の空行や改行コードが変わる。
 */
public final class MarkdownTextNormalizer {
	private static final String CRLF = "\r\n";
	private static final String LF = "\n";
	private static final char CR = '\r';
	private static final char LF_CHAR = '\n';

	private MarkdownTextNormalizer() {
	}

	/**
	 * 改行をLFへ統一し、末尾の空白文字を除去する。
	 *
	 * @param text 正規化対象のテキスト
	 * @return 正規化済みテキスト。入力がnullまたは空文字の場合は入力をそのまま返す
	 */
	public static String normalize(String text) {
		if (StringUtils.isEmpty(text)) {
			return text;
		}
		return text.replace(CRLF, LF).replace(CR, LF_CHAR).stripTrailing();
	}
}
