package com.clip.ghost.webcontent.logic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import com.clip.ghost.common.utils.MarkdownBlockJoiner;
import com.clip.ghost.common.utils.MarkdownTableBuilder;

import lombok.NoArgsConstructor;

/**
 * HTMLから取り出した本文をMarkdownへ組み立てるクラス。
 * <p>
 * 変換するのは見出し・段落・リスト・表・コードブロック・引用・水平線・リンク・画像だけにする。
 * HTMLの表現力をすべてMarkdownへ写すことはできないため、対応表を固定し、それ以外は文字として残す。
 * 対応外のタグを見つけるたびに規則を足すと、同じページを取り込み直したときに結果が変わる。
 * <p>
 * 表の組み立てとブロックの連結は {@link MarkdownTableBuilder} / {@link MarkdownBlockJoiner} を使う。
 * Office文書からのMarkdownと同じ組み立てにそろえ、取り込み元による見た目の差を作らない。
 * <p>
 * 画像は参照だけ書き出し、取得はしない。帯域と権利の両方の問題を持ち込まずに、
 * 「元ページのどこに何の画像があったか」は残せる。
 */
@Component
@NoArgsConstructor
public class WebMarkdownBuilder {
	private static final DateTimeFormatter RETRIEVED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final String SOURCE_LINE_FORMAT = "- 取得元: %s";
	private static final String RETRIEVED_AT_LINE_FORMAT = "- 取得日時: %s";
	private static final String DESCRIPTION_LINE_FORMAT = "- 説明: %s";
	private static final String HEADING_MARKER = "#";
	private static final String HEADING_TAG_PREFIX = "h";
	private static final String SPACE = " ";
	private static final String LINE_SEPARATOR = "\n";
	private static final String LIST_INDENT = "  ";
	private static final String UNORDERED_MARKER = "- ";
	private static final String ORDERED_MARKER_FORMAT = "%d. ";
	private static final String QUOTE_PREFIX = "> ";
	private static final String HORIZONTAL_RULE = "---";
	private static final String CODE_FENCE = "```";
	private static final String INLINE_CODE_MARKER = "`";
	private static final String BOLD_MARKER = "**";
	private static final String ITALIC_MARKER = "*";
	private static final String LINK_FORMAT = "[%s](%s)";
	private static final String IMAGE_FORMAT = "![%s](%s)";
	private static final String LANGUAGE_CLASS_PREFIX = "language-";
	private static final String HREF_ATTRIBUTE = "href";
	private static final String SRC_ATTRIBUTE = "src";
	private static final String ALT_ATTRIBUTE = "alt";
	private static final String TABLE_TAG = "table";
	private static final String CODE_TAG = "code";
	private static final int MAX_HEADING_LEVEL = 6;

	/** 入れ子の可能性があるブロック要素。これらを含む要素は、段落ではなく入れ物として扱う。 */
	private static final String BLOCK_ELEMENT_SELECTOR = "h1,h2,h3,h4,h5,h6,p,ul,ol,table,pre,blockquote,hr,div,section,article,main,li";

	/** インライン整形で読み飛ばすタグ。ブロックとして別に組み立てるため、文章の途中へ混ぜない。 */
	private static final List<String> INLINE_SKIP_TAG_NAMES = List.of("ul", "ol", "table");

	/**
	 * 本文をMarkdownへ組み立てる。
	 * <p>
	 * 冒頭には必ず出典ヘッダー（見出し・取得元・取得日時・説明）を付ける。Markdownメモへ貼った後に
	 * 「これはどこから取ったものか」を思い出せないと、引用の可否も更新の要否も判断できなくなる。
	 *
	 * @param content     HTMLから取り出した本文
	 * @param source      取得元の表示。アップロードならファイル名、Stage 2 ならURL
	 * @param retrievedAt 取得日時
	 * @return Markdown本文
	 */
	public String build(WebPageContent content, String source, LocalDateTime retrievedAt) {
		Objects.requireNonNull(content, "content must not be null.");
		List<String> blocks = new ArrayList<>();
		blocks.add(buildSourceHeader(content, source, retrievedAt));
		appendChildren(blocks, content.root());
		return MarkdownBlockJoiner.join(blocks);
	}

