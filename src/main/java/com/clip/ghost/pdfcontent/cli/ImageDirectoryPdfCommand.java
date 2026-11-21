package com.clip.ghost.pdfcontent.cli;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.clip.ghost.pdfcontent.enums.PdfRotation;
import com.clip.ghost.pdfcontent.service.PdfImageService;

/**
 * ローカル画像フォルダを一括PDF化するコマンドの入口。
 * Webサーバーを起動せず、画面と同じService / PDFBox処理を使う。
 */
public final class ImageDirectoryPdfCommand {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImageDirectoryPdfCommand.class);
	private static final String USAGE = "使い方: images-to-pdf.sh <画像フォルダ> <出力.pdf> [FIT|A4] [90|180|270]";

	private ImageDirectoryPdfCommand() {
	}

	/**
	 * 画像フォルダ、保存先、任意のページサイズと時計回りの回転角を受け取って変換する。
	 *
	 * @param args 入力フォルダ、出力PDF、ページサイズ（既定FIT）、回転角（既定は回転なし）
	 */
	public static void main(String[] args) {
		int exitCode = execute(args);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	static int execute(String... args) {
		if (args.length == 1 && Strings.CS.equals(args[0], "--help")) {
			LOGGER.info(USAGE);
			return 0;
		}
		if (args.length < 2 || args.length > 4) {
			LOGGER.error(USAGE);
			return 1;
		}
		try {
			PdfImagePageSize pageSize = args.length >= 3 ? PdfImagePageSize.fromKey(args[2]) : PdfImagePageSize.FIT;
			PdfRotation rotation = args.length == 4 ? PdfRotation.fromKey(Integer.valueOf(args[3])) : null;
			Path outputPath = Path.of(args[1]);
			PdfImageService service = PdfImageService.createForLocalFiles();
			service.createPdfFromImageDirectory(Path.of(args[0]), outputPath, pageSize, rotation);
			LOGGER.info("PDFを保存しました: {}", outputPath.toAbsolutePath().normalize());
			return 0;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("PDFを作成できませんでした: {}", e.getMessage());
			LOGGER.debug("一括PDF化の失敗詳細です。", e);
			return 1;
		}
	}
}
