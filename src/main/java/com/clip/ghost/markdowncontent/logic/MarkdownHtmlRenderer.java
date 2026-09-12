package com.clip.ghost.markdowncontent.logic;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Markdown本文をsanitize済みHTMLへ変換する内部ロジック。
 * <p>
 * 画面プレビューとPDF出力の両方がこの変換を使う。変換規則が2箇所に分かれると、
 * 「プレビューでは表になるのにPDFでは崩れる」といった差が後から効いてくるため、1箇所に閉じる。
 * <p>
 * commonmarkとjsoupの依存はこのクラスに閉じ込め、Service層はHTML文字列だけを扱う。
 */
@Component
public class MarkdownHtmlRenderer {
	// GFM表拡張はセルの列揃えを th/td の align 属性として出力するが、Safelist.relaxed() は align を
	// 許可しないため、列揃えを保つ目的でこの2属性だけ明示的に許可する。他の属性の許可範囲は変えない。
	private static final Safelist MARKDOWN_SAFELIST = Safelist.relaxed().addAttributes("th", "align")
			.addAttributes("td", "align");

	private final Parser markdownParser;
	private final HtmlRenderer htmlRenderer;

	/**
	 * GFMの表を解釈できるMarkdownパーサーとHTMLレンダラーを組み立てる。
	 */
	public MarkdownHtmlRenderer() {
		// commonmarkの素のParserはGFMの表を解釈しないため、表拡張をParserとHtmlRendererの両方へ渡す。
		// 片方だけに渡すと表として描画されない。生HTMLはescapeHtml(true)でエスケープし、その後jsoupでsanitizeする。
		List<Extension> extensions = List.of(TablesExtension.create());
		this.markdownParser = Parser.builder().extensions(extensions).build();
		this.htmlRenderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).build();
	}

	/**
	 * Markdown本文をHTMLへ変換し、表示用にsanitizeする。
	 *
	 * @param markdown Markdown本文
	 * @return sanitize済みHTML
	 */
	public String render(String markdown) {
		Node document = markdownParser.parse(StringUtils.defaultString(markdown));
		return Jsoup.clean(htmlRenderer.render(document), MARKDOWN_SAFELIST);
	}
}
