package com.clip.ghost.webcontent.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.clip.ghost.common.utils.MarkdownBlockJoiner;

import lombok.NoArgsConstructor;

/**
 * 取り込んだページの構造を、制作の参考にできるレポートへ組み立てるクラス。
 * <p>
 * 出すのは<strong>観察した事実と数値だけ</strong>で、評価も改善提案も書かない。「この見出し構成は良い/悪い」の
 * 判断が要るときは、出来上がったMarkdownを既存の {@code POST /markdownAiTransform} へ渡す。
 * この分担を崩すと、抽出器の中にAIの都合（プロンプト、トークン上限、provider差）が混ざり始める。
 * <p>
 * 節は3つに絞る（文書メタ・見出しアウトライン・ランドマーク構成）。抽出できる項目は他にもあるが、
 * 使ってみて「これが欲しい」と分かってから足す。使う前に項目を増やすと、読まれない節の維持費だけが残る。
 * <p>
 * 見るのは除去前のDOM（{@link WebPageContent#document()}）。本文から外すナビゲーションやフッターこそ、
 * ページの組み立て方を見るときの対象になる。
 * <p>
 * jsoupはJavaScriptを実行せずCSSも評価しないため、次は取れない。取れないものをそれらしく埋めない。
 * <ul>
 * <li>算出後のスタイル（実際の文字サイズや色）。</li>
 * <li>外部CSSファイルの中身。読み込んでいるファイルの存在までしか分からない。</li>
 * <li>JavaScriptで描画される要素。SPAをURLから取ると、ほぼ空のDOMしか返らない。
 * その場合はブラウザで「名前を付けて保存」したHTMLをアップロードする経路を使う。</li>
 * </ul>
 */
@Component
@NoArgsConstructor
public class WebStructureReportBuilder {
	private static final String REPORT_HEADING = "## 構造レポート";
	private static final String META_HEADING = "### 文書メタ";
	private static final String OUTLINE_HEADING = "### 見出しアウトライン";
	private static final String LANDMARK_HEADING = "### ランドマーク構成";
	private static final String ITEM_FORMAT = "- %s: %s";
	private static final String LIST_INDENT = "  ";
	private static final String UNORDERED_MARKER = "- ";
	private static final String LINE_SEPARATOR = "\n";
	private static final String NOT_SET = "（指定なし）";
	private static final String NO_HEADING_MESSAGE = "見出し要素がありません。";
	private static final String NO_LANDMARK_MESSAGE = "ランドマーク要素（header / nav / main / section / article / aside / footer）がありません。";
	private static final String HEADING_SELECTOR = "h1,h2,h3,h4,h5,h6";
	private static final String HEADING_SKIP_FORMAT = "%s（h%d から h%d へ見出しレベルが飛んでいます）";
	private static final String HEADING_LINE_FORMAT = "h%d %s";
	private static final String LANDMARK_LINE_FORMAT = "%s%s";
	private static final String LANDMARK_LABEL_FORMAT = " … %s";
	private static final String REPEATED_LINE_FORMAT = "%s（同じ並びが %d 件）";
	private static final int MAX_HEADING_LEVEL = 6;

	/** ランドマークとして数える要素。HTMLの文書構造を表す要素だけに限る。 */
	private static final List<String> LANDMARK_TAG_NAMES = List.of("header", "nav", "main", "section", "article",
			"aside", "footer");

	/** 文書メタとして拾う {@code <meta name="...">}。SEO・表示制御に関わるものに限る。 */
	private static final List<String> META_NAMES = List.of("description", "robots", "viewport", "theme-color",
			"author", "keywords");

	/** 文書メタとして拾うOGP（{@code <meta property="og:...">}）。SNS共有の作り込みを見るため。 */
	private static final List<String> OGP_PROPERTIES = List.of("og:title", "og:type", "og:description", "og:image",
			"og:url", "og:site_name");

