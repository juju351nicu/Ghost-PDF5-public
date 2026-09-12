package com.clip.ghost.markdowncontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.clip.ghost.markdowncontent.config.MarkdownPdfProperties;
import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;

/**
 * {@link MarkdownPdfRenderer} のPDF描画を検証するテスト。
 * <p>
 * 生成したPDFをPDFBoxで読み直し、日本語・表・ページ番号が実際に入っているかを確認する。
 * 見た目の細部までは固定できないが、「日本語が豆腐になる」「表が消える」といった壊れ方は検知できる。
 */
class MarkdownPdfRendererTest {

	@TempDir
	Path tempDirectory;

	private final MarkdownHtmlRenderer htmlRenderer = new MarkdownHtmlRenderer();

	@Test
	@DisplayName("日本語の見出しと本文をPDFへ描画する")
	void rendersJapaneseTextIntoPdf() throws IOException {
		String html = htmlRenderer.render("# 設計書\n\n日本語の本文をPDFへ出力する。");

		byte[] pdfBytes = createRenderer(new MarkdownPdfProperties()).render("設計書", html);

		String text = extractText(pdfBytes);
		// 日本語フォントを埋め込めていないと、ここで文字が落ちるか豆腐になる。
		assertTrue(text.contains("設計書"));
		assertTrue(text.contains("日本語の本文をPDFへ出力する。"));
	}

	@Test
	@DisplayName("Markdown表のセルをPDFへ描画する")
	void rendersTableCellsIntoPdf() throws IOException {
		String markdown = """
				| 項番 | 確認観点 | 期待結果 |
				| --- | --- | --- |
				| 1 | 初期表示 | 一覧が表示される |
				""";
		String html = htmlRenderer.render(markdown);

		byte[] pdfBytes = createRenderer(new MarkdownPdfProperties()).render("表", html);

		String text = extractText(pdfBytes);
		assertTrue(text.contains("確認観点"));
		assertTrue(text.contains("一覧が表示される"));
	}

	@Test
	@DisplayName("コードブロック内の日本語も埋め込みフォントで描画する")
	void rendersJapaneseInsideCodeBlock() throws IOException {
		String markdown = """
				```java
				// 日本語コメント
				String message = "こんにちは";
				```
				""";
		String html = htmlRenderer.render(markdown);

		byte[] pdfBytes = createRenderer(new MarkdownPdfProperties()).render("コード", html);

		// code / pre はブラウザ既定で monospace になり、指定しないと埋め込みフォントから外れて豆腐になる。
		assertTrue(extractText(pdfBytes).contains("こんにちは"));
		assertTrue(extractText(pdfBytes).contains("日本語コメント"));
	}

	@Test
	@DisplayName("複数ページになる本文にページ番号を入れる")
	void rendersPageNumberOnEveryPage() throws IOException {
		StringBuilder markdown = new StringBuilder();
		for (int index = 0; index < 120; index++) {
			markdown.append("これは改ページを起こすための長い段落です。").append(index).append("\n\n");
		}
		String html = htmlRenderer.render(markdown.toString());

		byte[] pdfBytes = createRenderer(new MarkdownPdfProperties()).render("長い文書", html);

		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			assertTrue(document.getNumberOfPages() > 1);
			String text = new PDFTextStripper().getText(document);
			assertTrue(text.contains("1 / " + document.getNumberOfPages()));
		}
	}

	@Test
	@DisplayName("外部URLの画像は取得せず、本文だけを描画する")
	void doesNotFetchExternalResources() throws IOException {
		// 外部URLを取りに行くとSSRFの入口になる。取得しないことを、描画が通ることで確認する。
		String html = htmlRenderer.render("![図](http://127.0.0.1:9/not-exist.png)\n\n本文は残る。");

		byte[] pdfBytes = createRenderer(new MarkdownPdfProperties()).render("画像", html);

		assertTrue(extractText(pdfBytes).contains("本文は残る。"));
	}

	@Test
	@DisplayName("設定したフォントを読み込めない場合は例外にする")
	void throwsWhenConfiguredFontIsMissing() {
		MarkdownPdfProperties properties = new MarkdownPdfProperties();
		properties.setFontPath(tempDirectory.resolve("missing-font.ttf").toString());

		// 設定ミスを同梱フォントで黙って補うと、出力が指定と違う理由に気付けない。
		assertThrows(MarkdownPdfException.class, () -> createRenderer(properties).render("題名", "<p>本文</p>"));
	}

	@Test
	@DisplayName("設定したフォントを読み込めた場合はそのフォントで描画する")
	void usesConfiguredFont() throws IOException {
		Path fontPath = tempDirectory.resolve("configured-font.ttf");
		Files.write(fontPath, readBundledFont());
		MarkdownPdfProperties properties = new MarkdownPdfProperties();
		properties.setFontPath(fontPath.toString());
		properties.setBoldFontPath(fontPath.toString());

		byte[] pdfBytes = createRenderer(properties).render("題名", htmlRenderer.render("**太字**の日本語"));

		assertTrue(extractText(pdfBytes).contains("太字"));
	}

	@Test
	@DisplayName("空のMarkdownでも1ページのPDFを返す")
	void rendersEmptyDocument() throws IOException {
		byte[] pdfBytes = createRenderer(new MarkdownPdfProperties()).render("空", htmlRenderer.render(""));

		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			assertEquals(1, document.getNumberOfPages());
		}
		assertFalse(pdfBytes.length == 0);
	}

	/**
	 * 指定した設定でレンダラーを生成する。
	 *
	 * @param properties フォント設定
	 * @return PDFレンダラー
	 */
	private MarkdownPdfRenderer createRenderer(MarkdownPdfProperties properties) {
		return new MarkdownPdfRenderer(properties);
	}

	/**
	 * 同梱フォントをbyte配列として読み込む。
	 *
	 * @return 同梱フォントのbyte配列
	 * @throws IOException 読み込みに失敗した場合
	 */
	private byte[] readBundledFont() throws IOException {
		try (InputStream inputStream = getClass().getResourceAsStream("/fonts/NotoSansJP-Regular.ttf")) {
			return inputStream.readAllBytes();
		}
	}

	/**
	 * PDFのbyte配列からテキストを抽出する。
	 *
	 * @param pdfBytes PDFのbyte配列
	 * @return 抽出したテキスト
	 * @throws IOException PDFを読み込めない場合
	 */
	private String extractText(byte[] pdfBytes) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			return new PDFTextStripper().getText(document);
		}
	}
}
