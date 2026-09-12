package com.clip.ghost.markdowncontent.logic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities.EscapeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.clip.ghost.markdowncontent.config.MarkdownPdfProperties;
import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;
import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;

/**
 * sanitize済みHTMLをPDFへ描画する内部ロジック。
 * <p>
 * openhtmltopdf（HTML/CSSレンダラー）への依存はこのクラスに閉じ込め、Service層はbyte配列だけを扱う。
 * レイアウトはクラスパスの {@code markdown-pdf.css} が持ち、Javaコード側にスタイルを書かない。
 * <p>
 * 日本語フォントを埋め込まないとPDFBoxの標準フォントでは描画できないため、既定で同梱のNoto Sans JPを登録する。
 * 太字フォントが設定されていない場合は、太字の指定にも本文フォントを登録する。フォントが見つからず
 * 描画自体が失敗するより、字形が太くならないだけの方が実害が小さい。
 * <p>
 * 外部リソース（http/https/file）は取得しない。Markdown本文に外部URLの画像が書かれていても、
 * サーバーからその URL へ取りに行くとSSRFの入口になるため、data URI以外は解決しない。
 */
@Component
@RequiredArgsConstructor
public class MarkdownPdfRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownPdfRenderer.class);
	private static final String BUNDLED_FONT_RESOURCE = "fonts/NotoSansJP-Regular.ttf";
	private static final String STYLE_RESOURCE = "markdown-pdf.css";
	private static final String FONT_FAMILY = "Noto Sans JP";
	private static final int NORMAL_FONT_WEIGHT = 400;
	private static final int BOLD_FONT_WEIGHT = 700;
	private static final String DATA_URI_SCHEME = "data:";
	private static final String PRODUCER = "Ghost-PDF5";
	// baseUriを空にして、相対URIの解決先を持たせない。
	private static final String BASE_URI = "";
	// CSSはCDATAで包む。XMLとして解析されるため、包まないとCSS中の記号やコメントが整形式エラーになる。
	private static final String XHTML_FORMAT = """
			<?xml version="1.0" encoding="UTF-8"?>
			<html xmlns="http://www.w3.org/1999/xhtml">
			<head><title>%s</title><style type="text/css"><![CDATA[
			%s
			]]></style></head>
			<body>%s</body>
			</html>""";

	private final MarkdownPdfProperties properties;

	/**
	 * sanitize済みHTMLをPDFのbyte配列へ変換する。
	 *
	 * @param title       PDFのタイトル（文書プロパティに入る）
	 * @param sanitizedHtml sanitize済みHTML本文
	 * @return PDFのbyte配列
	 * @throws MarkdownPdfException PDFの描画に失敗した場合
	 */
	public byte[] render(String title, String sanitizedHtml) {
		String xhtml = buildXhtml(title, sanitizedHtml);
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			// fast modeは1.1系で既定になったため、明示的な有効化（非推奨API）は呼ばない。
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.withProducer(PRODUCER);
			builder.withHtmlContent(xhtml, BASE_URI);
			builder.useUriResolver(this::resolveUri);
			registerFonts(builder);
			builder.toStream(outputStream);
			builder.run();
			return outputStream.toByteArray();
		} catch (IOException | RuntimeException e) {
			throw new MarkdownPdfException("MarkdownからのPDF描画に失敗しました。", e);
		}
	}

	/**
	 * PDFへ渡すXHTMLを組み立てる。
	 * <p>
	 * openhtmltopdfはXMLとして解析するため、sanitize済みHTMLをXHTMLとして出力し直す（{@code <br>} → {@code <br />}）。
	 * CSSは {@code <style>} へそのまま埋め込む。外部CSSにするとレンダラーがURIを解決しに行くため、埋め込みにする。
	 *
	 * @param title         PDFのタイトル
	 * @param sanitizedHtml sanitize済みHTML本文
	 * @return レンダラーへ渡すXHTML
	 */
	private String buildXhtml(String title, String sanitizedHtml) {
		Document bodyDocument = Jsoup.parseBodyFragment(StringUtils.defaultString(sanitizedHtml));
		bodyDocument.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(EscapeMode.xhtml)
				.charset(StandardCharsets.UTF_8).prettyPrint(false);
		String escapedTitle = org.jsoup.nodes.Entities.escape(StringUtils.defaultString(title));
		return XHTML_FORMAT.formatted(escapedTitle, readStyle(), bodyDocument.body().html());
	}

	/**
	 * クラスパスからPDF用CSSを読み込む。
	 *
	 * @return PDF用CSS
	 * @throws MarkdownPdfException CSSを読み込めない場合
	 */
	private String readStyle() {
		try (InputStream inputStream = new ClassPathResource(STYLE_RESOURCE).getInputStream()) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new MarkdownPdfException("PDF出力用のスタイルを読み込めませんでした。", e);
		}
	}

	/**
	 * 本文用と太字用のフォントを登録する。
	 *
	 * @param builder PDFレンダラーのbuilder
	 */
	private void registerFonts(PdfRendererBuilder builder) {
		FSSupplier<InputStream> normalFont = buildFontSupplier(properties.getFontPath());
		// 太字フォント未指定なら本文フォントを太字としても登録する。登録が無いとその指定だけ描画できない。
		FSSupplier<InputStream> boldFont = StringUtils.isBlank(properties.getBoldFontPath()) ? normalFont
				: buildFontSupplier(properties.getBoldFontPath());
		builder.useFont(normalFont, FONT_FAMILY, NORMAL_FONT_WEIGHT, FontStyle.NORMAL, true);
		builder.useFont(boldFont, FONT_FAMILY, BOLD_FONT_WEIGHT, FontStyle.NORMAL, true);
	}

	/**
	 * フォントの読み込み元を組み立てる。
	 * <p>
	 * レンダラーはフォントを複数回読み込むことがあるため、呼ばれるたびに新しいストリームを返す。
	 *
	 * @param fontPath 設定されたフォントファイルパス。未設定なら同梱フォントを使う
	 * @return フォントの読み込み元
	 */
	private FSSupplier<InputStream> buildFontSupplier(String fontPath) {
		if (StringUtils.isBlank(fontPath)) {
			return () -> openBundledFont();
		}
		Path path = Paths.get(StringUtils.trim(fontPath)).toAbsolutePath().normalize();
		if (!Files.isReadable(path)) {
			// 設定ミスを黙って同梱フォントで補うと、出力が指定と違う理由に気付けない。
			LOGGER.warn("設定された日本語フォントを読み込めません。fileName={}", path.getFileName());
			throw new MarkdownPdfException("設定された日本語フォントを読み込めませんでした。ghost.markdown.pdf の設定を確認してください。");
		}
		return () -> openFont(path);
	}

	/**
	 * 同梱フォントを開く。
	 *
	 * @return 同梱フォントの読み込みストリーム
	 * @throws MarkdownPdfException 同梱フォントを読み込めない場合
	 */
	private InputStream openBundledFont() {
		try {
			return new ClassPathResource(BUNDLED_FONT_RESOURCE).getInputStream();
		} catch (IOException e) {
			throw new MarkdownPdfException("同梱の日本語フォントを読み込めませんでした。", e);
		}
	}

	/**
	 * 設定されたフォントファイルを開く。
	 *
	 * @param path フォントファイルパス
	 * @return フォントの読み込みストリーム
	 */
	private InputStream openFont(Path path) {
		try {
			return Files.newInputStream(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * レンダラーからのURI解決要求を判定する。
	 * <p>
	 * data URIだけを許可し、http/https/fileは解決しない。解決しないURIはnullを返し、レンダラー側で無視させる。
	 * 外部URLを取得すると、サーバーが任意のURLへアクセスする入口（SSRF）になるため。
	 *
	 * @param baseUri ベースURI
	 * @param uri     解決対象URI
	 * @return 許可する場合はURI、許可しない場合はnull
	 */
	private String resolveUri(String baseUri, String uri) {
		if (Strings.CI.startsWith(StringUtils.trim(uri), DATA_URI_SCHEME)) {
			return uri;
		}
		LOGGER.debug("外部リソースはPDFへ取り込みません。");
		return null;
	}
}