	/**
	 * 出典ヘッダーを組み立てる。
	 *
	 * @param content     HTMLから取り出した本文
	 * @param source      取得元の表示
	 * @param retrievedAt 取得日時
	 * @return 出典ヘッダーのMarkdown
	 */
	private String buildSourceHeader(WebPageContent content, String source, LocalDateTime retrievedAt) {
		String sourceLabel = StringUtils.defaultString(source);
		List<String> lines = new ArrayList<>();
		// タイトルが取れないHTML断片でも見出し無しにはしない。取得元がそのまま見出しになるほうが一覧で探せる。
		lines.add(HEADING_MARKER + SPACE + StringUtils.defaultIfBlank(content.title(), sourceLabel));
		lines.add(StringUtils.EMPTY);
		lines.add(SOURCE_LINE_FORMAT.formatted(sourceLabel));
		lines.add(RETRIEVED_AT_LINE_FORMAT.formatted(RETRIEVED_AT_FORMAT.format(retrievedAt)));
		if (StringUtils.isNotBlank(content.description())) {
			lines.add(DESCRIPTION_LINE_FORMAT.formatted(content.description()));
		}
		return String.join(LINE_SEPARATOR, lines);
	}

	/**
	 * 要素の子をそれぞれMarkdownブロックとして追加する。
	 *
	 * @param blocks  追加先のブロックリスト
	 * @param element 走査対象の要素
	 */
	private void appendChildren(List<String> blocks, Element element) {
		element.children().forEach(child -> appendBlock(blocks, child));
	}

	/**
	 * 要素1つをMarkdownブロックとして追加する。
	 * <p>
	 * 対応表に無いタグは、中にブロック要素があれば入れ物として掘り下げ、無ければ段落として扱う。
	 * {@code div} しか使っていないページでも本文が落ちないようにするため。
	 *
	 * @param blocks  追加先のブロックリスト
	 * @param element 変換対象の要素
	 */
	private void appendBlock(List<String> blocks, Element element) {
		String tagName = element.normalName();
		int headingLevel = resolveHeadingLevel(tagName);
		if (headingLevel > 0) {
			addIfNotBlank(blocks, HEADING_MARKER.repeat(headingLevel) + SPACE + renderInline(element));
			return;
		}
		switch (tagName) {
		case "p" -> addIfNotBlank(blocks, renderInline(element));
		case "ul", "ol" -> addIfNotBlank(blocks, buildList(element, 0));
		case "table" -> addIfNotBlank(blocks, MarkdownTableBuilder.build(readTableRows(element)));
		case "pre" -> addIfNotBlank(blocks, buildCodeBlock(element));
		case "blockquote" -> addIfNotBlank(blocks, buildBlockQuote(element));
		case "hr" -> blocks.add(HORIZONTAL_RULE);
		case "br" -> {
			// 空要素。ブロックの区切りとしては意味を持たないため何も出さない。
		}
		default -> appendContainerOrParagraph(blocks, element);
		}
	}

	/**
	 * 対応表に無いタグを、入れ物または段落として追加する。
	 *
	 * @param blocks  追加先のブロックリスト
	 * @param element 変換対象の要素
	 */
	private void appendContainerOrParagraph(List<String> blocks, Element element) {
		if (containsBlockElement(element)) {
			appendChildren(blocks, element);
			return;
		}
		addIfNotBlank(blocks, renderInline(element));
	}

	/**
	 * 要素の中にブロック要素があるかを判定する。
	 * <p>
	 * 判定はこの要素自身を除いて行う。{@code div} のように、それ自体がブロック要素である入れ物を
	 * 「中にブロックがある」と数えると、文字しか持たない {@code div} の中身が本文から落ちる。
	 *
	 * @param element 判定対象の要素
	 * @return 子孫にブロック要素がある場合true
	 */
	private boolean containsBlockElement(Element element) {
		return element.children().stream()
				.anyMatch(child -> CollectionUtils.isNotEmpty(child.select(BLOCK_ELEMENT_SELECTOR)));
	}

	/**
	 * 空でないブロックだけを追加する。
	 *
	 * @param blocks 追加先のブロックリスト
	 * @param block  追加するブロック
	 */
	private void addIfNotBlank(List<String> blocks, String block) {
		if (StringUtils.isNotBlank(block)) {
			blocks.add(block);
		}
	}

	/**
	 * 見出しタグから見出しレベルを求める。
	 *
	 * @param tagName タグ名
	 * @return 見出しレベル。見出しでない場合は0
	 */
	private int resolveHeadingLevel(String tagName) {
		for (int level = 1; level <= MAX_HEADING_LEVEL; level++) {
			if (Strings.CS.equals(tagName, HEADING_TAG_PREFIX + level)) {
				return level;
			}
		}
		return 0;
	}

