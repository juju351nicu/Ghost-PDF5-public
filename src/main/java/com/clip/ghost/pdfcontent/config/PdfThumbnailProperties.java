package com.clip.ghost.pdfcontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * ページ選択用サムネイル（{@code POST /thumbnailsPdf}）の解像度とページ数上限の設定。
 * <p>
 * サムネイルは1リクエストで全ページ分を返すため、レスポンスサイズがページ数に比例して増える。
 * 上限と解像度を設定で持ち、運用側で絞れるようにする。
 * <p>
 * 設定キーは {@code ghost.ocr.*}（画像→Markdown変換の設定）へ混ぜず、{@code ghost.pdf.thumbnail.*} に分ける。
 * サムネイルは外部AIを使わない画面表示用の機能で、OCRのコストガードとは目的が別のため。
 */
@ConfigurationProperties(prefix = "ghost.pdf.thumbnail")
@Getter
@Setter
public class PdfThumbnailProperties {
	/**
	 * サムネイルのレンダリング解像度（DPI）。
	 * <p>
	 * 既定の40dpiはA4で約331x468px。PNG 1ページが20〜40 KB程度、base64化で約1.33倍になるため、
	 * {@code maxPages} の100ページでレスポンスは概ね3〜5 MBに収まる。
	 */
	private int dpi = 40;

	/** サムネイルを返すページ数の上限。超過時は1ページも描画せず400で拒否する。 */
	private int maxPages = 100;
}
