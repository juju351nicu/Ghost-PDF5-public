package com.clip.ghost.pdfcontent.exception;

/**
 * 分割範囲がPDFの総ページ数と噛み合わない場合に送出する例外。
 * <p>
 * 形式や重なりの検証はannotation validationが担当し、この例外はPDFを開いて総ページ数が分かった後の
 * 突き合わせだけを扱う。利用者が入力を直せるエラーのため400として返し、
 * {@code PdfProcessingException}（500）とは分ける。
 */
public class PdfSplitRangeException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 指定された分割範囲のうち、PDFの範囲外だったもの。 */
	private final String outsideRangeText;

	/** PDFの総ページ数。 */
	private final int totalPages;

	/**
	 * 範囲外だった分割範囲と総ページ数を指定して例外を生成する。
	 *
	 * @param outsideRangeText PDFの範囲外だった分割範囲
	 * @param totalPages       PDFの総ページ数
	 */
	public PdfSplitRangeException(String outsideRangeText, int totalPages) {
		super("分割範囲がPDFのページ範囲外です。range=" + outsideRangeText + ", totalPages=" + totalPages);
		this.outsideRangeText = outsideRangeText;
		this.totalPages = totalPages;
	}

	/**
	 * PDFの範囲外だった分割範囲を返す。
	 *
	 * @return 範囲外だった分割範囲
	 */
	public String getOutsideRangeText() {
		return outsideRangeText;
	}

	/**
	 * PDFの総ページ数を返す。
	 *
	 * @return 総ページ数
	 */
	public int getTotalPages() {
		return totalPages;
	}
}