	/**
	 * リストをMarkdownへ組み立てる。
	 * <p>
	 * 入れ子は半角2桁のインデントで表す。タブや4桁では、Markdownの実装によってコードブロックと
	 * 解釈されることがある。
	 *
	 * @param element リスト要素（{@code ul} / {@code ol}）
	 * @param depth   入れ子の深さ
	 * @return リストのMarkdown
	 */
	private String buildList(Element element, int depth) {
		boolean ordered = Strings.CS.equals(element.normalName(), "ol");
		List<String> lines = new ArrayList<>();
		int itemNumber = 1;
		for (Element item : element.children()) {
			if (!Strings.CS.equals(item.normalName(), "li")) {
				continue;
			}
			String marker = ordered ? ORDERED_MARKER_FORMAT.formatted(itemNumber) : UNORDERED_MARKER;
			itemNumber++;
			lines.add(StringUtils.stripEnd(LIST_INDENT.repeat(depth) + marker + renderInline(item), null));
			appendNestedLists(lines, item, depth);
		}
		return String.join(LINE_SEPARATOR, lines);
	}

	/**
	 * 項目の中にある入れ子リストを1段深いリストとして追加する。
	 *
	 * @param lines 追加先の行リスト
	 * @param item  リスト項目（{@code li}）
	 * @param depth 現在の入れ子の深さ
	 */
	private void appendNestedLists(List<String> lines, Element item, int depth) {
		for (Element child : item.children()) {
			if (Strings.CS.equalsAny(child.normalName(), "ul", "ol")) {
				addIfNotBlank(lines, buildList(child, depth + 1));
			}
		}
	}

	/**
	 * 表のセルを行ごとに読み取る。
	 * <p>
	 * 入れ子の表の行を外側の表へ混ぜないよう、直近の {@code table} がこの表である行だけを対象にする。
	 *
	 * @param table 表要素
	 * @return 行ごとのセル文字列
	 */
	private List<List<String>> readTableRows(Element table) {
		List<List<String>> rows = new ArrayList<>();
		for (Element row : table.select("tr")) {
			if (!Objects.equals(row.closest(TABLE_TAG), table)) {
				continue;
			}
			List<String> cells = new ArrayList<>();
			row.children().stream().filter(cell -> Strings.CS.equalsAny(cell.normalName(), "th", "td"))
					.forEach(cell -> cells.add(renderInline(cell)));
			if (CollectionUtils.isNotEmpty(cells)) {
				rows.add(cells);
			}
		}
		return rows;
	}

	/**
	 * コードブロックをMarkdownへ組み立てる。
	 * <p>
	 * {@code pre} の中身は整形済みテキストなので、空白と改行をそのまま残す。
	 *
	 * @param element {@code pre} 要素
	 * @return フェンス付きコードブロックのMarkdown
	 */
	private String buildCodeBlock(Element element) {
		String code = StringUtils.stripEnd(element.wholeText(), null);
		if (StringUtils.isBlank(code)) {
			return StringUtils.EMPTY;
		}
		return CODE_FENCE + resolveCodeLanguage(element) + LINE_SEPARATOR + code + LINE_SEPARATOR + CODE_FENCE;
	}

	/**
	 * コードブロックの言語指定を求める。
	 * <p>
	 * 多くのサイトが {@code class="language-java"} の形でシンタックスハイライトを指定しているため、
	 * それをそのままフェンスの言語指定へ写す。判定できない場合は言語指定なしにする。
	 *
	 * @param element {@code pre} 要素
	 * @return 言語指定。判定できない場合は空文字
	 */
	private String resolveCodeLanguage(Element element) {
		Element codeElement = element.selectFirst(CODE_TAG);
		List<String> classNames = new ArrayList<>(element.classNames());
		if (Objects.nonNull(codeElement)) {
			classNames.addAll(codeElement.classNames());
		}
		return classNames.stream().filter(className -> Strings.CS.startsWith(className, LANGUAGE_CLASS_PREFIX))
				.map(className -> Strings.CS.removeStart(className, LANGUAGE_CLASS_PREFIX)).findFirst()
				.orElse(StringUtils.EMPTY);
	}

	/**
	 * 引用をMarkdownへ組み立てる。
	 * <p>
	 * 引用の中の見出しや箇条書きも引用として残す。中身を段落1つへ潰すと、引用元の構造が消える。
	 *
	 * @param element {@code blockquote} 要素
	 * @return 引用のMarkdown
	 */
	private String buildBlockQuote(Element element) {
		List<String> innerBlocks = new ArrayList<>();
		appendChildren(innerBlocks, element);
		String inner = MarkdownBlockJoiner.join(innerBlocks);
		if (StringUtils.isBlank(inner)) {
			inner = renderInline(element);
		}
		if (StringUtils.isBlank(inner)) {
			return StringUtils.EMPTY;
		}
		return inner.lines().map(line -> StringUtils.stripEnd(QUOTE_PREFIX + line, null))
				.reduce((left, right) -> left + LINE_SEPARATOR + right).orElse(StringUtils.EMPTY);
	}

