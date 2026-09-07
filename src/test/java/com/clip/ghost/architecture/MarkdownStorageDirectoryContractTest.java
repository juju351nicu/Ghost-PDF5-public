package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Markdown保存先の既定値が一時ディレクトリを指さないことを固定するテスト。
 * <p>
 * 保存したMarkdownは利用者の成果物であり、{@code java.io.tmpdir} 配下はOSが自動削除する
 * （Windowsのストレージセンサーは既定で有効で、空き容量が少ないほど積極的に消す）。
 * 既定値は2箇所（{@code application.yml} と {@code @Value} のfallback）にあり、
 * 片方だけ直すと設定を書かない環境で元に戻るため、両方をまとめて検証する。
 */
class MarkdownStorageDirectoryContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
	private static final Path MAIN_SOURCE = Paths.get("src/main/java");
	private static final String STORAGE_DIRECTORY_KEY = "ghost.markdown.storage-directory";
	private static final String TEMPORARY_DIRECTORY_PROPERTY = "java.io.tmpdir";
	private static final String HOME_DIRECTORY_PROPERTY = "${user.home}/ghost-pdf5/markdown";

	/**
	 * {@code application.yml} の既定値がホーム配下で、環境変数で上書きできることを確認する。
	 *
	 * @throws IOException 設定ファイルを読み込めない場合
	 */
	@Test
	void applicationYmlDefaultsToHomeDirectory() throws IOException {
		String applicationYml = read(RESOURCE_ROOT.resolve("application.yml"));
		String storageDirectoryLine = applicationYml.lines()
				.filter(line -> line.contains("storage-directory:")).findFirst().orElse("");

		assertAll(
				() -> assertFalse(storageDirectoryLine.contains(TEMPORARY_DIRECTORY_PROPERTY),
						"Markdown保存先の既定値に一時ディレクトリを使わないでください。OSの自動削除で成果物が消えます。"),
				() -> assertTrue(storageDirectoryLine.contains(HOME_DIRECTORY_PROPERTY),
						"Markdown保存先の既定値はホーム配下にしてください。"),
				() -> assertTrue(storageDirectoryLine.contains("${GHOST_MARKDOWN_STORAGE_DIRECTORY:"),
						"Markdown保存先は環境変数で上書きできるようにしてください。"),
				// 公開リポジトリのため、個人のディレクトリ構成が読み取れる絶対パスを書かない。
				() -> assertFalse(storageDirectoryLine.contains(":\\") || storageDirectoryLine.contains("C:/"),
						"設定ファイルにローカル絶対パスを書かないでください。"));
	}

	/**
	 * {@code @Value} のfallbackも一時ディレクトリを指さないことを確認する。
	 *
	 * @throws IOException ソースを読み込めない場合
	 */
	@Test
	void valueFallbackDefaultsToHomeDirectory() throws IOException {
		String service = read(MAIN_SOURCE.resolve("com/clip/ghost/markdowncontent/service/MarkdownDocumentService.java"));
		String annotationLine = service.lines().filter(line -> line.contains(STORAGE_DIRECTORY_KEY)).findFirst()
				.orElse("");

		assertAll(
				() -> assertTrue(annotationLine.contains(STORAGE_DIRECTORY_KEY),
						"Markdown保存先の設定キーが見つかりません。"),
				() -> assertFalse(annotationLine.contains(TEMPORARY_DIRECTORY_PROPERTY),
						"@Valueのfallbackにも一時ディレクトリを使わないでください。"),
				() -> assertTrue(annotationLine.contains(HOME_DIRECTORY_PROPERTY),
						"@Valueのfallbackもホーム配下にしてください。"));
	}

	/**
	 * プロジェクト相対パスのファイルをUTF-8で読み込む。
	 *
	 * @param path 読み込むファイルパス
	 * @return ファイル内容
	 * @throws IOException ファイルを読み込めない場合
	 */
	private String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
