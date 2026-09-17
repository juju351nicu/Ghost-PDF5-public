package com.clip.ghost.aicontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Markdown本文AI整形・要約の入力サイズに関する設定。
 * <p>
 * 外部AIの課金が発生する前に処理を止めるコストガードとして、入力文字数の上限を持つ。
 * <p>
 * この値は{@code ghost.ai.anthropic.max-output-tokens} / {@code ghost.ai.openai.max-output-tokens}
 * （既定16,000トークン）から独立に決めていない。REFINEは出力が入力とほぼ同じ長さになり得るため、
 * 入力上限を通った本文の出力が、必ず出力トークン上限に収まる値にする必要がある。実測（日本語混じりの
 * Markdownで出力側は約0.43トークン/文字）に安全側の余裕を見込み、24,000文字を既定にした。
 * OpenAI（{@code gpt-4o}の完了トークン上限は実質16,384）とAnthropicの両方を想定し、より厳しい方に合わせる。
 * 詳細・実測値は {@code docs/markdown-ai-transform-design.md} に記録する。この見積もりを超えて実際に
 * 出力が打ち切られた場合も、{@code MarkdownAiConverter}実装が打ち切りを検出して失敗として扱うため、
 * 途中で切れたMarkdownが正常応答として利用者に返ることはない。
 */
@ConfigurationProperties(prefix = "ghost.ai.markdown")
@Getter
@Setter
public class AiMarkdownProperties {
	/** Markdown本文AI変換の入力文字数上限。超過時は変換器を呼ばずに400を返す。 */
	private int maxInputCharacters = 24_000;
}
