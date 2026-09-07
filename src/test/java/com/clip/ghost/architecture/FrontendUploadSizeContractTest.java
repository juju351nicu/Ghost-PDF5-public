package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.clip.ghost.pdfcontent.constant.PdfConstants;

/**
 * アップロードサイズ上限のBE / コンテナ / FEの整合を固定するテスト。
 * <p>
 * 3箇所の値がずれると、上限超過時にアプリの413が返らずTomcatが接続を切り、
 * ブラウザには理由の分からないネットワークエラー（Failed to fetch）しか出なくなる。
 * その再発をMavenテストで検知する。
 */
class FrontendUploadSizeContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
	private static final Pattern MAX_PDF_BYTES_PATTERN = Pattern.compile("MAX_PDF_BYTES:\\s*(\\d+)");
	private static final Pattern MAX_FILE_SIZE_PATTERN = Pattern.compile("max-file-size:\\s*(\\d+)MB");
	private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

	/**
	 * FEの上限値がBEの上限値と一致することを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void frontendPdfSizeLimitMatchesBackendLimit() throws IOException {
		String constants = read("static/js/const.js");

		Matcher matcher = MAX_PDF_BYTES_PATTERN.matcher(constants);
		assertTrue(matcher.find(), "const.js should define FILE_SIZE.MAX_PDF_BYTES.");
		assertEquals(PdfConstants.MAX_PDF_FILE_SIZE_BYTES, Long.parseLong(matcher.group(1)),
				"const.js の MAX_PDF_BYTES は PdfConstants.MAX_PDF_FILE_SIZE_BYTES と同じ値にしてください。");
	}

	/**
	 * コンテナ側の1ファイル上限が、アプリ側の上限より大きいことを確認する。
	 * <p>
	 * アプリ側の検証が先に動かないと、413ではなく接続切断になる。
	 *
	 * @throws IOException 設定ファイルを読み込めない場合
	 */
	@Test
	void multipartLimitIsLargerThanApplicationLimit() throws IOException {
		String applicationYml = read("application.yml");

		Matcher matcher = MAX_FILE_SIZE_PATTERN.matcher(applicationYml);
		assertTrue(matcher.find(), "application.yml should define spring.servlet.multipart.max-file-size in MB.");
		long multipartLimitBytes = Long.parseLong(matcher.group(1)) * BYTES_PER_MEGABYTE;
		assertAll(
				() -> assertTrue(multipartLimitBytes > PdfConstants.MAX_PDF_FILE_SIZE_BYTES,
						"max-file-size は PdfConstants.MAX_PDF_FILE_SIZE_BYTES より大きくしてください。"),
				() -> assertTrue(applicationYml.contains("max-swallow-size:"),
						"上限超過時に413を返せるよう server.tomcat.max-swallow-size を設定してください。"));
	}

	/**
	 * 上限超過をFEが送信前に止めることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void frontendRejectsOversizedPdfBeforeUpload() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String validator = read("static/js/validation/file-size-validator.js");

		assertAll(
				() -> assertTrue(validator.contains("const isWithinPdfSizeLimit")),
				() -> assertTrue(validator.contains("CONST.FILE_SIZE.MAX_PDF_BYTES")),
				() -> assertTrue(pdfApp.contains("FileSizeValidator.isWithinPdfSizeLimit(fileObject)")),
				() -> assertTrue(pdfApp.contains("FileSizeValidator.buildPdfSizeLimitMessage(fileObject)")));
	}

	/**
	 * プロジェクト相対パスのresourceをUTF-8で読み込む。
	 *
	 * @param relativePath {@link #RESOURCE_ROOT} からの相対パス
	 * @return resource内容
	 * @throws IOException resourceを読み込めない場合
	 */
	private String read(String relativePath) throws IOException {
		return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
