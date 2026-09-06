package com.clip.ghost.imagecontent.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.imagecontent.config.TesseractProperties;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;

import lombok.RequiredArgsConstructor;

/**
 * ローカルのTesseract CLIを使って画像をMarkdown（プレーンテキスト）へ文字起こしする変換器。
 * <p>
 * オフラインや大量バッチ向けのproviderとして、外部送信なしで動く。プロセス起動は {@link CommandRunner} へ
 * 委譲し、コマンド引数はすべて設定値と生成した一時ファイルパスから作る（リクエスト由来の値は渡さない）。
 */
@Component
@RequiredArgsConstructor
public class TesseractImageToMarkdownConverter implements ImageToMarkdownConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(TesseractImageToMarkdownConverter.class);

	private final TesseractProperties properties;
	private final CommandRunner commandRunner;

	/**
	 * 機能が有効かつコマンドが設定されている場合に利用可能と判定する。
	 *
	 * @return 利用可能な場合はtrue
	 */
	@Override
	public boolean isEnabled() {
		return properties.isEnabled() && StringUtils.isNotBlank(properties.getCommand());
	}

	/**
	 * 認識言語とpsmを含む識別文字列を返す。
	 *
	 * @return 実装を説明する文字列
	 */
	@Override
	public String describe() {
		return "tesseract(languages=" + properties.getLanguages() + ", psm=" + properties.getPsm() + ")";
	}

	/**
	 * provider識別子 {@code tesseract} を返す。
	 *
	 * @return provider識別子
	 */
	@Override
	public String provider() {
		return "tesseract";
	}

	/**
	 * 画像バイト列をTesseractで文字起こしする。
	 *
	 * @param imageBytes 画像のバイト列
	 * @param mediaType  画像のMIMEタイプ（Tesseractは拡張子ではなく内容で判別するため参照のみ）
	 * @return 文字起こし結果のテキスト
	 * @throws ImageProcessingException 一時保存、実行失敗、異常終了、または空応答の場合
	 */
	@Override
	public String convert(byte[] imageBytes, String mediaType) {
		Path imagePath = writeTemporaryImage(imageBytes);
		LOGGER.info("Tesseractで画像を文字起こしします。languages={}", properties.getLanguages());
		try {
			CommandResult result = commandRunner.run(buildCommand(imagePath), properties.getTimeoutSeconds());
			if (result.exitCode() != 0) {
				throw new ImageProcessingException("Tesseractが異常終了しました。exitCode=" + result.exitCode());
			}
			if (StringUtils.isBlank(result.output())) {
				throw new ImageProcessingException("Tesseractから空の応答が返りました。");
			}
			return result.output();
		} finally {
			deleteQuietly(imagePath);
		}
	}

	/**
	 * Tesseractの実行コマンドと引数を組み立てる。
	 * <p>
	 * 引数は設定値と生成した一時ファイルパスだけで構成し、リクエスト由来の値は含めない。
	 *
	 * @param imagePath 入力画像の一時ファイルパス
	 * @return コマンドと引数
	 */
	private List<String> buildCommand(Path imagePath) {
		List<String> command = new ArrayList<>();
		command.add(properties.getCommand());
		command.add(imagePath.toString());
		command.add("stdout");
		command.add("-l");
		command.add(properties.getLanguages());
		command.add("--psm");
		command.add(Integer.toString(properties.getPsm()));
		if (properties.isPreserveInterwordSpaces()) {
			command.add("-c");
			command.add("preserve_interword_spaces=1");
		}
		if (StringUtils.isNotBlank(properties.getTessdataDirectory())) {
			command.add("--tessdata-dir");
			command.add(properties.getTessdataDirectory());
		}
		return command;
	}

	/**
	 * 画像バイト列を一時PNGファイルへ保存する。
	 *
	 * @param imageBytes 画像バイト列
	 * @return 一時ファイルのパス
	 * @throws ImageProcessingException 保存に失敗した場合
	 */
	private Path writeTemporaryImage(byte[] imageBytes) {
		try {
			Path imagePath = Files.createTempFile("ghost-ocr-", ".png");
			Files.write(imagePath, imageBytes);
			return imagePath;
		} catch (IOException e) {
			throw new ImageProcessingException("OCR一時画像の保存に失敗しました。", e);
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
			LOGGER.warn("OCR一時画像の削除に失敗しました。path={}", path, e);
		}
	}
}
