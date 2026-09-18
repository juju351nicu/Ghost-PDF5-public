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
import com.clip.ghost.webcontent.enums.WebMarkdownDraftMode;

import lombok.RequiredArgsConstructor;

/**
 * HTMLから取り出した本文をMarkdownへ組み立てるクラス。
 * <p>
 * 変換するのは見出し・段落・リスト・定義リスト・表・コードブロック・引用・水平線・リンク・画像だけにする。
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
@RequiredArgsConstructor
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
	private static final String DEFINITION_SEPARATOR = ": ";
	private static final String ORDERED_MARKER_FORMAT = "%d. ";
	private static final String QUOTE_PREFIX = "> ";
	private static final String HORIZONTAL_RULE = "---";
	private static final String ELLIPSIS = "…";
	private static final int MAX_DESCRIPTION_LENGTH = 200;
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
	private static final String CAPTION_TAG = "caption";
	private static final String LINK_SELECTOR = "a[href]";
	private static final int MAX_HEADING_LEVEL = 6;

	/** 入れ子の可能性があるブロック要素。これらを含む要素は、段落ではなく入れ物として扱う。 */
	private static final String BLOCK_ELEMENT_SELECTOR = "h1,h2,h3,h4,h5,h6,p,ul,ol,table,pre,blockquote,hr,div,section,article,main,li";

	/** インライン整形で読み飛ばすタグ。ブロックとして別に組み立てるため、文章の途中へ混ぜない。 */
	private static final List<String> INLINE_SKIP_TAG_NAMES = List.of("ul", "ol", "table");

	/** リスト項目の中で、項目の続きのブロックとして出すタグ。インライン整形では拾えない。 */
	private static final List<String> ITEM_BLOCK_TAG_NAMES = List.of("table", "pre", "blockquote", "dl");

	private final WebStructureReportBuilder webStructureReportBuilder;

	/**
	 * 本文をMarkdownへ組み立てる。
	 * <p>
	 * 冒頭には必ず出典ヘッダー（見出し・取得元・取得日時・説明）を付ける。Markdownメモへ貼った後に
	 * 「これはどこから取ったものか」を思い出せないと、引用の可否も更新の要否も判断できなくなる。
	 * <p>
	 * 構造レポートは本文より前に置く。レポートは十数行、本文は数千行になり得るため、後ろに置くと
	 * 読むために本文全体をスクロールすることになる。
	 *
	 * @param content     HTMLから取り出した本文
	 * @param source      取得元の表示。アップロードならファイル名、URL取得ならURL
	 * @param retrievedAt 取得日時
	 * @param mode        出力モード。本文・構造レポート・その両方
	 * @return Markdown本文
	 */
	public String build(WebPageContent content, String source, LocalDateTime retrievedAt,
			WebMarkdownDraftMode mode) {
		Objects.requireNonNull(content, "content must not be null.");
		List<String> blocks = new ArrayList<>();
		blocks.add(buildSourceHeader(content, source, retrievedAt));
		if (mode.outputsStructure()) {
			addIfNotBlank(blocks, webStructureReportBuilder.build(content));
		}
		if (mode.outputsArticle()) {
			appendChildren(blocks, content.root());
		}
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
			// ページの索引やナビの文字列をそのままdescriptionへ入れているサイトがあり、放っておくと
			// 出典ヘッダーだけで数百文字になる。出典は一目で読めることに意味があるので切り詰める。
			lines.add(DESCRIPTION_LINE_FORMAT
					.formatted(StringUtils.abbreviate(content.description(), ELLIPSIS, MAX_DESCRIPTION_LENGTH)));
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
			addIfNotBlank(blocks, HEADING_MARKER.repeat(headingLevel) + SPACE + renderHeadingText(element));
			return;
		}
		switch (tagName) {
		case "p" -> addIfNotBlank(blocks, renderInline(element));
		case "ul", "ol" -> addIfNotBlank(blocks, buildList(element, 0));
		case "dl" -> addIfNotBlank(blocks, buildDefinitionList(element));
		case "table" -> appendTable(blocks, element);
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
	 * 表をMarkdownへ追加する。
	 * <p>
	 * {@code caption} は表の直前に太字の1行として出す。Markdownの表にキャプションの記法が無く、
	 * 捨てると「何の表か」が分からなくなる。入れ子の表のキャプションを外側へ付けないよう、
	 * 直近の {@code table} がこの表であるものだけを対象にする。
	 *
	 * @param blocks  追加先のブロックリスト
	 * @param element 表要素
	 */
	private void appendTable(List<String> blocks, Element element) {
		Element caption = element.selectFirst(CAPTION_TAG);
		if (Objects.nonNull(caption) && Objects.equals(caption.closest(TABLE_TAG), element)) {
			addIfNotBlank(blocks, wrapIfNotBlank(renderInline(caption), BOLD_MARKER));
		}
		addIfNotBlank(blocks, MarkdownTableBuilder.build(readTableRows(element)));
	}

	/**
	 * 対応表に無いタグを、入れ物または段落として追加する。
	 *
	 * @param blocks  追加先のブロックリスト
	 * @param element 変換対象の要素
	 */
	private void appendContainerOrParagraph(List<String> blocks, Element element) {
		if (!containsBlockElement(element)) {
			addIfNotBlank(blocks, renderInline(element));
			return;
		}
		// 子をそのまま掘るだけだと、入れ物の直下に地の文がある場合（<div>説明<p>…</p></div>）にその文が落ちる。
		// ブロックに当たるまでのインライン要素とテキストを1つの段落としてまとめ、出現順を保ったまま出す。
		StringBuilder inlineBuffer = new StringBuilder();
		for (Node node : element.childNodes()) {
			if (node instanceof Element child && isBlockElement(child)) {
				flushInlineBuffer(blocks, inlineBuffer);
				appendBlock(blocks, child);
				continue;
			}
			appendInlineNode(inlineBuffer, node);
		}
		flushInlineBuffer(blocks, inlineBuffer);
	}

	/**
	 * 溜めたインラインの塊を段落として書き出し、バッファを空にする。
	 *
	 * @param blocks       追加先のブロックリスト
	 * @param inlineBuffer インラインの塊
	 */
	private void flushInlineBuffer(List<String> blocks, StringBuilder inlineBuffer) {
		addIfNotBlank(blocks, StringUtils.strip(inlineBuffer.toString()));
		inlineBuffer.setLength(0);
	}

	/**
	 * ブロックとして扱う要素かを判定する。
	 * <p>
	 * 自身がブロック要素か、ブロック要素を含む場合にtrueを返す。中にブロックを抱えたインライン要素を
	 * 段落へ混ぜると、その中の見出しや表が段落の文字列として潰れてしまう。
	 *
	 * @param element 判定対象の要素
	 * @return ブロックとして扱う場合true
	 */
	private boolean isBlockElement(Element element) {
		return CollectionUtils.isNotEmpty(element.select(BLOCK_ELEMENT_SELECTOR));
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
			appendItemBlocks(lines, item, depth);
		}
		return String.join(LINE_SEPARATOR, lines);
	}

	/**
	 * 定義リストをMarkdownへ組み立てる。
	 * <p>
	 * Markdownに定義リストの記法は無いため、「用語: 説明」の箇条書きへ写す。用語を太字にするのは、
	 * 説明と地続きに並べると、どこまでが用語なのかが読み取れなくなるため。
	 * <p>
	 * 用語と説明を別々の段落として出す手もあるが、それだと対応関係が消える。APIリファレンスの
	 * 属性一覧のように、定義リストは「どの語の説明か」が価値の中心にある。
	 *
	 * @param element 定義リスト要素（{@code dl}）
	 * @return 定義リストのMarkdown
	 */
	private String buildDefinitionList(Element element) {
		List<String> lines = new ArrayList<>();
		for (Element child : element.children()) {
			String text = renderInline(child);
			if (StringUtils.isBlank(text)) {
				continue;
			}
			if (Strings.CS.equals(child.normalName(), "dt")) {
				lines.add(UNORDERED_MARKER + wrapIfNotBlank(text, BOLD_MARKER));
				continue;
			}
			appendDefinitionDescription(lines, text);
		}
		return String.join(LINE_SEPARATOR, lines);
	}

	/**
	 * 定義の説明を、直前の用語へつなげる形で追加する。
	 * <p>
	 * 1つの用語に説明が複数付く場合があるため、2つ目以降は続きの行として並べる。
	 * 用語より先に説明が現れる壊れた並びのHTMLでも、説明を捨てずに箇条書きとして残す。
	 *
	 * @param lines 追加先の行リスト
	 * @param text  説明のテキスト
	 */
	private void appendDefinitionDescription(List<String> lines, String text) {
		if (CollectionUtils.isEmpty(lines)) {
			lines.add(UNORDERED_MARKER + text);
			return;
		}
		int lastIndex = lines.size() - 1;
		String lastLine = lines.get(lastIndex);
		if (Strings.CS.endsWith(lastLine, BOLD_MARKER)) {
			lines.set(lastIndex, lastLine + DEFINITION_SEPARATOR + text);
			return;
		}
		lines.set(lastIndex, lastLine + LINE_SEPARATOR + LIST_INDENT + text);
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
	 * リスト項目の中にあるブロック（表・コードブロック・引用・定義リスト）を項目の続きとして追加する。
	 * <p>
	 * これらはインライン整形で読み飛ばすため、追わないと項目の中身が丸ごと落ちる。項目の続きだと分かるよう、
	 * 1段深いインデントを付けて並べる。
	 *
	 * @param lines 追加先の行リスト
	 * @param item  リスト項目（{@code li}）
	 * @param depth 現在の入れ子の深さ
	 */
	private void appendItemBlocks(List<String> lines, Element item, int depth) {
		String indent = LIST_INDENT.repeat(depth + 1);
		for (Element child : item.children()) {
			if (!ITEM_BLOCK_TAG_NAMES.contains(child.normalName())) {
				continue;
			}
			List<String> childBlocks = new ArrayList<>();
			appendBlock(childBlocks, child);
			MarkdownBlockJoiner.join(childBlocks).lines()
					.forEach(line -> lines.add(StringUtils.stripEnd(indent + line, null)));
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
		// Markdownの説明ページのように、コードの中にコードフェンスが入っていることがある。囲みと同じ長さだと
		// そこでブロックが終わったと解釈され、以降の本文がコード扱いになる。中で使われているより長い囲みにする。
		String fence = INLINE_CODE_MARKER.repeat(Math.max(CODE_FENCE.length(), longestBacktickRun(code) + 1));
		return fence + resolveCodeLanguage(element) + LINE_SEPARATOR + code + LINE_SEPARATOR + fence;
	}

	/**
	 * 文字列に含まれるバッククォートの最長連続数を数える。
	 *
	 * @param text 対象文字列
	 * @return バッククォートの最長連続数
	 */
	private int longestBacktickRun(String text) {
		int longest = 0;
		int current = 0;
		for (char character : text.toCharArray()) {
			current = character == '`' ? current + 1 : 0;
			longest = Math.max(longest, current);
		}
		return longest;
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
	 * 見出しの中身をインラインのMarkdownへ整形する。
	 * <p>
	 * 見出しの中ではリンク記法を作らず、テキストだけを残す。多くのサイトが見出しに「この見出しへの
	 * リンク」を埋め込んでおり、そのまま写すと目次として読みたい見出しがすべてリンク記法になる。
	 * 行き先は同じページの同じ見出しなので、落としても情報は減らない。
	 *
	 * @param element 見出し要素
	 * @return インライン整形後の見出し文字列
	 */
	private String renderHeadingText(Element element) {
		// 元のDOMを壊さないよう複製してからリンクの囲みだけを外す。呼び出し元は同じDOMを使い続ける。
		Element heading = element.clone();
		heading.select(LINK_SELECTOR).unwrap();
		return renderInline(heading);
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
		case "code" -> renderInlineCode(element);
		case "strong", "b" -> wrapIfNotBlank(renderInline(element), BOLD_MARKER);
		case "em", "i" -> wrapIfNotBlank(renderInline(element), ITALIC_MARKER);
		default -> renderInline(element);
		};
	}

	/**
	 * インラインコードをMarkdownへ整形する。
	 * <p>
	 * コード記法の中では他の記法が働かない。中身を通常のインライン整形に掛けると、
	 * APIリファレンスのように {@code <code><a>…</a></code>} と書かれたページで
	 * リンク記法がコード記法の内側へ入り、どちらとしても描画されない文字列になる。
	 * そのため中身は素のテキストだけを使う。
	 * <p>
	 * ただし、コード全体が1つのリンクになっている場合は、コードを包む形
	 * （{@code [`text`](url)}）へ入れ替える。この形ならリンクとコードが両立し、
	 * 参照先を失わずに済む。部分的にリンクを含む場合は入れ替えられないため、コードとしてだけ残す。
	 *
	 * @param element {@code code} 要素
	 * @return インラインコードのMarkdown
	 */
	private String renderInlineCode(Element element) {
		String code = wrapInlineCode(StringUtils.normalizeSpace(element.text()));
		if (StringUtils.isBlank(code)) {
			return StringUtils.EMPTY;
		}
		Element link = element.selectFirst(LINK_SELECTOR);
		if (Objects.isNull(link) || !Strings.CS.equals(link.text(), element.text())) {
			return code;
		}
		String url = resolveUrl(link, HREF_ATTRIBUTE);
		return StringUtils.isBlank(url) ? code : LINK_FORMAT.formatted(code, url);
	}

	/**
	 * インラインコードの囲みを付ける。
	 * <p>
	 * 中身にバッククォートがある場合は、それより1つ多い囲みにして前後へ空白を入れる（Markdownの規則）。
	 * 同じ長さの囲みだと、コードの途中でコードが終わったと解釈される。
	 *
	 * @param text コードの中身
	 * @return 囲みを付けたインラインコード。中身が空なら空文字
	 */
	private String wrapInlineCode(String text) {
		if (StringUtils.isBlank(text)) {
			return StringUtils.EMPTY;
		}
		int longestRun = longestBacktickRun(text);
		String marker = INLINE_CODE_MARKER.repeat(longestRun + 1);
		String padding = longestRun > 0 ? SPACE : StringUtils.EMPTY;
		return marker + padding + text + padding + marker;
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
