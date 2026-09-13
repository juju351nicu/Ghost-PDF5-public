package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.clip.ghost.pdfcontent.enums.PdfRotation;

/**
 * 変換系プルダウンの選択肢が、サーバのenumのコード値と一致していることを固定するテスト。
 * <p>
 * 画面の選択肢とサーバが受け付ける値がずれると、利用者が選べるのに必ず400になる組み合わせが生まれる。
 * ずれは「その選択肢を選んだときだけ落ちる」形で出るため、手で試す限り見逃しやすい。
 * <p>
 * npmなしのWebJar Vue構成のため、JavaScriptのソースを文字列として読み、
 * enumのコード値が選択肢の定義に含まれることを確認する方式を既存の契約テストから踏襲する。
 */
class FrontendConversionChoiceContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
	private static final String FORM_STATE_PATH = "static/js/models/pdf-form-state.js";

	/**
	 * ページ回転角の選択肢が {@link PdfRotation} のコード値と一致することを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("ページ回転角の選択肢がPdfRotationのコード値と一致する")
	void rotationChoicesMatchServerEnum() throws IOException {
		String formState = read(FORM_STATE_PATH);
		List<String> keys = Arrays.stream(PdfRotation.values()).map(rotation -> String.valueOf(rotation.getKey()))
				.toList();

		assertAll(() -> assertTrue(formState.contains("const createRotationItems")),
				() -> assertAllKeysDeclared(formState, keys, "id: %s"));
	}

	/**
	 * ページ画像化の出力形式の選択肢が {@link PdfImageFormat} のコード値と一致することを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("ページ画像化の出力形式の選択肢がPdfImageFormatのコード値と一致する")
	void imageFormatChoicesMatchServerEnum() throws IOException {
		String formState = read(FORM_STATE_PATH);
		List<String> keys = Arrays.stream(PdfImageFormat.values()).map(PdfImageFormat::getKey).toList();

		assertAll(() -> assertTrue(formState.contains("const createImageFormatItems")),
				() -> assertAllKeysDeclared(formState, keys, "id: \"%s\""));
	}

	/**
	 * 画像からPDFを作る際のページサイズの選択肢が {@link PdfImagePageSize} のコード値と一致することを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("画像PDFのページサイズの選択肢がPdfImagePageSizeのコード値と一致する")
	void imagePageSizeChoicesMatchServerEnum() throws IOException {
		String formState = read(FORM_STATE_PATH);
		List<String> keys = Arrays.stream(PdfImagePageSize.values()).map(PdfImagePageSize::getKey).toList();

		assertAll(() -> assertTrue(formState.contains("const createImagePageSizeItems")),
				() -> assertAllKeysDeclared(formState, keys, "id: \"%s\""));
	}

	/**
	 * PDFから出力するOffice形式の選択肢が {@link OfficeDocumentType} のコード値と一致することを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("Office出力形式の選択肢がOfficeDocumentTypeのコード値と一致する")
	void officeFormatChoicesMatchServerEnum() throws IOException {
		String formState = read(FORM_STATE_PATH);
		List<String> keys = Arrays.stream(OfficeDocumentType.values()).map(OfficeDocumentType::getKey).toList();

		assertAll(() -> assertTrue(formState.contains("const createOfficeFormatItems")),
				() -> assertAllKeysDeclared(formState, keys, "id: \"%s\""));
	}

	/**
	 * 変換系の新しい操作が、責務別JavaScript境界を通ってAPIへ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("回転・画像化・HTML/EPUB/Office出力が責務別JavaScript境界を通ってAPIへ接続される")
	void conversionOperationsUseExistingFrontendBoundaries() throws IOException {
		String mainTemplate = read("templates/main.html");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String constants = read("static/js/const.js");
		String payload = read("static/js/api/pdf-payload.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(() -> assertTrue(mainTemplate.contains("@request-rotate-pdf=\"requestRotatePdf\"")),
				() -> assertTrue(mainTemplate.contains("@request-images-pdf=\"requestImagesPdf\"")),
				() -> assertTrue(mainTemplate.contains("@request-html-pdf=\"requestHtmlPdf\"")),
				() -> assertTrue(mainTemplate.contains("@request-office-from-pdf=\"requestOfficeFromPdf\"")),
				() -> assertTrue(mainTemplate.contains("@request-epub-pdf=\"requestEpubPdf\"")),
				() -> assertTrue(originalPdfForm.contains("this.$emit(\"request-rotate-pdf\")")),
				() -> assertTrue(originalPdfForm.contains("this.$emit(\"request-images-pdf\")")),
				() -> assertTrue(constants.contains("ROTATE_PDF: \"/rotatePdf\"")),
				() -> assertTrue(constants.contains("IMAGES_PDF: \"/imagesPdf\"")),
				() -> assertTrue(constants.contains("PDF_FROM_IMAGES: \"/pdfFromImages\"")),
				() -> assertTrue(constants.contains("HTML_PDF: \"/htmlPdf\"")),
				() -> assertTrue(constants.contains("PDF_FROM_HTML: \"/pdfFromHtml\"")),
				() -> assertTrue(constants.contains("EPUB_PDF: \"/epubPdf\"")),
				() -> assertTrue(constants.contains("PDF_FROM_EPUB: \"/pdfFromEpub\"")),
				() -> assertTrue(constants.contains("MARKDOWN_DRAFT_OFFICE: \"/markdownDraftOffice\"")),
				() -> assertTrue(constants.contains("PDF_FROM_OFFICE: \"/pdfFromOffice\"")),
				() -> assertTrue(constants.contains("OFFICE_FROM_PDF: \"/officeFromPdf\"")),
				() -> assertTrue(constants.contains("MARKDOWN_FILES_CSV: \"/markdownFilesCsv\"")),
				() -> assertTrue(payload.contains("const buildRotatePayload")),
				() -> assertTrue(payload.contains("const buildImagesPayload")),
				() -> assertTrue(payload.contains("const buildPdfFromImagesPayload")),
				() -> assertTrue(payload.contains("const buildOfficePayload")),
				() -> assertTrue(payload.contains("const buildOfficeFromPdfPayload")),
				() -> assertTrue(payload.contains("const buildPdfFromEpubPayload")),
				() -> assertTrue(pdfApp.contains("PdfApiClient.requestOfficeMarkdown(")),
				() -> assertTrue(pdfApp.contains("MarkdownApiClient.downloadMarkdownFilesCsv(")));
	}

	/**
	 * enumのコード値がすべて選択肢として宣言されていることを検証する。
	 *
	 * @param formState  画面状態のJavaScriptソース
	 * @param keys       サーバのenumのコード値
	 * @param itemFormat 選択肢の宣言形式（{@code id: "%s"} など）
	 */
	private void assertAllKeysDeclared(String formState, List<String> keys, String itemFormat) {
		for (String key : keys) {
			String declaration = itemFormat.formatted(key);
			assertTrue(formState.contains(declaration),
					() -> FORM_STATE_PATH + " へ選択肢 " + declaration + " を追加してください。");
		}
	}

	/**
	 * プロジェクト相対パスのフロントエンドresourceをUTF-8で読み込む。
	 *
	 * @param relativePath {@code src/main/resources} からの相対パス
	 * @return resourceの内容
	 * @throws IOException resourceを読み込めない場合
	 */
	private String read(String relativePath) throws IOException {
		return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
