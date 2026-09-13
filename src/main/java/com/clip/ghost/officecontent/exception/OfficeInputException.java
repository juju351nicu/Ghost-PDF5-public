package com.clip.ghost.officecontent.exception;

import lombok.Getter;

/**
 * Office文書として扱えないファイルを受け取った場合に送出する例外。
 * <p>
 * 対象外の拡張子、旧形式（{@code .doc} / {@code .xls} / {@code .ppt}）、壊れたOOXMLがここに来る。
 * いずれも利用者が別のファイルを用意すれば通るため400として返し、
 * {@link OfficeProcessingException}（500）とは分ける。
 */
@Getter
public class OfficeInputException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 読み取れなかったファイルの名前。 */
	private final String fileName;

	/**
	 * 読み取れなかったファイル名を指定して例外を生成する。
	 *
	 * @param fileName 読み取れなかったファイルの名前
	 */
	public OfficeInputException(String fileName) {
		super("Office文書として読み込めないファイルです。fileName=" + fileName);
		this.fileName = fileName;
	}

	/**
	 * 読み取れなかったファイル名と原因を指定して例外を生成する。
	 *
	 * @param fileName 読み取れなかったファイルの名前
	 * @param cause    原因となった例外
	 */
	public OfficeInputException(String fileName, Throwable cause) {
		super("Office文書として読み込めないファイルです。fileName=" + fileName, cause);
		this.fileName = fileName;
	}
}
