package com.clip.ghost.imagecontent.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.clip.ghost.imagecontent.config.TesseractProperties;

/**
 * 実際のTesseractコマンドを使う統合テスト。
 * <p>
 * Tesseractが利用できない環境では {@link org.junit.jupiter.api.Assumptions} でskipする。
 * CIに日本語フォントが無い可能性を考え、英数字のみで検証する。{@code @Tag("ocr")} で通常の軽量確認からは除外する。
 */
@Tag("ocr")
class TesseractOcrIntegrationTest {

	@Test
	@DisplayName("英数字を描いた画像をTesseractで文字起こしできる")
	void recognizesEnglishTextWithRealTesseract() throws IOException {
		String command = resolveTesseractCommand();
		assumeTrue(isRunnable(command), "Tesseractが利用できない環境のためskipします。");

		byte[] png = renderText("HELLO 12345");
		TesseractProperties properties = new TesseractProperties();
		properties.setEnabled(true);
		properties.setCommand(command);
		properties.setLanguages("eng");
		properties.setPsm(6);
		properties.setPreserveInterwordSpaces(true);
		properties.setTimeoutSeconds(60);
		TesseractImageToMarkdownConverter converter = new TesseractImageToMarkdownConverter(properties,
				new ProcessCommandRunner());

		String output = converter.convert(png, "image/png");

		String normalized = output.replace(" ", "").toUpperCase();
		assertTrue(normalized.contains("HELLO") || normalized.contains("12345"), output);
	}

	/**
	 * 利用するTesseractコマンドを解決する。環境変数、既定インストール先、PATH上のコマンドの順に選ぶ。
	 *
	 * @return Tesseractコマンドまたはパス
	 */
	private String resolveTesseractCommand() {
		String fromEnv = System.getenv("GHOST_OCR_TESSERACT_COMMAND");
		if (StringUtils.isNotBlank(fromEnv)) {
			return fromEnv;
		}
		Path installed = Paths.get("C:", "Program Files", "Tesseract-OCR", "tesseract.exe");
		if (Files.isRegularFile(installed)) {
			return installed.toString();
		}
		return "tesseract";
	}

	/**
	 * 指定コマンドで {@code --version} を実行でき、正常終了するか判定する。
	 *
	 * @param command Tesseractコマンドまたはパス
	 * @return 実行できて正常終了する場合はtrue
	 */
	private boolean isRunnable(String command) {
		try {
			Process process = new ProcessBuilder(command, "--version")
					.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			return process.waitFor() == 0;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * 指定文字列を描いたPNG画像を生成する。
	 *
	 * @param text 描画する文字列
	 * @return PNGバイト列
	 * @throws IOException PNGエンコードに失敗した場合
	 */
	private byte[] renderText(String text) throws IOException {
		BufferedImage image = new BufferedImage(600, 160, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
		graphics.drawString(text, 30, 100);
		graphics.dispose();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(image, "png", outputStream);
		return outputStream.toByteArray();
	}
}
