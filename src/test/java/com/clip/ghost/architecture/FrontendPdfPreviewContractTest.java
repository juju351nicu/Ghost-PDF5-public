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
 * 編集元PDFのプレビューがローカル表示で完結することを固定するテスト。
 * <p>
 * 以前はファイル選択のたびにPDFを {@code /showPdf} へアップロードして別タブで開いていた。
 * ページ番号を指定する加工機能では入力PDFを見られることが前提になるため、
 * アップロードなしのローカルプレビューへ変更した。その退行をMavenテストで検知する。
 */
class FrontendPdfPreviewContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * 編集元PDFのプレビューがObject URLとiframeで表示され、Object URLが解放されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void originalPdfPreviewUsesLocalObjectUrl() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String formState = read("static/js/models/pdf-form-state.js");
		String mainTemplate = read("templates/main.html");

		assertAll(
				() -> assertTrue(formState.contains("previewUrl: \"\"")),
				() -> assertTrue(pdfApp.contains("URL.createObjectURL(fileObject)")),
				() -> assertTrue(pdfApp.contains("URL.revokeObjectURL(this.originalFile.previewUrl)")),
				() -> assertTrue(pdfApp.contains("this.clearOriginalPdfPreview();")),
				() -> assertTrue(originalPdfForm.contains(":src=\"originalFile.previewUrl\"")),
				() -> assertTrue(originalPdfForm.contains("this.$emit(\"request-open-original-pdf\")")),
				() -> assertTrue(
						mainTemplate.contains("@request-open-original-pdf=\"openOriginalPdfInNewTab\"")));
	}

	/**
	 * プレビュー目的のアップロードが残っていないことを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void originalPdfPreviewDoesNotUploadForViewing() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String constants = read("static/js/const.js");
		String payload = read("static/js/api/pdf-payload.js");

		assertAll(() -> assertFalse(pdfApp.contains("REST_PATH.SHOW_PDF")),
				() -> assertFalse(constants.contains("SHOW_PDF")),
				() -> assertFalse(payload.contains("buildPreviewPayload")));
	}

	/**
	 * プロジェクト相対パスのフロントエンドresourceをUTF-8で読み込む。
	 *
	 * @param relativePath {@link #RESOURCE_ROOT} からの相対パス
	 * @return resource内容
	 * @throws IOException resourceを読み込めない場合
	 */
	private String read(String relativePath) throws IOException {
		return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
