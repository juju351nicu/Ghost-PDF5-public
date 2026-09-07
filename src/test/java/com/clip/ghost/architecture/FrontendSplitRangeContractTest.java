package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * 範囲ごとのPDF分割で、画面入力からAPIまでの接続を固定するテスト。
 * <p>
 * 分割範囲は画面状態・形式validation・payload・API clientをまたぐため、
 * JavaScriptのテストランナーを持たない構成でも接続欠落をMavenテストで検知する。
 */
class FrontendSplitRangeContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * 分割範囲入力が既存の責務別JavaScript境界を通ってAPIへ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void splitRangeInputReachesSplitApiThroughExistingBoundaries() throws IOException {
		String formState = read("static/js/models/pdf-form-state.js");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String validator = read("static/js/validation/page-number-validator.js");
		String payload = read("static/js/api/pdf-payload.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertTrue(formState.contains("splitRangesText: { text: \"\", message: \"\" },"),
						"分割範囲の画面状態を用意してください。"),
				() -> assertTrue(originalPdfForm.contains("v-model=\"originalFile.splitRangesText.text\""),
						"分割範囲の入力欄を編集元PDFカードへ置いてください。"),
				() -> assertTrue(validator.contains("const parseSplitRangesText"),
						"分割範囲の形式validationはpage-number-validator.jsへ置いてください。"),
				() -> assertTrue(pdfApp.contains("PageNumberValidator.parseSplitRangesText("),
						"分割範囲の解析はpage-number-validator.js経由にしてください。"),
				() -> assertTrue(payload.contains("payload.push({ key: \"splitRanges\", value: splitRanges });"),
						"分割範囲をmultipart payloadへ載せてください。"),
				() -> assertTrue(pdfApp.contains("PdfPayload.buildSplitPayload("),
						"分割payloadの生成はpdf-payload.js経由にしてください。"));
	}

	/**
	 * 分割範囲が空欄のときに従来どおり1ページずつ分割されることを、入力と送信の両側で確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void emptySplitRangeKeepsSinglePageSplitBehavior() throws IOException {
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String payload = read("static/js/api/pdf-payload.js");

		assertAll(
				// 空欄は「1ページずつ」を表す正常な入力。placeholderでもそれを示す。
				() -> assertTrue(originalPdfForm.contains("空欄で1ページずつ"), "空欄時の挙動をplaceholderで示してください。"),
				() -> assertTrue(payload.contains("if (Array.isArray(splitRanges) && splitRanges.length > 0) {"),
						"分割範囲が空の場合はkeyを送らないでください。"));
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
