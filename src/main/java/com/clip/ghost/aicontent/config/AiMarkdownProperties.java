package com.clip.ghost.aicontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文AI整形・要約の入力サイズに関する設定。
 * <p>
 * 外部AIの課金が発生する前に処理を止めるコストガードとして、入力文字数の上限を持つ。
 * 既定値は日本語混じりの文書を想定し、Claude Opus 5 / GPT-4oのコンテキスト長から逆算した安全側の値にする。
 * 実測値は {@code docs/markdown-ai-transform-design.md} に記録する。
 */
@ConfigurationProperties(prefix = "ghost.ai.markdown")
@Getter
@Setter
public class AiMarkdownProperties {
	/** Markdown本文AI変換の入力文字数上限。超過時は変換器を呼ばずに400を返す。 */
	private int maxInputCharacters = 60_000;
}
