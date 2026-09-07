package com.clip.ghost.pdfcontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像PDFのMarkdown下書き（{@code mode=AUTO}）で使うページ上限とレンダリング設定。
 * <p>
 * 設定キーは運用者から見てOCR設定が1箇所に集まるよう {@code ghost.ocr.pdf} にそろえるが、内容はPDF固有のため
 * クラスは {@code pdfcontent} 側に置く。{@code imagecontent} 側へPDFの概念を持ち込むと、
 * 現在の {@code pdfcontent} → {@code imagecontent} という一方向の依存が逆流するため。
 * provider選択などOCR共通の設定は {@link com.clip.ghost.imagecontent.config.ImageOcrProperties} が持つ。
 */
@ConfigurationProperties(prefix = "ghost.ocr.pdf")
@Getter
@Setter
public class PdfOcrProperties {
	/** AUTOモードで画像変換にかけるページ数の上限。超過時は1ページも変換せず400で拒否する。 */
	private int maxPages = 20;

	/** 文字レイヤーが無いページを画像化する解像度（DPI）。 */
	private int renderDpi = 200;
}
