package com.clip.ghost.markdowncontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * MarkdownからPDFを出力するときのフォント設定。
 * <p>
 * PDFBoxの標準14フォントは日本語を描画できないため、日本語フォントの埋め込みが必須になる。
 * 既定では同梱のNoto Sans JP（SIL OFL 1.1）を使い、社内指定フォントなどへ差し替えたい場合だけ設定でパスを与える。
 * <p>
 * 太字フォントを指定しない場合、太字指定の箇所も通常フォントで描画する。字形は太くならないが、
 * フォントが見つからずに豆腐になるより読める状態を優先する。
 */
@ConfigurationProperties(prefix = "ghost.markdown.pdf")
@Getter
@Setter
public class MarkdownPdfProperties {
	/** 本文に使うTrueTypeフォントのファイルパス。未指定なら同梱フォントを使う。 */
	private String fontPath;

	/** 太字に使うTrueTypeフォントのファイルパス。未指定なら本文フォントで代用する。 */
	private String boldFontPath;
}
