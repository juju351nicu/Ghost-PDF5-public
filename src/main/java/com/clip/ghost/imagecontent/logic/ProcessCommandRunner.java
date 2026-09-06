package com.clip.ghost.imagecontent.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.imagecontent.exception.ImageProcessingException;

import lombok.NoArgsConstructor;

/**
 * {@link CommandRunner} のプロセス起動実装。
 * <p>
 * {@link ProcessBuilder} でシェルを介さずにコマンドを起動する。プロジェクト内で {@code ProcessBuilder} を
 * 使うのはこのクラスだけに限定する。標準出力は一時ファイルへリダイレクトしてパイプのバッファ詰まりを避け、
 * 標準エラーは破棄する。タイムアウト超過時はプロセスを強制終了する。
 */
@Component
@NoArgsConstructor
public class ProcessCommandRunner implements CommandRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCommandRunner.class);

	/**
	 * 外部コマンドを実行し、終了コードと標準出力を返す。
	 *
	 * @param command        実行するコマンドと引数
	 * @param timeoutSeconds タイムアウト秒数
	 * @return 実行結果
	 * @throws ImageProcessingException 起動失敗、タイムアウト、または中断された場合
	 */
	@Override
	public CommandResult run(List<String> command, long timeoutSeconds) {
		Path outputFile = createOutputFile();
		try {
			ProcessBuilder builder = new ProcessBuilder(command);
			builder.redirectOutput(outputFile.toFile());
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process process = builder.start();
			boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new ImageProcessingException("外部コマンドがタイムアウトしました。");
			}
			return new CommandResult(process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new ImageProcessingException("外部コマンドの実行に失敗しました。", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ImageProcessingException("外部コマンドの実行が中断されました。", e);
		} finally {
			deleteQuietly(outputFile);
		}
	}

	/**
	 * 標準出力のリダイレクト先一時ファイルを作成する。
	 *
	 * @return 一時ファイルのパス
	 * @throws ImageProcessingException 作成に失敗した場合
	 */
	private Path createOutputFile() {
		try {
			return Files.createTempFile("ghost-ocr-out-", ".txt");
		} catch (IOException e) {
			throw new ImageProcessingException("外部コマンドの出力ファイル作成に失敗しました。", e);
		}
	}

	/**
	 * 一時ファイルを削除する。削除失敗は警告ログに記録し、例外を送出しない。
	 *
	 * @param path 削除対象のパス
	 */
	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.warn("外部コマンドの一時ファイル削除に失敗しました。path={}", path, e);
		}
	}
}
