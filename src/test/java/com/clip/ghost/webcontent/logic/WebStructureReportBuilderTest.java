package com.clip.ghost.webcontent.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link WebStructureReportBuilder} の構造レポートを検証するテスト。
 * <p>
 * フィクスチャHTMLはすべてこのテスト内で組み立てた自作で、実在サイトのHTMLは持ち込まない。
 */
class WebStructureReportBuilderTest {
	private static final String BASE_URI = "https://example.test/docs/";

	private final WebStructureReportBuilder webStructureReportBuilder = new WebStructureReportBuilder();

	@Test
	@DisplayName("文書メタにtitle・lang・canonical・description・OGPを並べる")
	void buildListsDocumentMeta() {
		String html = """
				<html lang="ja"><head><title>設計メモ</title>
				<meta name="description" content="説明文">
				<meta name="robots" content="index,follow">
				<meta property="og:title" content="共有タイトル">
				<link rel="canonical" href="https://example.test/docs/page">
				</head><body><main><h1>見出し</h1></main></body></html>""";

		String report = build(html);

		assertTrue(Strings.CS.contains(report, "- title: 設計メモ"));
		assertTrue(Strings.CS.contains(report, "- lang: ja"));
		assertTrue(Strings.CS.contains(report, "- canonical: https://example.test/docs/page"));
		assertTrue(Strings.CS.contains(report, "- meta description: 説明文"));
		assertTrue(Strings.CS.contains(report, "- meta robots: index,follow"));
		assertTrue(Strings.CS.contains(report, "- og:title: 共有タイトル"));
	}

	@Test
	@DisplayName("指定が無いメタは「（指定なし）」として事実のまま出す")
	void buildMarksMissingMetaAsNotSet() {
		String report = build("<html><head><title>設計メモ</title></head><body><p>本文</p></body></html>");

		assertTrue(Strings.CS.contains(report, "- meta description: （指定なし）"));
		assertTrue(Strings.CS.contains(report, "- og:image: （指定なし）"));
	}

	@Test
	@DisplayName("見出しをレベルどおりのインデントでアウトラインにする")
	void buildBuildsHeadingOutline() {
		String html = "<body><h1>大見出し</h1><h2>中見出し</h2><h3>小見出し</h3></body>";

		String report = build(html);

		assertTrue(Strings.CS.contains(report, "- h1 大見出し\n  - h2 中見出し\n    - h3 小見出し"));
	}

	@Test
	@DisplayName("見出しレベルが飛んでいる箇所に注記を付ける")
	void buildAnnotatesSkippedHeadingLevel() {
		String html = "<body><h2>中見出し</h2><h4>飛んだ見出し</h4></body>";

		String report = build(html);

		assertTrue(Strings.CS.contains(report, "h2 から h4 へ見出しレベルが飛んでいます"));
	}

	@Test
	@DisplayName("見出しが無い場合はその事実を書く")
	void buildStatesWhenNoHeadingExists() {
		String report = build("<body><p>本文だけのページ</p></body>");

		assertTrue(Strings.CS.contains(report, "見出し要素がありません。"));
	}

	@Test
	@DisplayName("ランドマーク要素を入れ子のまま並べ、見分けが付く手がかりを添える")
	void buildBuildsLandmarkTree() {
		String html = """
				<body><header><nav aria-label="グローバル">…</nav></header>
				<main><article><section>…</section></article></main>
				<footer>…</footer></body>""";

		String report = build(html);

		assertTrue(Strings.CS.contains(report, "- header"));
		assertTrue(Strings.CS.contains(report, "  - nav … グローバル"));
		assertTrue(Strings.CS.contains(report, "- main"));
		assertTrue(Strings.CS.contains(report, "  - article"));
		assertTrue(Strings.CS.contains(report, "    - section"));
		assertTrue(Strings.CS.contains(report, "- footer"));
	}

