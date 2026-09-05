package com.clip.ghost.common.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ファイル情報の取得、ファイル一覧、サイズ、行数、内容検索を扱うユーティリティクラス。
 */
public final class FileInfoUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileInfoUtils.class);

	private FileInfoUtils() {
	}

	/**
	 * フォルダ配下の通常ファイルパスを取得する。
	 *
	 * @param folderPath フォルダパス
	 * @param recursive  trueの場合は再帰的に取得する
	 * @return 通常ファイルパスのリスト。対象フォルダが存在しない場合は空リスト
	 */
	public static List<Path> getFilePaths(Path folderPath, boolean recursive) {
		if (folderPath == null || !Files.isDirectory(folderPath)) {
			return List.of();
		}
		if (recursive) {
			return getRegularFilePathsRecursively(folderPath);
		}
		return getRegularFilePathsDirectly(folderPath);
	}

	/**
	 * フォルダ配下の通常ファイルパスを再帰的に取得する。
	 *
	 * @param folderPath フォルダパス
	 * @return 通常ファイルパスのリスト
	 */
	private static List<Path> getRegularFilePathsRecursively(Path folderPath) {
		try (Stream<Path> stream = Files.walk(folderPath)) {
			return stream.filter(Files::isRegularFile).toList();
		} catch (IOException e) {
			LOGGER.warn("フォルダ配下のファイルパス取得に失敗しました。folderPath={}, recursive={}", folderPath, true);
			LOGGER.debug("フォルダ配下ファイルパス取得失敗の詳細です。", e);
			return List.of();
		}
	}

	/**
	 * フォルダ直下の通常ファイルパスを取得する。
	 *
	 * @param folderPath フォルダパス
	 * @return 通常ファイルパスのリスト
	 */
	private static List<Path> getRegularFilePathsDirectly(Path folderPath) {
		try (Stream<Path> stream = Files.list(folderPath)) {
			return stream.filter(Files::isRegularFile).toList();
		} catch (IOException e) {
			LOGGER.warn("フォルダ配下のファイルパス取得に失敗しました。folderPath={}, recursive={}", folderPath, false);
			LOGGER.debug("フォルダ配下ファイルパス取得失敗の詳細です。", e);
			return List.of();
		}
	}

	/**
	 * フォルダ内の通常ファイル数を再帰的に取得する。
	 *
	 * @param folderPath フォルダパス
	 * @return 通常ファイル数。対象フォルダが存在しない場合は0
	 */
	public static long countFiles(Path folderPath) {
		if (folderPath == null || !Files.isDirectory(folderPath)) {
			return 0;
		}
		try (Stream<Path> stream = Files.walk(folderPath)) {
			return stream.filter(Files::isRegularFile).count();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 指定フォルダ直下で、正規表現に一致する通常ファイル数を取得する。
	 *
	 * @param folderPath フォルダパス
	 * @param pattern    ファイル名に適用する正規表現。nullまたは空文字の場合は全ファイル数を返却する
	 * @return ファイル名に一致する通常ファイル数
	 */
	public static int countFiles(Path folderPath, String pattern) {
		if (folderPath == null || !Files.isDirectory(folderPath)) {
			return 0;
		}
		try (Stream<Path> stream = Files.list(folderPath)) {
			Stream<Path> regularFiles = stream.filter(Files::isRegularFile);
			if (StringUtils.isEmpty(pattern)) {
				return (int) regularFiles.count();
			}
			return (int) regularFiles.filter(path -> Pattern.matches(pattern, path.getFileName().toString())).count();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * ファイルの最終変更時刻を文字列で取得する。
	 *
	 * @param filePath ファイルパス
	 * @return 最終変更時刻。取得できない場合はnull
	 */
	public static String getLastModifiedTimeText(Path filePath) {
		try {
			if (isRegularFile(filePath)) {
				FileTime fileTime = Files.getLastModifiedTime(filePath);
				Instant instant = fileTime.toInstant();
				LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
				return localDateTime.toString();
			}
		} catch (IOException e) {
			LOGGER.warn("ファイル最終変更時刻の取得に失敗しました。filePath={}", filePath);
			LOGGER.debug("ファイル最終変更時刻取得失敗の詳細です。", e);
		}
		return null;
	}

	/**
	 * フォルダ直下の通常ファイル名を、指定文字列の大文字小文字を無視した部分一致で取得する。
	 *
	 * @param folderPath フォルダパス
	 * @param target     検索文字列
	 * @return 一致したファイル名リスト
	 */
	public static List<String> getTargetFileNames(Path folderPath, String target) {
		if (folderPath == null || StringUtils.isEmpty(target)) {
			return List.of();
		}
		return getFilePaths(folderPath, false).stream().map(path -> path.getFileName().toString())
				.filter(fileName -> Strings.CI.contains(fileName, target)).toList();
	}

	/**
	 * ファイル内容に指定文字列が含まれるか判定する。
	 * <p>
	 * ファイル読み込みは {@link Files#readString(Path)} に任せ、検索条件不足や対象なしはfalseとして扱う。
	 *
	 * @param filePath   読み取り対象ファイル
	 * @param searchText 検索文字列
	 * @return 含まれる場合true
	 */
	public static boolean containsText(Path filePath, String searchText) {
		if (!isRegularFile(filePath) || StringUtils.isEmpty(searchText)) {
			return false;
		}
		try {
			return Strings.CS.contains(Files.readString(filePath), searchText);
		} catch (IOException e) {
			LOGGER.warn("ファイル内容検索時のファイル読み込みに失敗しました。searchText={}, filePath={}", searchText, filePath);
			LOGGER.debug("ファイル内容検索時のファイル読み込み失敗の詳細です。", e);
			return false;
		}
	}

	/**
	 * ファイルサイズをバイト単位で取得する。
	 *
	 * @param filePath ファイルパス
	 * @return ファイルサイズ。存在しない場合はnull
	 */
	public static Long getFileByteSize(Path filePath) {
		try {
			if (isRegularFile(filePath)) {
				return Files.size(filePath);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return null;
	}

	/**
	 * ファイルサイズをKB単位に丸めて取得する。
	 *
	 * @param filePath ファイルパス
	 * @return ファイルサイズ(KB)。存在しない場合はnull
	 */
	public static Long getRoundedKiloByteSize(Path filePath) {
		Long byteSize = getFileByteSize(filePath);
		if (byteSize != null) {
			return Math.round(byteSize / 1024.0);
		}
		return null;
	}

	/**
	 * 指定されたパスが通常ファイルとして存在するか判定する。
	 *
	 * @param filePath ファイルパス
	 * @return 通常ファイルとして存在する場合true
	 */
	public static boolean isRegularFile(Path filePath) {
		return filePath != null && Files.isRegularFile(filePath);
	}

	/**
	 * ファイルサイズをKB単位に切り上げて取得する。
	 *
	 * @param filePath ファイルパス
	 * @return ファイルサイズ(KB)。存在しない場合はnull
	 */
	public static Double getCeilKiloByteSize(Path filePath) {
		if (isRegularFile(filePath)) {
			long fileSizeOfByte = getFileByteSize(filePath);
			return Math.ceil(fileSizeOfByte / 1024.0);
		}
		return null;
	}

	/**
	 * ファイルの行数を取得する。
	 *
	 * @param filePath ファイルパス
	 * @return 行数。対象ファイルが存在しない場合は0
	 */
	public static long countLines(Path filePath) {
		if (!isRegularFile(filePath)) {
			return 0;
		}
		try (Stream<String> lines = Files.lines(filePath)) {
			return lines.count();
		} catch (IOException e) {
			throw new UncheckedIOException("ファイルが読み込めません。" + filePath, e);
		}
	}
}
