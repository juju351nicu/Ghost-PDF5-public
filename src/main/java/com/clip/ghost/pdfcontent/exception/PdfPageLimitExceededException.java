package com.clip.ghost.pdfcontent.exception;

import lombok.Getter;

/**
 * AUTOモードで画像変換にかけるページ数が上限を超えた場合に送出する例外。
 * <p>
 * 外部AI（vision）の課金が発生する前に処理を止めるためのコストガードで、HTTP 400として扱う。
 * <p>
 * この例外は {@code IllegalStateException} を継承しない。PDF処理ロジックの
 * {@code catch (IllegalStateException | IOException)} に拾われると {@link PdfProcessingException} へ化け、
 * コストガードが500として見えてしまうため。
 */
@Getter
public class PdfPageLimitExceededException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 画像変換の対象になったページ数。 */
	private final int targetPageCount;

	/** 設定されたページ数の上限。 */
	private final int maxPages;

	/**
	 * 変換対象ページ数と上限を指定して例外を生成する。
	 * <p>
	 * ページ数は文書の内容ではないため、メッセージ・ログ・レスポンスに含めても情報漏洩にならない。
	 *
	 * @param targetPageCount 画像変換の対象になったページ数
	 * @param maxPages        設定されたページ数の上限
	 */
	public PdfPageLimitExceededException(int targetPageCount, int maxPages) {
		super("変換対象ページ数が上限を超えています。targetPageCount=" + targetPageCount + ", maxPages=" + maxPages);
		this.targetPageCount = targetPageCount;
		this.maxPages = maxPages;
	}
}
