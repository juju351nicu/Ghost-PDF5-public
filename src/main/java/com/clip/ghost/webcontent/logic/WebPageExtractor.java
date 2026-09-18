package com.clip.ghost.webcontent.logic;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector.SelectorParseException;
import org.springframework.stereotype.Component;

import com.clip.ghost.webcontent.exception.WebInputException;
import com.clip.ghost.webcontent.exception.WebProcessingException;

import lombok.NoArgsConstructor;

/**
 * HTMLを解析し、Markdownへ写す対象（タイトル・説明・本文の根）を取り出すクラス。
 * <p>
 * 本文らしさをスコアリングするヒューリスティックは使わない。除去するタグを固定の一覧に限ることで、
 * 「なぜこの出力になったのか」を毎回同じ根拠で説明できるようにする。読み落としが起きる場合は、
 * 利用者がセレクタ（{@code article} など）を指定して絞り込む。
 * <p>
 * ネットワークへは出ない。取得は Stage 2 の {@code WebPageFetcher} が担当し、このクラスは
 * 受け取ったHTMLの解析だけを行う。jsoupの接続API（{@code connect}）は本番コードで使わない。
 * 名前解決・リダイレクト追跡・取得をライブラリの内側でまとめて行うため、接続先IPを検査する隙間が無いため。
 * この線引きは {@code CodingConventionTest.jsoupConnectIsNotUsedInProductionCode} が機械的に守る。
 */
@Component
@NoArgsConstructor
public class WebPageExtractor {
	/**
	 * 本文へ写さずに除去するタグ。
	 * <p>
	 * ナビゲーション・広告枠・フォームはページの本文ではなく、Markdownメモへ入っても読み返す価値が無い。
	 * {@code script} / {@code style} / {@code noscript} を残すと、コードや宣言文がそのまま本文として出る。
	 */
	private static final List<String> NOISE_TAG_NAMES = List.of("script", "style", "nav", "header", "footer", "aside",
			"form", "noscript", "iframe", "svg");

	private static final String DESCRIPTION_META_SELECTOR = "meta[name=description]";
	private static final String CONTENT_ATTRIBUTE = "content";
	private static final String HEADING_SELECTOR = "h1";
	private static final String IMAGE_SELECTOR = "img";
	private static final String SELECTOR_SYNTAX_ERROR_MESSAGE = "セレクタの書式が正しくありません。article や main のようなCSSセレクタを指定してください。";
	private static final String SELECTOR_NOT_FOUND_MESSAGE = "指定されたセレクタに一致する要素がHTMLにありません。セレクタを見直すか、空欄にしてページ全体を対象にしてください。";
	private static final String EMPTY_CONTENT_MESSAGE = "HTMLから本文を取り出せませんでした。JavaScriptで描画するページの場合は、ブラウザで表示してから「名前を付けて保存」したHTMLを指定してください。";
	private static final String READ_FAILURE_MESSAGE = "HTMLの解析に失敗しました。";

	/**
	 * HTMLを解析し、Markdownへ写す対象を取り出す。
	 * <p>
	 * 文字コードはjsoupへ委ねる（{@code charsetName} にnullを渡す）。BOM、{@code <meta charset>}、
	 * {@code Content-Type} の順で判定され、判定できない場合はUTF-8として扱われる。自前で判定すると、
	 * 判定規則がブラウザと食い違ったときに文字化けの原因を追えなくなる。
	 *
	 * @param htmlStream HTMLの入力ストリーム
	 * @param baseUri    相対URLを絶対化する基準URL。アップロードで基準が無い場合は空文字
	 * @param selector   本文を絞り込むCSSセレクタ。空なら {@code <body>} 全体
	 * @return Markdownへ写す対象
	 * @throws WebInputException      セレクタの書式が不正、一致する要素が無い、または本文が空の場合
	 * @throws WebProcessingException HTMLを読み取れなかった場合
	 */
	public WebPageContent extract(InputStream htmlStream, String baseUri, String selector) {
		return extract(htmlStream, baseUri, selector, null);
	}

	/**
	 * 文字コードを指定してHTMLを解析し、Markdownへ写す対象を取り出す。
	 * <p>
	 * URL取得では応答の {@code Content-Type} が文字コードを示すことがある。HTTPヘッダーの指定は
	 * HTML内の {@code <meta charset>} より優先されるため、指定があればそれを渡す。
	 *
	 * @param htmlStream  HTMLの入力ストリーム
	 * @param baseUri     相対URLを絶対化する基準URL。アップロードで基準が無い場合は空文字
	 * @param selector    本文を絞り込むCSSセレクタ。空なら {@code <body>} 全体
	 * @param charsetName 文字コード名。nullならBOMと {@code <meta charset>} から判定する
	 * @return Markdownへ写す対象
	 * @throws WebInputException      セレクタの書式が不正、一致する要素が無い、または本文が空の場合
	 * @throws WebProcessingException HTMLを読み取れなかった場合
	 */
	public WebPageContent extract(InputStream htmlStream, String baseUri, String selector, String charsetName) {
		Document document = parseDocument(htmlStream, baseUri, charsetName);
		// 除去はセレクタでの絞り込みより先に行う。絞り込み先の内側にもナビゲーションや広告枠は入り得る。
		NOISE_TAG_NAMES.forEach(tagName -> document.select(tagName).remove());
		Element root = resolveRoot(document, selector);
		validateNotEmpty(root);
		return new WebPageContent(resolveTitle(document, root), resolveDescription(document), root);
	}