	/**
	 * 構造レポートを組み立てる。
	 *
	 * @param content HTMLから取り出した本文と元のDOM
	 * @return 構造レポートのMarkdown
	 */
	public String build(WebPageContent content) {
		Objects.requireNonNull(content, "content must not be null.");
		Document document = content.document();
		List<String> blocks = new ArrayList<>();
		blocks.add(REPORT_HEADING);
		blocks.add(META_HEADING);
		blocks.add(buildMetaSection(document));
		blocks.add(OUTLINE_HEADING);
		blocks.add(buildOutlineSection(document));
		blocks.add(LANDMARK_HEADING);
		blocks.add(buildLandmarkSection(document));
		return MarkdownBlockJoiner.join(blocks);
	}

	/**
	 * 文書メタの節を組み立てる。
	 * <p>
	 * 「指定なし」も事実として書く。未設定であることは、そのサイトがSEOやSNS共有をどこまで
	 * 作り込んでいるかを示す情報になる。
	 *
	 * @param document 除去前のDOM
	 * @return 文書メタのMarkdown
	 */
	private String buildMetaSection(Document document) {
		List<String> lines = new ArrayList<>();
		lines.add(ITEM_FORMAT.formatted("title", defaultNotSet(document.title())));
		lines.add(ITEM_FORMAT.formatted("lang", defaultNotSet(attributeOf(document, "html", "lang"))));
		lines.add(ITEM_FORMAT.formatted("canonical", defaultNotSet(attributeOf(document, "link[rel=canonical]", "href"))));
		lines.add(ITEM_FORMAT.formatted("favicon", defaultNotSet(attributeOf(document, "link[rel~=icon]", "href"))));
		META_NAMES.forEach(name -> lines
				.add(ITEM_FORMAT.formatted("meta " + name, defaultNotSet(metaContent(document, "name", name)))));
		OGP_PROPERTIES.forEach(property -> lines
				.add(ITEM_FORMAT.formatted(property, defaultNotSet(metaContent(document, "property", property)))));
		return String.join(LINE_SEPARATOR, lines);
	}

	/**
	 * 見出しアウトラインの節を組み立てる。
	 * <p>
	 * 見出しレベルをそのままインデントの深さへ写し、レベルが飛んだ箇所には注記を付ける。
	 * 見出しの飛び（h2の次がh4など）は情報設計の乱れがそのまま出る場所で、参考にするときに
	 * 最初に見たい情報のひとつ。飛びを「直すべき」とは書かず、飛んでいる事実だけを書く。
	 *
	 * @param document 除去前のDOM
	 * @return 見出しアウトラインのMarkdown
	 */
	private String buildOutlineSection(Document document) {
		List<Element> headings = document.select(HEADING_SELECTOR).stream().toList();
		if (CollectionUtils.isEmpty(headings)) {
			return NO_HEADING_MESSAGE;
		}
		List<String> lines = new ArrayList<>();
		int previousLevel = 0;
		for (Element heading : headings) {
			int level = headingLevel(heading.normalName());
			String line = LIST_INDENT.repeat(Math.max(0, level - 1)) + UNORDERED_MARKER
					+ HEADING_LINE_FORMAT.formatted(level, StringUtils.normalizeSpace(heading.text()));
			lines.add(previousLevel > 0 && level > previousLevel + 1
					? HEADING_SKIP_FORMAT.formatted(line, previousLevel, level)
					: line);
			previousLevel = level;
		}
		return String.join(LINE_SEPARATOR, lines);
	}

	/**
	 * ランドマーク構成の節を組み立てる。
	 * <p>
	 * 件数の一覧ではなく入れ子のまま出す。「header の中に nav がいくつあるか」「main の直下を
	 * section で割っているか」といった組み立て方は、件数だけでは分からない。
	 * <p>
	 * 同じ種類の要素が複数ある場合の見分けが付くよう、{@code aria-label} / {@code id} / {@code class} が
	 * あれば添える。無ければ要素名だけを出す。
	 *
	 * @param document 除去前のDOM
	 * @return ランドマーク構成のMarkdown
	 */
	private String buildLandmarkSection(Document document) {
		List<String> lines = new ArrayList<>();
		appendLandmarks(lines, document.body(), 0);
		return CollectionUtils.isEmpty(lines) ? NO_LANDMARK_MESSAGE
				: String.join(LINE_SEPARATOR, collapseRepeatedLines(lines));
	}

