package com.clip.ghost.pdfcontent.exception;

import lombok.Getter;

/**
 * 指定された解像度（DPI）が設定の上限を超えた場合に送出する例外。
 * <p>
 * 描画に必要なメモリは解像度の2乗で増えるため、利用者の指定をそのまま渡すと1ページでもヒープを使い切る。
 * 上限で黙って丸めず400として返す。ZIPを返すAPIには {@code messageList} の経路が無く、
 * 丸めたことを利用者へ伝える手段が無いまま「指定と違う解像度の画像」が届いてしまうため。
 * <p>
 * {@code IllegalArgumentException} を継承しない。PDF処理ロジックの
 * {@code catch (IllegalArgumentException | ...)} に拾われて {@link PdfProcessingException} へ化けると、
 * 入力の誤りが500として見えてしまうため。
 */
@Getter
public class PdfRenderDpiException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 指定された解像度（DPI）。 */
	private final int requestedDpi;

	/** 設定された解像度（DPI）の上限。 */
	private final int maxDpi;

	/**
	 * 指定された解像度と上限を指定して例外を生成する。
	 * <p>
	 * 解像度は文書の内容ではないため、メッセージ・ログ・レスポンスに含めても情報漏洩にならない。
	 *
	 * @param requestedDpi 指定された解像度（DPI）
	 * @param maxDpi       設定された解像度（DPI）の上限
	 */
	public PdfRenderDpiException(int requestedDpi, int maxDpi) {
		super("指定された解像度が上限を超えています。requestedDpi=" + requestedDpi + ", maxDpi=" + maxDpi);
		this.requestedDpi = requestedDpi;
		this.maxDpi = maxDpi;
	}
}