	/**
	 * 要素の中身をインラインのMarkdownへ整形する。
	 *
	 * @param element 対象要素
	 * @return インライン整形後の文字列
	 */
	private String renderInline(Element element) {
		StringBuilder builder = new StringBuilder();
		for (Node node : element.childNodes()) {
			appendInlineNode(builder, node);
		}
		return StringUtils.strip(builder.toString());
	}

	/**
	 * ノード1つをインラインのMarkdownとして追加する。
	 *
	 * @param builder 追加先
	 * @param node    対象ノード
	 */
	private void appendInlineNode(StringBuilder builder, Node node) {
		if (node instanceof TextNode textNode) {
			// TextNode#text は空白を正規化する。HTMLの改行・インデントをそのまま残すと段落が不自然に折れる。
			builder.append(textNode.text());
			return;
		}
		if (node instanceof Element element) {
			builder.append(renderInlineElement(element));
		}
	}

	/**
	 * 要素をインラインのMarkdownへ整形する。
	 * <p>
	 * 対応表に無いタグ（{@code span} 等）は、囲みを外して中身だけを残す。
	 *
	 * @param element 対象要素
	 * @return インライン整形後の文字列
	 */
	private String renderInlineElement(Element element) {
		String tagName = element.normalName();
		if (INLINE_SKIP_TAG_NAMES.contains(tagName)) {
			return StringUtils.EMPTY;
		}
		return switch (tagName) {
		case "a" -> renderLink(element);
		case "img" -> renderImage(element);
		case "br" -> LINE_SEPARATOR;
		case "code" -> wrapIfNotBlank(renderInline(element), INLINE_CODE_MARKER);
		case "strong", "b" -> wrapIfNotBlank(renderInline(element), BOLD_MARKER);
		case "em", "i" -> wrapIfNotBlank(renderInline(element), ITALIC_MARKER);
		default -> renderInline(element);
		};
	}

	/**
	 * 中身が空でない場合だけ、前後に記号を付ける。
	 * <p>
	 * 空のまま記号だけを残すと、{@code ****} のような読めない記号列がMarkdownに残る。
	 *
	 * @param text   中身
	 * @param marker 前後へ付ける記号
	 * @return 記号で囲んだ文字列。中身が空なら空文字
	 */
	private String wrapIfNotBlank(String text, String marker) {
		return StringUtils.isBlank(text) ? StringUtils.EMPTY : marker + text + marker;
	}

	/**
	 * リンクをMarkdownへ整形する。
	 * <p>
	 * URLは {@code absUrl} で絶対化する。相対パスのまま残すと、Markdownメモから開けないリンクになる。
	 * 基準URLが無い（アップロードしたHTMLに {@code <base>} が無い）場合は、書かれていたURLをそのまま残す。
	 *
	 * @param element {@code a} 要素
	 * @return リンクのMarkdown。テキストもURLも無い場合は空文字
	 */
	private String renderLink(Element element) {
		String text = renderInline(element);
		String url = resolveUrl(element, HREF_ATTRIBUTE);
		if (StringUtils.isBlank(url)) {
			return text;
		}
		return LINK_FORMAT.formatted(StringUtils.defaultIfBlank(text, url), url);
	}

	/**
	 * 画像参照をMarkdownへ整形する。画像の取得はしない。
	 *
	 * @param element {@code img} 要素
	 * @return 画像参照のMarkdown。URLが無い場合は空文字
	 */
	private String renderImage(Element element) {
		String url = resolveUrl(element, SRC_ATTRIBUTE);
		if (StringUtils.isBlank(url)) {
			return StringUtils.EMPTY;
		}
		return IMAGE_FORMAT.formatted(StringUtils.normalizeSpace(element.attr(ALT_ATTRIBUTE)), url);
	}

	/**
	 * 属性のURLを絶対化して取り出す。
	 *
	 * @param element       対象要素
	 * @param attributeName URLを持つ属性名
	 * @return 絶対URL。絶対化できない場合は書かれていた値
	 */
	private String resolveUrl(Element element, String attributeName) {
		String absoluteUrl = element.absUrl(attributeName);
		return StringUtils.defaultIfBlank(absoluteUrl, StringUtils.normalizeSpace(element.attr(attributeName)));
	}
}
