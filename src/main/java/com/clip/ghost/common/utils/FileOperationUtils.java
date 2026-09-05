package com.clip.ghost.common.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ファイル・ディレクトリの作成、コピー、移動、削除を扱うユーティリティクラス。
 * <p>
 * 新規コードでは文字列パスではなく {@link Path} を使う。
 */
public final class FileOperationUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileOperationUtils.class);

	private FileOperationUtils() {
	}

	/**
	 * 指定されたパスに通常ファイルを作成する。親ディレクトリがない場合は作成する。
	 *
	 * @param filePath 作成するファイルパス
	 * @return 作成済み、または既存のファイルパス
	 */
	public static Path ensureFile(Path filePath) {
		if (filePath == null) {
			throw new IllegalArgumentException("filePath must not be null.");
		}
		if (Files.isDirectory(filePath) || filePath.toString().endsWith("/") || filePath.toString().endsWith("\\")) {
			throw new IllegalArgumentException("ファイル作成先としてディレクトリは指定できません。filePath=" + filePath);
		}
		try {
			Path parent = filePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			if (Files.notExists(filePath)) {
				Files.createFile(filePath);
			}
			return filePath;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * ファイルをコピーする。コピー元が存在しない場合は何もしない。
	 *
	 * @param sourcePath コピー元ファイル
	 * @param targetPath コピー先ファイル
	 */
	public static void copyFile(Path sourcePath, Path targetPath) {
		if (isMissing(sourcePath)) {
			LOGGER.debug("コピー元ファイルが存在しないためスキップします。sourcePath={}", sourcePath);
			return;
		}
		requirePath(targetPath, "targetPath");
		try {
			createParentDirectories(targetPath);
			Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.debug("ファイルコピーが完了しました。sourcePath={}, targetPath={}, byteSize={}", sourcePath, targetPath,
					Files.size(targetPath));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * フォルダを再帰的にコピーする。コピー元が存在しない場合は何もしない。
	 *
	 * @param sourcePath コピー元フォルダ
	 * @param targetPath コピー先フォルダ
	 */
	public static void copyDirectory(Path sourcePath, Path targetPath) {
		if (isMissing(sourcePath)) {
			LOGGER.debug("コピー元フォルダが存在しないためスキップします。sourcePath={}", sourcePath);
			return;
		}
		requirePath(targetPath, "targetPath");
		try {
			FileUtils.copyDirectory(sourcePath.toFile(), targetPath.toFile());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * ファイルを移動する。移動元が存在しない場合は何もしない。
	 *
	 * @param sourcePath 移動元ファイル
	 * @param targetPath 移動先ファイル
	 */
	public static void moveFile(Path sourcePath, Path targetPath) {
		if (isMissing(sourcePath)) {
			LOGGER.debug("移動元ファイルが存在しないためスキップします。sourcePath={}", sourcePath);
			return;
		}
		requirePath(targetPath, "targetPath");
		try {
			createParentDirectories(targetPath);
			Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * フォルダを移動する。移動元が存在しない場合は何もしない。
	 *
	 * @param sourcePath 移動元フォルダ
	 * @param targetPath 移動先フォルダ
	 */
	public static void moveDirectory(Path sourcePath, Path targetPath) {
		if (isMissing(sourcePath)) {
			LOGGER.debug("移動元フォルダが存在しないためスキップします。sourcePath={}", sourcePath);
			return;
		}
		requirePath(targetPath, "targetPath");
		copyDirectory(sourcePath, targetPath);
		deleteFolder(sourcePath, true);
	}

	/**
	 * ディレクトリを作成する。親ディレクトリがない場合はまとめて作成する。
	 *
	 * @param folderPath 作成するディレクトリ
	 */
	public static void createDirectories(Path folderPath) {
		if (folderPath == null) {
			return;
		}
		try {
			Files.createDirectories(folderPath);
		} catch (IOException e) {
			throw new UncheckedIOException("新しいディレクトリ操作でエラーが発生しました。" + folderPath.toAbsolutePath(), e);
		}
	}

	/**
	 * 文字列をUTF-8でファイルへ書き込む。親ディレクトリがない場合は作成する。
	 *
	 * @param filePath 書き込み先ファイル
	 * @param content  書き込む文字列。nullの場合は空文字として保存する
	 * @return 書き込み先ファイル
	 */
	public static Path writeString(Path filePath, String content) {
		requirePath(filePath, "filePath");
		try {
			createParentDirectories(filePath);
			return Files.writeString(filePath, StringUtils.defaultString(content), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 指定されたファイルを削除する。存在しない場合は何もしない。
	 *
	 * @param filePathAndName 削除対象ファイル
	 */
	public static void deleteFile(Path filePathAndName) {
		if (filePathAndName == null) {
			return;
		}
		try {
			Files.deleteIfExists(filePathAndName);
		} catch (IOException e) {
			throw new UncheckedIOException("削除エラーが発生しました。" + filePathAndName.toAbsolutePath(), e);
		}
	}

	/**
	 * フォルダを削除する。
	 *
	 * @param folderPath  削除対象フォルダ
	 * @param isSubFolder trueの場合はサブフォルダも含めて再帰削除する
	 */
	public static void deleteFolder(Path folderPath, boolean isSubFolder) {
		if (folderPath == null || Files.notExists(folderPath)) {
			return;
		}
		try {
			if (isSubFolder) {
				FileUtils.deleteDirectory(folderPath.toFile());
				return;
			}
			try (Stream<Path> stream = Files.list(folderPath)) {
				stream.filter(Files::isRegularFile).forEach(FileOperationUtils::deleteFile);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * 指定されたパスの親ディレクトリを作成する。
	 *
	 * @param filePath 親ディレクトリを作成したいファイルパス
	 * @throws IOException ディレクトリ作成に失敗した場合
	 */
	private static void createParentDirectories(Path filePath) throws IOException {
		Path parent = filePath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	/**
	 * パスが未指定、または存在しないか判定する。
	 *
	 * @param path 判定対象パス
	 * @return 未指定または存在しない場合true
	 */
	private static boolean isMissing(Path path) {
		return path == null || Files.notExists(path);
	}

	/**
	 * 必須パスが指定されていることを確認する。
	 *
	 * @param path パス
	 * @param name 引数名
	 */
	private static void requirePath(Path path, String name) {
		if (path == null) {
			throw new IllegalArgumentException(name + " must not be null.");
		}
	}
}