	/**
	 * 同じ行が続く箇所を件数へまとめる。
	 * <p>
	 * 記事を {@code section} で区切るページでは、同じ行が20件以上並ぶことがある。並びをそのまま出すと
	 * レポートの大半がその羅列になり、構成が読み取れなくなる。入れ子を持つ要素は行の内容が変わるため、
	 * 連続する完全一致だけをまとめれば、構成を崩さずに件数へ置き換えられる。
	 *
	 * @param lines ランドマークの行
	 * @return 連続する同一行を件数へまとめた行
	 */
	private List<String> collapseRepeatedLines(List<String> lines) {
		List<String> collapsed = new ArrayList<>();
		int index = 0;
		while (index < lines.size()) {
			String line = lines.get(index);
			int count = 1;
			while (index + count < lines.size() && Strings.CS.equals(lines.get(index + count), line)) {
				count++;
			}
			collapsed.add(count == 1 ? line : REPEATED_LINE_FORMAT.formatted(line, count));
			index += count;
		}
		return collapsed;
	}

	/**
	 * ランドマーク要素を入れ子のまま行として追加する。
	 *
	 * @param lines   追加先の行リスト
	 * @param element 走査対象の要素
	 * @param depth   ランドマークの入れ子の深さ
	 */
	private void appendLandmarks(List<String> lines, Element element, int depth) {
		if (Objects.isNull(element)) {
			return;
		}
		for (Element child : element.children()) {
			boolean landmark = LANDMARK_TAG_NAMES.contains(child.normalName());
			if (landmark) {
				lines.add(LIST_INDENT.repeat(depth) + UNORDERED_MARKER
						+ LANDMARK_LINE_FORMAT.formatted(child.normalName(), describeLandmark(child)));
			}
			// ランドマークでない入れ物（div等）は深さを増やさずに掘る。装飾用のdivの数だけ段が深くなると、
			// 元の文書構造よりも実装の都合が前に出てしまう。
			appendLandmarks(lines, child, landmark ? depth + 1 : depth);
		}
	}

	/**
	 * ランドマーク要素の見分けが付く説明を組み立てる。
	 *
	 * @param element ランドマーク要素
	 * @return 説明。手がかりが無ければ空文字
	 */
	private String describeLandmark(Element element) {
		String label = StringUtils.firstNonBlank(element.attr("aria-label"), element.id(), element.className());
		return StringUtils.isBlank(label) ? StringUtils.EMPTY
				: LANDMARK_LABEL_FORMAT.formatted(StringUtils.normalizeSpace(label));
	}

	/**
	 * 見出しタグ名から見出しレベルを求める。
	 *
	 * @param tagName タグ名
	 * @return 見出しレベル。見出しでない場合は1
	 */
	private int headingLevel(String tagName) {
		for (int level = 1; level <= MAX_HEADING_LEVEL; level++) {
			if (Strings.CS.equals(tagName, "h" + level)) {
				return level;
			}
		}
		return 1;
	}

	/**
	 * セレクタに一致する最初の要素の属性値を取得する。
	 *
	 * @param document      除去前のDOM
	 * @param selector      CSSセレクタ
	 * @param attributeName 属性名
	 * @return 属性値。要素が無ければ空文字
	 */
	private String attributeOf(Document document, String selector, String attributeName) {
		Element element = document.selectFirst(selector);
		return Objects.isNull(element) ? StringUtils.EMPTY : StringUtils.normalizeSpace(element.attr(attributeName));
	}

	/**
	 * {@code <meta>} のcontent値を取得する。
	 *
	 * @param document      除去前のDOM
	 * @param attributeName 絞り込みに使う属性名（{@code name} または {@code property}）
	 * @param attributeValue 属性値
	 * @return content値。要素が無ければ空文字
	 */
	private String metaContent(Document document, String attributeName, String attributeValue) {
		return attributeOf(document, "meta[" + attributeName + "=" + attributeValue + "]", "content");
	}

	/**
	 * 空の場合に「指定なし」へ置き換える。
	 *
	 * @param value 値
	 * @return 値。空なら「（指定なし）」
	 */
	private String defaultNotSet(String value) {
		return StringUtils.defaultIfBlank(StringUtils.normalizeSpace(value), NOT_SET);
	}
}
