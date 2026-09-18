package com.clip.ghost.common.utils;

import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * パス文字列とファイル名を扱うユーティリティクラス。
 * <p>
 * ファイル実体へのアクセスを伴う処理は {@link FileOperationUtils} または {@link FileInfoUtils} に分ける。
 */
public final class PathUtils {
	/** PDFの拡張子。判定に使うだけなので、PDF機能のpackageへは依存しない。 */
	private static final String FILE_PDF = "pdf";

	/** Markdownの拡張子。 */
	private static final String FILE_MARKDOWN = "md";
	/** ファイル名として扱えない文字。パス区切り、ドライブ指定、Windowsで使えない記号、制御文字。 */
	private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]+");

	private PathUtils() {
	}

	/**
	 * ファイルパスから親ディレクトリパスを取得する。
	 *
	 * @param filePath ファイルパス
	 * @return 親ディレクトリパス。親ディレクトリが取得できない場合はnull
	 */
	public static String extractDirectoryPath(String filePath) {
		String directoryPath = FilenameUtils.getFullPathNoEndSeparator(filePath);
		return StringUtils.isEmpty(directoryPath) ? null : directoryPath;
	}

	/**
	 * ファイル名から拡張子を取得する。
	 *
	 * @param fileName ファイル名
	 * @return 拡張子。拡張子がない場合は空文字
	 */
	public static String getExtension(String fileName) {
		return StringUtils.defaultString(FilenameUtils.getExtension(fileName));
	}

	/**
	 * ファイルパスからファイル名を取得する。
	 * <p>
	 * 旧実装からの既存仕様を維持するため、区切り文字が含まれない場合は入力値ではなくnullを返す。
	 *
	 * @param filePath ファイルパス
	 * @return ファイル名。区切り文字が含まれない場合はnull
	 */
	public static String extractFileName(String filePath) {
		if (StringUtils.isEmpty(filePath) || FilenameUtils.indexOfLastSeparator(filePath) == -1) {
			return null;
		}
		return FilenameUtils.getName(filePath);
	}

	/**
	 * ファイル名から拡張子を除いた文字列を取得する。
	 *
	 * @param fileName ファイル名
	 * @return 拡張子を除いたファイル名。null、空文字、拡張子がない場合は入力値
	 */
	public static String removeExtension(String fileName) {
		return FilenameUtils.removeExtension(fileName);
	}

	/**
	 * フォルダパスとファイル名を連結する。
	 * <p>
	 * 片方だけ未指定の場合は、指定されている値をそのまま返す。
	 *
	 * @param folderPath フォルダパス
	 * @param fileName   ファイル名
	 * @return 連結後のパス文字列。両方未指定の場合は空文字
	 */
	public static String mergePathAndFileName(String folderPath, String fileName) {
		if (StringUtils.isEmpty(folderPath)) {
			return StringUtils.defaultString(fileName);
		}
		if (StringUtils.isEmpty(fileName)) {
			return folderPath;
		}
		return Paths.get(folderPath).resolve(fileName).toString();
	}

	/**
	 * ファイル名がPDF拡張子を持つか判定する。
	 *
	 * @param fileName ファイル名
	 * @return PDF拡張子の場合true
	 */
	public static boolean isPdfFileName(String fileName) {
		return Strings.CI.equals(FILE_PDF, FilenameUtils.getExtension(fileName));
	}

	/**
	 * ファイル名がMarkdown拡張子を持つか判定する。
	 *
	 * @param fileName ファイル名
	 * @return Markdown拡張子の場合true
	 */
	public static boolean isMarkdownFileName(String fileName) {
		return Strings.CI.equals(FILE_MARKDOWN, FilenameUtils.getExtension(fileName));
	}

	/**
	 * 保存やダウンロードに使えるよう、ファイル名から扱えない文字を置き換える。
	 * <p>
	 * ディレクトリ区切りやドライブ指定、制御文字を残すと、保存先を抜け出すパスや壊れた
	 * {@code Content-Disposition} になる。置き換え先は {@code _} で、区切り位置は保つ。
	 *
	 * @param fileName ファイル名
	 * @return 扱えない文字を置き換えたファイル名
	 */
	public static String sanitizeFileName(String fileName) {
		return UNSAFE_FILE_NAME_CHARS.matcher(StringUtils.defaultString(fileName)).replaceAll("_");
	}
}
