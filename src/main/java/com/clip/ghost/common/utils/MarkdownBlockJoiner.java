package com.clip.ghost.common.utils;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Markdownのブロック（見出し・段落・表）を連結するユーティリティ。
 * <p>
 * ブロックの間は必ず空行で区切る。Markdownは空行が無いと直前の段落の続きとして解釈するため、
 * 区切りを詰めると見出しや表が本文へ吸収されて描画されなくなる。
 * <p>
 * Webページ取り込み（{@code webcontent}）でも同じ連結規則を使うため、Office専用の
 * {@code officecontent.logic} から {@code common.utils} へ移した。
 */
public final class MarkdownBlockJoiner {
	private static final String BLOCK_SEPARATOR = "\n\n";

	/**
	 * インスタンス化を禁止する。
	 */
	private MarkdownBlockJoiner() {
	}

	/**
	 * Markdownブロックを空行区切りで連結する。
	 *
	 * @param blocks 連結するブロック
	 * @return 連結したMarkdown本文
	 */
	public static String join(List<String> blocks) {
		return CollectionUtils.emptyIfNull(blocks).stream().filter(StringUtils::isNotEmpty)
				.reduce((left, right) -> left + BLOCK_SEPARATOR + right).orElse(StringUtils.EMPTY);
	}
}
