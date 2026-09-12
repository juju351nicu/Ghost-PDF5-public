package com.clip.ghost.pdfcontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像PDFのMarkdown下書き（{@code mode=AUTO} / {@code mode=VISION}）で使うページ上限とレンダリング設定。
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
	/**
	 * 画像変換にかけるページ数の上限。超過時は1ページも変換せず400で拒否する。
	 * <p>
	 * 数える対象はモードで変わる。AUTOは文字レイヤーが無いページ数、VISIONは総ページ数がそのまま対象になる。
	 * 既定値はVISION追加後も20のまま据え置く。この値は実用上の目安ではなく外部AIへの課金を止めるための歯止めで、
	 * 上げるとAUTOのコストガードも同時に緩むため。長い設計書をVISIONで変換する場合は、運用側で
	 * {@code ghost.ocr.pdf.max-pages} を明示的に引き上げる。
	 */
	private int maxPages = 20;

	/** 変換対象ページを画像化する解像度（DPI）。 */
	private int renderDpi = 200;
}