	@Test
	@DisplayName("ランドマークでない入れ物（div）では階層を深くしない")
	void buildDoesNotDeepenForNonLandmarkContainers() {
		String html = "<body><div><div><main><p>本文</p></main></div></div></body>";

		String report = build(html);

		assertTrue(Strings.CS.contains(report, "- main"));
		assertFalse(Strings.CS.contains(report, "  - main"));
	}

	@Test
	@DisplayName("同じランドマークが続く場合は件数へまとめる")
	void buildCollapsesRepeatedLandmarks() {
		String html = "<body><main><section>1</section><section>2</section><section>3</section></main></body>";

		String report = build(html);

		assertTrue(Strings.CS.contains(report, "  - section（同じ並びが 3 件）"));
	}

	@Test
	@DisplayName("入れ子を持つランドマークは件数へまとめず構成を残す")
	void buildKeepsStructureForNestedLandmarks() {
		String html = "<body><main><section><nav>…</nav></section><section>2</section></main></body>";

		String report = build(html);

		assertTrue(Strings.CS.contains(report, "  - section\n    - nav"));
		assertFalse(Strings.CS.contains(report, "同じ並びが 2 件"));
	}

	@Test
	@DisplayName("ランドマーク要素が無い場合はその事実を書く")
	void buildStatesWhenNoLandmarkExists() {
		String report = build("<body><div><p>本文だけのページ</p></div></body>");

		assertTrue(Strings.CS.contains(report, "ランドマーク要素"));
		assertTrue(Strings.CS.contains(report, "ありません。"));
	}

	@Test
	@DisplayName("見分けの手がかりが長すぎる場合は切り詰めて1行を読める長さに保つ")
	void buildShortensLongLandmarkLabel() {
		String longClass = "flex items-center justify-between gap-4 px-6 py-3 text-sm font-medium text-slate-700 "
				+ "hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2";
		String html = "<body><main class=\"" + longClass + "\"><p>本文</p></main></body>";

		String report = build(html);

		// ユーティリティクラス主体のサイトでは、classがそのままだと1行が数百文字になり構成が読めない。
		String landmarkLine = report.lines().filter(line -> Strings.CS.contains(line, "- main")).findFirst()
				.orElse(StringUtils.EMPTY);
		assertTrue(landmarkLine.length() <= 80, () -> "1行が長すぎます: " + landmarkLine.length());
		assertTrue(Strings.CS.contains(landmarkLine, "…"));
	}

	@Test
	@DisplayName("h1が無くh3から始まるページでもアウトラインを出す")
	void buildBuildsOutlineWhenFirstHeadingIsNotH1() {
		String report = build("<body><h3>小見出しから始まる</h3><h4>その下</h4></body>");

		assertTrue(Strings.CS.contains(report, "- h3 小見出しから始まる"));
		assertFalse(Strings.CS.contains(report, "見出しレベルが飛んでいます"));
	}

	@Test
	@DisplayName("見出しが1つ下がるだけの並びには注記を付けない")
	void buildDoesNotAnnotateNormalHeadingSequence() {
		String report = build("<body><h1>大</h1><h2>中</h2><h1>大2</h1></body>");

		assertFalse(Strings.CS.contains(report, "飛んでいます"));
	}

	@Test
	@DisplayName("langが無いページでも「（指定なし）」として出す")
	void buildMarksMissingLangAsNotSet() {
		String report = build("<html><head><title>設計メモ</title></head><body><p>本文</p></body></html>");

		assertTrue(Strings.CS.contains(report, "- lang: （指定なし）"));
	}

	/**
	 * HTMLから構造レポートを組み立てる。
	 *
	 * @param html HTML
	 * @return 構造レポートのMarkdown
	 */
	private String build(String html) {
		Document document = Jsoup.parse(html, BASE_URI);
		return webStructureReportBuilder
				.build(new WebPageContent(document.title(), StringUtils.EMPTY, document.body(), document));
	}
}
