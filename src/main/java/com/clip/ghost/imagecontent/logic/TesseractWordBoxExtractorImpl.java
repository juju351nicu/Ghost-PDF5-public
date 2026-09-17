package com.clip.ghost.imagecontent.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.imagecontent.config.TesseractProperties;
import com.clip.ghost.imagecontent.dto.OcrWordBox;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;

import lombok.RequiredArgsConstructor;

/**
 * ローカルのTesseract CLIを使って、画像から単語単位の位置付きOCR結果を取り出す実装。
 * <p>
 * {@link TesseractImageToMarkdownConverter} と同じ設定（{@link TesseractProperties}）・同じ
 * {@link CommandRunner} を再利用するが、出力形式はTSV（単語ごとの座標付き）にする専用の呼び出しを行う。
 * Markdown下書き用の平文呼び出しは変更しない。
 */
@Component
@RequiredArgsConstructor
public class TesseractWordBoxExtractorImpl implements TesseractWordBoxExtractor {
	private static final Logger LOGGER = LoggerFactory.getLogger(TesseractWordBoxExtractorImpl.class);
	/** TSVの単語行を表すlevel値。1=ページ、2=ブロック、3=段落、4=行、5=単語。 */
	private static final int TSV_WORD_LEVEL = 5;
	private static final String TSV_CONFIG_FILE = "tsv";
	private static final int TSV_COLUMN_COUNT = 12;
	private static final int TSV_COLUMN_LEVEL = 0;
	private static final int TSV_COLUMN_LEFT = 6;
	private static final int TSV_COLUMN_TOP = 7;
	private static final int TSV_COLUMN_WIDTH = 8;
	private static final int TSV_COLUMN_HEIGHT = 9;
	private static final int TSV_COLUMN_TEXT = 11;

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
	 * 画像バイト列をTesseractのTSV出力で単語単位の位置付きOCR結果へ変換する。
	 *
	 * @param imageBytes 画像のバイト列
	 * @return 認識された単語ボックスの一覧。単語が1つも認識されない場合は空リスト
	 * @throws ImageProcessingException 一時保存、実行失敗、または異常終了の場合
	 */
	@Override
	public List<OcrWordBox> extractWordBoxes(byte[] imageBytes) {
		Path imagePath = writeTemporaryImage(imageBytes);
		LOGGER.info("Tesseractで位置付き文字認識を行います。languages={}", properties.getLanguages());
		try {
			CommandResult result = commandRunner.run(buildCommand(imagePath), properties.getTimeoutSeconds());
			if (result.exitCode() != 0) {
				throw new ImageProcessingException("Tesseractが異常終了しました。exitCode=" + result.exitCode());
			}
			return parseWordBoxes(result.output());
		} finally {
			deleteQuietly(imagePath);
		}
	}

	/**
	 * Tesseractの実行コマンドと引数を組み立てる。
	 * <p>
	 * 出力設定ファイル（{@code tsv}）はTesseractの仕様上、他のオプションより後ろの最後の引数にする必要がある。
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
		if (StringUtils.isNotBlank(properties.getTessdataDirectory())) {
			command.add("--tessdata-dir");
			command.add(properties.getTessdataDirectory());
		}
		command.add(TSV_CONFIG_FILE);
		return command;
	}

	/**
	 * TesseractのTSV出力から単語行（{@code level=5}）だけを単語ボックスへ変換する。
	 *
	 * @param tsvOutput Tesseractの標準出力（TSV形式）
	 * @return 認識された単語ボックスの一覧
	 * @throws ImageProcessingException TSV出力が空、またはヘッダー行すら無い場合
	 */
	private List<OcrWordBox> parseWordBoxes(String tsvOutput) {
		if (StringUtils.isBlank(tsvOutput)) {
			throw new ImageProcessingException("Tesseractから空の応答が返りました。");
		}
		List<String> lines = tsvOutput.lines().toList();
		List<OcrWordBox> wordBoxes = new ArrayList<>();
		// 1行目はヘッダー（level, page_num, ... text）のため読み飛ばす。
		for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
			parseWordBoxLine(lines.get(lineIndex)).ifPresent(wordBoxes::add);
		}
		return wordBoxes;
	}

	/**
	 * TSVの1行を単語ボックスへ変換する。単語行（{@code level=5}）以外、または文字列が空の行は対象外とする。
	 *
	 * @param line TSVの1行
	 * @return 単語ボックス。対象外の行の場合は空
	 */
	private Optional<OcrWordBox> parseWordBoxLine(String line) {
		// textカラムに稀に含まれ得るタブ文字で分割がズレないよう、最後のカラムとしてまとめて受け取る。
		String[] columns = line.split("\t", TSV_COLUMN_COUNT);
		if (columns.length < TSV_COLUMN_COUNT) {
			return Optional.empty();
		}
		if (!Integer.toString(TSV_WORD_LEVEL).equals(columns[TSV_COLUMN_LEVEL])) {
			return Optional.empty();
		}
		String text = columns[TSV_COLUMN_TEXT];
		if (StringUtils.isBlank(text)) {
			return Optional.empty();
		}
		return Optional.of(new OcrWordBox(text, Double.parseDouble(columns[TSV_COLUMN_LEFT]),
				Double.parseDouble(columns[TSV_COLUMN_TOP]), Double.parseDouble(columns[TSV_COLUMN_WIDTH]),
				Double.parseDouble(columns[TSV_COLUMN_HEIGHT])));
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
			Path imagePath = Files.createTempFile("ghost-ocr-box-", ".png");
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
