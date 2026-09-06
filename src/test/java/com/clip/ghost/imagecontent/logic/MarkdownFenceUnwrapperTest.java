package com.clip.ghost.imagecontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MarkdownFenceUnwrapper} の外側フェンス除去を検証するテスト。
 */
class MarkdownFenceUnwrapperTest {

	@Test
	@DisplayName("出力全体を包む ```markdown を外す")
	void unwrapsMarkdownFence() {
		String input = "```markdown\n## 見出し\n\nデバイスとドライブ\n```";

		assertEquals("## 見出し\n\nデバイスとドライブ", MarkdownFenceUnwrapper.unwrap(input));
	}

	@Test
	@DisplayName("出力全体を包む ```md を外す")
	void unwrapsMdFence() {
		String input = "```md\n本文\n```";

		assertEquals("本文", MarkdownFenceUnwrapper.unwrap(input));
	}

	@Test
	@DisplayName("言語指定なしのフェンスで内側にフェンスが無ければ外す")
	void unwrapsBareFenceWithoutInnerFence() {
		String input = "```\n本文\n```";

		assertEquals("本文", MarkdownFenceUnwrapper.unwrap(input));
	}

	@Test
	@DisplayName("```java で始まるコードのフェンスは残す")
	void keepsLanguageTaggedCodeFence() {
		String input = "```java\npublic class A {}\n```";

		assertEquals(input, MarkdownFenceUnwrapper.unwrap(input));
	}

	@Test
	@DisplayName("```markdown の包みは内側にフェンスがあっても外側だけ外す")
	void unwrapsMarkdownWrapperKeepingInnerFence() {
		String input = "```markdown\n## 手順\n\n```java\nvar x = 1;\n```\n```";

		assertEquals("## 手順\n\n```java\nvar x = 1;\n```", MarkdownFenceUnwrapper.unwrap(input));
	}

	@Test
	@DisplayName("言語指定なしで内側にもフェンスがある場合は触らない")
	void keepsBareFenceWithInnerFence() {
		String input = "```\n## 手順\n\n```java\nvar x = 1;\n```\n```";

		assertEquals(input, MarkdownFenceUnwrapper.unwrap(input));
	}

	@Test
	@DisplayName("フェンスで包まれていない通常出力は変えない")
	void keepsPlainOutput() {
		String input = "## 見出し\n\n本文";

		assertEquals(input, MarkdownFenceUnwrapper.unwrap(input));
	}

	@Test
	@DisplayName("空や空白は変えない")
	void keepsBlank() {
		assertEquals("", MarkdownFenceUnwrapper.unwrap(""));
		assertEquals("   ", MarkdownFenceUnwrapper.unwrap("   "));
	}
}
