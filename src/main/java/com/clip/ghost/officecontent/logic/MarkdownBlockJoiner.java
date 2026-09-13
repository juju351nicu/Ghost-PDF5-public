package com.clip.ghost.officecontent.logic;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Markdownのブロック（見出し・段落・表）を連結するユーティリティ。
 * <p>
 * ブロックの間は必ず空行で区切る。Markdownは空行が無いと直前の段落の続きとして解釈するため、
 * 区切りを詰めると見出しや表が本文へ吸収されて描画されなくなる。
 */
final class MarkdownBlockJoiner {
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
	static String join(List<String> blocks) {
		return CollectionUtils.emptyIfNull(blocks).stream().filter(StringUtils::isNotEmpty)
				.reduce((left, right) -> left + BLOCK_SEPARATOR + right).orElse(StringUtils.EMPTY);
	}
}
