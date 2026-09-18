package com.clip.ghost.webcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link WebMarkdownBuilder} のMarkdown組み立て規則を検証するテスト。
 * <p>
 * 取得日時は固定値を渡す。実時刻を使うと出典ヘッダーの期待値が書けない。
 */
class WebMarkdownBuilderTest {
	private static final String BASE_URI = "https://example.test/docs/";
	private static final String SOURCE = "page.html";
	private static final LocalDateTime RETRIEVED_AT = LocalDateTime.of(2026, 9, 18, 12, 34, 56);
	private static final String TITLE = "設計メモ";
	private static final String DESCRIPTION = "説明文";

	private final WebMarkdownBuilder webMarkdownBuilder = new WebMarkdownBuilder();

	@Test
	@DisplayName("冒頭に出典ヘッダー（タイトル・取得元・取得日時・説明）を付ける")
	void buildAddsSourceHeader() {
		String markdown = build("<p>本文</p>", TITLE, DESCRIPTION);

		assertEquals("""
				# 設計メモ

				- 取得元: page.html
				- 取得日時: 2026-09-18 12:34:56
				- 説明: 説明文

				本文""", markdown);
	}

	@Test
	@DisplayName("説明が無い場合は説明行を出さない")
	void buildOmitsDescriptionLineWhenDescriptionIsBlank() {
		String markdown = build("<p>本文</p>", TITLE, StringUtils.EMPTY);

		assertFalse(Strings.CS.contains(markdown, "- 説明:"));
		assertTrue(Strings.CS.contains(markdown, "- 取得元: page.html"));
	}

	@Test
	@DisplayName("タイトルが取れない場合は取得元を見出しにする")
	void buildUsesSourceAsHeadingWhenTitleIsBlank() {
		String markdown = build("<p>本文</p>", StringUtils.EMPTY, StringUtils.EMPTY);

		assertTrue(Strings.CS.startsWith(markdown, "# page.html"));
	}

	@ParameterizedTest
	@DisplayName("h1〜h6を同じ深さのMarkdown見出しへ写す")
	@CsvSource({ "h1, '# 見出し'", "h2, '## 見出し'", "h3, '### 見出し'", "h4, '#### 見出し'", "h5, '##### 見出し'",
			"h6, '###### 見出し'" })
	void buildConvertsHeadingLevels(String tagName, String expected) {
		String markdown = build("<" + tagName + ">見出し</" + tagName + ">", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, expected));
	}

	@Test
	@DisplayName("段落中の相対リンクを絶対URLのMarkdownリンクにする")
	void buildConvertsRelativeLinkToAbsoluteUrl() {
		String markdown = build("<p>前<a href=\"guide.html\">リンク</a>後</p>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "前[リンク](https://example.test/docs/guide.html)後"));
	}

	@Test
	@DisplayName("強調と斜体をMarkdownの記号へ写す")
	void buildConvertsEmphasis() {
		String markdown = build("<p><strong>太字</strong>と<em>斜体</em></p>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "**太字**と*斜体*"));
	}

	@Test
	@DisplayName("入れ子の箇条書きを2桁インデントで表す")
	void buildConvertsNestedUnorderedList() {
		String markdown = build("<ul><li>親<ul><li>子</li></ul></li><li>次</li></ul>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "- 親\n  - 子\n- 次"));
	}

	@Test
	@DisplayName("番号付きリストを連番のMarkdownにする")
	void buildConvertsOrderedList() {
		String markdown = build("<ol><li>最初</li><li>次</li></ol>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "1. 最初\n2. 次"));
	}

	@Test
	@DisplayName("表をGFMの表へ写し、1行目を見出し行にする")
	void buildConvertsTable() {
		String html = "<table><tr><th>項目</th><th>内容</th></tr><tr><td>形式</td><td>Markdown</td></tr></table>";

		String markdown = build(html, TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "| 項目 | 内容 |\n| --- | --- |\n| 形式 | Markdown |"));
	}

	@Test
	@DisplayName("preをフェンス付きコードブロックにし、class属性から言語を引き継ぐ")
	void buildConvertsCodeBlockWithLanguage() {
		String markdown = build("<pre><code class=\"language-java\">int value = 1;</code></pre>", TITLE,
				StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "```java\nint value = 1;\n```"));
	}

	@Test
	@DisplayName("引用は中の段落ごと引用記号を付けて残す")
	void buildConvertsBlockQuote() {
		String markdown = build("<blockquote><p>引用した一文。</p></blockquote>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "> 引用した一文。"));
	}

	@Test
	@DisplayName("hrを水平線のMarkdownにする")
	void buildConvertsHorizontalRule() {
		String markdown = build("<p>前</p><hr><p>後</p>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "前\n\n---\n\n後"));
	}

	@Test
	@DisplayName("画像は絶対URLの参照だけを残し、取得はしない")
	void buildKeepsImageReferenceOnly() {
		String markdown = build("<p><img src=\"images/figure.png\" alt=\"図1\"></p>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "![図1](https://example.test/docs/images/figure.png)"));
	}

	@Test
	@DisplayName("divしか使っていないHTMLでも本文が落ちない")
	void buildKeepsTextInsideNestedDivs() {
		String markdown = build("<div><div>divの中の文</div></div>", TITLE, StringUtils.EMPTY);

		assertTrue(Strings.CS.contains(markdown, "divの中の文"));
	}

	/**
	 * body断片からMarkdownを組み立てる。
	 *
	 * @param bodyHtml    body内のHTML断片
	 * @param title       ページタイトル
	 * @param description ページの説明
	 * @return 組み立てたMarkdown
	 */
	private String build(String bodyHtml, String title, String description) {
		Document document = Jsoup.parse("<html><body>" + bodyHtml + "</body></html>", BASE_URI);
		WebPageContent content = new WebPageContent(title, description, document.body());
		return webMarkdownBuilder.build(content, SOURCE, RETRIEVED_AT);
	}
}
