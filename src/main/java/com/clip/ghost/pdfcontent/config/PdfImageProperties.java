package com.clip.ghost.pdfcontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * PDFページ画像化（{@code POST /imagesPdf}）の解像度とページ数上限の設定。
 * <p>
 * 設定キーを {@code ghost.pdf.thumbnail.*} と共有しない。サムネイルは画面表示用の低解像度で、
 * こちらは利用者がダウンロードして使う画像のため、必要な解像度もページ数上限も別物になる。
 * 共有すると、片方を上げたときにもう片方のレスポンスサイズが跳ね上がる。
 */
@ConfigurationProperties(prefix = "ghost.pdf.image")
@Getter
@Setter
public class PdfImageProperties {
	/** 解像度（DPI）を指定しなかった場合の既定値。150dpiはA4で約1240x1754pxで、画面確認と印刷の中間。 */
	private int defaultDpi = 150;

	/**
	 * 指定できる解像度（DPI）の上限。
	 * <p>
	 * 描画に必要なメモリは解像度の2乗で増える。600dpiのA4は約35 MピクセルでRGB 4バイト換算140 MB程度になるため、
	 * 1ページでもヒープを圧迫する。利用者の指定をそのまま信じない。
	 */
	private int maxDpi = 600;

	/** 画像化するページ数の上限。超過時は1ページも描画せず400で拒否する。 */
	private int maxPages = 200;
}
