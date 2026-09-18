package com.clip.ghost.webcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.clip.ghost.webcontent.exception.WebInputException;

/**
 * {@link WebPageExtractor} の不要要素の除去、セレクタ、タイトル・説明の取り出しを検証するテスト。
 * <p>
 * フィクスチャHTMLはすべて自作で、実在サイトのHTMLは持ち込まない。
 */
class WebPageExtractorTest {
	private static final Path FIXTURE_DIRECTORY = Paths.get("src/test/resources/web");
	private static final String SAMPLE_ARTICLE = "sample-article.html";
	private static final String EMPTY_BODY = "empty-body.html";
	private static final String SHIFT_JIS_ARTICLE = "shift-jis-article.html";
	private static final String BASE_URI = "https://example.test/docs/";

	private final WebPageExtractor webPageExtractor = new WebPageExtractor();

	@Test
	@DisplayName("titleとmeta descriptionを取り出す")
	void extractReadsTitleAndDescription() throws IOException {
		WebPageContent content = extractFixture(SAMPLE_ARTICLE, StringUtils.EMPTY);

		assertEquals("設計メモのサンプルページ", content.title());
		assertEquals("取り込みテスト用のサンプル説明", content.description());
	}

	@ParameterizedTest
	@ValueSource(strings = { "サイト共通ヘッダー", "トップへ", "関連記事の一覧", "サイト共通フッター", "JavaScriptを有効にしてください。" })
	@DisplayName("nav / header / footer / aside / noscript の内容は本文に残らない")
	void extractRemovesNoiseElements(String noiseText) throws IOException {
		WebPageContent content = extractFixture(SAMPLE_ARTICLE, StringUtils.EMPTY);

		assertFalse(Strings.CS.contains(content.root().text(), noiseText));
	}

	@Test
	@DisplayName("scriptとstyleの中身は本文に残らない")
	void extractRemovesScriptAndStyle() throws IOException {
		WebPageContent content = extractFixture(SAMPLE_ARTICLE, StringUtils.EMPTY);

		String text = content.root().text();
		assertFalse(Strings.CS.contains(text, "var tracking"));
		assertFalse(Strings.CS.contains(text, "color: red"));
	}

	@Test
	@DisplayName("セレクタ指定でその要素配下だけを本文にする")
	void extractNarrowsContentBySelector() throws IOException {
		WebPageContent content = extractFixture(SAMPLE_ARTICLE, "table");

		assertEquals("table", content.root().normalName());
		assertTrue(Strings.CS.contains(content.root().text(), "形式"));
		assertFalse(Strings.CS.contains(content.root().text(), "設計メモ"));
	}

	@Test
	@DisplayName("セレクタに一致する要素が無い場合はWebInputExceptionになる")
	void extractThrowsWhenSelectorMatchesNothing() throws IOException {
		byte[] html = readFixture(SAMPLE_ARTICLE);

		WebInputException exception = assertThrows(WebInputException.class,
				() -> extract(html, "section.not-exists"));

		assertTrue(Strings.CS.contains(exception.getDisplayMessage(), "セレクタ"));
	}

	@Test
	@DisplayName("セレクタの書式が不正な場合はWebInputExceptionになる")
	void extractThrowsWhenSelectorIsInvalid() throws IOException {
		byte[] html = readFixture(SAMPLE_ARTICLE);

		WebInputException exception = assertThrows(WebInputException.class, () -> extract(html, "div["));

		assertTrue(Strings.CS.contains(exception.getDisplayMessage(), "書式"));
	}

	@Test
	@DisplayName("除去後に本文が残らないHTMLでも例外にせず、空の本文として返す")
	void extractReturnsEmptyContentWhenNothingRemains() throws IOException {
		WebPageContent content = extractFixture(EMPTY_BODY, StringUtils.EMPTY);

		// 空を許さないかは「何を出力するか」で決まるため、判定はService層が行う。
		assertTrue(StringUtils.isBlank(content.root().text()));
		assertEquals("本文が無いページ", content.title());
	}

	@Test
	@DisplayName("ボタンのラベルは本文に残らない")
	void extractRemovesButtonLabels() {
		byte[] html = "<article><p>本文</p><button>ページをコピー</button></article>".getBytes(StandardCharsets.UTF_8);

		WebPageContent content = extract(html, StringUtils.EMPTY);

		// 操作のためのラベルであって読み返す対象ではない。本文へ混ざると段落として残ってしまう。
		assertFalse(Strings.CS.contains(content.root().text(), "ページをコピー"));
		assertTrue(Strings.CS.contains(content.root().text(), "本文"));
	}

	@Test
	@DisplayName("titleが無いHTML断片では最初のh1をタイトルにする")
	void extractFallsBackToFirstHeadingWhenTitleIsMissing() {
		byte[] html = "<div><h1>断片の見出し</h1><p>本文</p></div>".getBytes(StandardCharsets.UTF_8);

		WebPageContent content = extract(html, StringUtils.EMPTY);

		assertEquals("断片の見出し", content.title());
	}

	@Test
	@DisplayName("Shift_JISのHTMLでも日本語が化けない")
	void extractDetectsCharsetFromMetaTag() throws IOException {
		WebPageContent content = extractFixture(SHIFT_JIS_ARTICLE, StringUtils.EMPTY);

		assertEquals("文字コード確認ページ", content.title());
		assertTrue(Strings.CS.contains(content.root().text(), "日本語の段落が化けないことを確認する。"));
	}

	/**
	 * フィクスチャHTMLを解析する。
	 *
	 * @param fixtureName フィクスチャのファイル名
	 * @param selector    本文を絞り込むCSSセレクタ
	 * @return 解析結果
	 * @throws IOException フィクスチャを読めない場合
	 */
	private WebPageContent extractFixture(String fixtureName, String selector) throws IOException {
		return extract(readFixture(fixtureName), selector);
	}

	/**
	 * HTMLのバイト列を解析する。
	 *
	 * @param html     HTMLのバイト列
	 * @param selector 本文を絞り込むCSSセレクタ
	 * @return 解析結果
	 */
	private WebPageContent extract(byte[] html, String selector) {
		try (InputStream inputStream = new ByteArrayInputStream(html)) {
			return webPageExtractor.extract(inputStream, BASE_URI, selector);
		} catch (IOException e) {
			throw new IllegalStateException("テスト用HTMLの読み取りに失敗しました。", e);
		}
	}

	/**
	 * フィクスチャHTMLを読み込む。
	 *
	 * @param fixtureName フィクスチャのファイル名
	 * @return HTMLのバイト列
	 * @throws IOException フィクスチャを読めない場合
	 */
	private byte[] readFixture(String fixtureName) throws IOException {
		return Files.readAllBytes(FIXTURE_DIRECTORY.resolve(fixtureName));
	}
}