	/**
	 * HTMLを解析してDOMを組み立てる。
	 *
	 * @param htmlStream  HTMLの入力ストリーム
	 * @param baseUri     相対URLを絶対化する基準URL
	 * @param charsetName 文字コード名。nullならjsoupの判定に任せる
	 * @return 解析済みのDOM
	 * @throws WebProcessingException HTMLを読み取れなかった場合
	 */
	private Document parseDocument(InputStream htmlStream, String baseUri, String charsetName) {
		try {
			return Jsoup.parse(htmlStream, StringUtils.trimToNull(charsetName), StringUtils.defaultString(baseUri));
		} catch (IOException e) {
			throw new WebProcessingException(READ_FAILURE_MESSAGE, e);
		}
	}

	/**
	 * Markdownへ写す本文の根を決める。
	 * <p>
	 * セレクタ指定があり一致が複数ある場合は最初の1件だけを対象にする。複数を連結すると、
	 * 同じセレクタでもページごとに本文の量が変わり、結果を説明できなくなる。
	 *
	 * @param document 解析済みのDOM
	 * @param selector 本文を絞り込むCSSセレクタ
	 * @return 本文の根の要素
	 * @throws WebInputException セレクタの書式が不正、または一致する要素が無い場合
	 */
	private Element resolveRoot(Document document, String selector) {
		if (StringUtils.isBlank(selector)) {
			return Objects.isNull(document.body()) ? document : document.body();
		}
		Elements selected = selectElements(document, selector);
		if (CollectionUtils.isEmpty(selected)) {
			throw new WebInputException(SELECTOR_NOT_FOUND_MESSAGE);
		}
		return selected.first();
	}

	/**
	 * セレクタで要素を絞り込む。
	 *
	 * @param document 解析済みのDOM
	 * @param selector 本文を絞り込むCSSセレクタ
	 * @return 一致した要素
	 * @throws WebInputException セレクタの書式が不正な場合
	 */
	private Elements selectElements(Document document, String selector) {
		try {
			return document.select(selector);
		} catch (SelectorParseException e) {
			// jsoupの書式エラーメッセージはそのまま画面へ出さない。内部表現が利用者の指定文字列と対応しない。
			throw new WebInputException(SELECTOR_SYNTAX_ERROR_MESSAGE, e);
		}
	}

	/**
	 * 本文が空でないことを確認する。
	 * <p>
	 * 画像しかないページもあるため、文字が無くても画像があれば取り込み対象として扱う。
	 *
	 * @param root 本文の根の要素
	 * @throws WebInputException 文字も画像も無い場合
	 */
	private void validateNotEmpty(Element root) {
		if (StringUtils.isBlank(root.text()) && CollectionUtils.isEmpty(root.select(IMAGE_SELECTOR))) {
			throw new WebInputException(EMPTY_CONTENT_MESSAGE);
		}
	}

	/**
	 * ページタイトルを決める。
	 * <p>
	 * {@code <title>} が空のHTML断片（DevToolsからのコピーなど）も入力になり得るため、
	 * 最初の {@code <h1>} を代わりに使う。どちらも無い場合は空文字を返し、見出しの決定は
	 * 呼び出し元（取得元のファイル名やURLを使う）へ委ねる。
	 *
	 * @param document 解析済みのDOM
	 * @param root     本文の根の要素
	 * @return ページタイトル。決められない場合は空文字
	 */
	private String resolveTitle(Document document, Element root) {
		String title = document.title();
		if (StringUtils.isNotBlank(title)) {
			return StringUtils.normalizeSpace(title);
		}
		Element heading = root.selectFirst(HEADING_SELECTOR);
		return Objects.isNull(heading) ? StringUtils.EMPTY : StringUtils.normalizeSpace(heading.text());
	}

	/**
	 * ページの説明（{@code <meta name="description">}）を取り出す。
	 *
	 * @param document 解析済みのDOM
	 * @return ページの説明。無ければ空文字
	 */
	private String resolveDescription(Document document) {
		Element meta = document.selectFirst(DESCRIPTION_META_SELECTOR);
		return Objects.isNull(meta) ? StringUtils.EMPTY : StringUtils.normalizeSpace(meta.attr(CONTENT_ATTRIBUTE));
	}
}
