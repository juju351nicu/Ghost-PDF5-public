package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * サムネイル一覧からのページ選択で、画面とpdf.jsの接続を固定するテスト。
 * <p>
 * サムネイルはコンポーネント・app state・pdf.jsのラッパーをまたぐため、
 * JavaScriptのテストランナーを持たない構成でも接続欠落をMavenテストで検知する。
 */
class FrontendThumbnailContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
	private static final Path PDFJS_VENDOR_ROOT = RESOURCE_ROOT.resolve("static/vendor/pdfjs");
	private static final Path JAVA_SOURCE_ROOT = Paths.get("src/main/java");
	private static final Path PDFJS_TYPE_REFERENCE_ROOT = Paths.get("docs/reference");
	private static final Pattern RENDERER_VERSION = Pattern.compile("const PDFJS_VERSION = \"([^\"]+)\";");
	private static final Pattern VENDOR_README_VERSION = Pattern.compile("\\| バージョン \\| ([^|]+?) \\|");

	/**
	 * サムネイル操作が既存の責務別JavaScript境界を通ってpdf.jsの描画へ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("サムネイル操作が責務別JavaScript境界を通ってpdf.jsの描画へ接続される")
	void thumbnailUiUsesExistingFrontendBoundaries() throws IOException {
		String mainTemplate = read("templates/main.html");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");
		String thumbnailList = read("static/js/components/pdf-thumbnail-list.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String renderer = read("static/js/pdf/pdf-thumbnail-renderer.js");

		assertAll(
				() -> assertTrue(mainTemplate.contains("@render-thumbnails-pdf=\"renderPdfThumbnails\""),
						"サムネイル描画のイベントをpdf-app.jsへつないでください。"),
				() -> assertTrue(originalPdfForm.contains("<pdf-thumbnail-list"),
						"サムネイル一覧は編集元PDFカードへ置いてください。"),
				() -> assertTrue(thumbnailList.contains("this.$emit(\"render-thumbnails\")"),
						"サムネイルの描画し直しはコンポーネントからイベントで通知してください。"),
				() -> assertTrue(pdfApp.contains("PdfThumbnailRenderer.renderThumbnails("),
						"サムネイル描画はpdf-thumbnail-renderer.js経由にしてください。"),
				// pdf.jsの呼び出しをラッパー1箇所へ閉じ、componentやapp stateへ散らさない。
				() -> assertTrue(renderer.contains("import(PDFJS_MODULE_PATH)"),
						"pdf.jsの読み込みはpdf-thumbnail-renderer.jsへ置いてください。"),
				() -> assertEquals(1, countOccurrences(pdfApp, "PdfThumbnailRenderer.renderThumbnails("),
						"サムネイル描画の呼び出し箇所は1つにしてください。"));
	}

	/**
	 * サムネイル生成のためにPDFをサーバーへ送らないことを確認する。
	 *
	 * @throws IOException resourceを読み込めない場合
	 */
	@Test
	@DisplayName("サムネイル生成でPDFをサーバーへ送らない")
	void thumbnailsAreNeverUploadedToServer() throws IOException {
		String constants = read("static/js/const.js");
		String payload = read("static/js/api/pdf-payload.js");
		String apiClient = read("static/js/api/pdf-api-client.js");
		String renderer = read("static/js/pdf/pdf-thumbnail-renderer.js");

		assertAll(
				// サーバー側のサムネイルAPIは削除済み。復活させると同じ描画が2系統になる。
				() -> assertFalse(constants.contains("thumbnailsPdf"), "サムネイルAPIのURLを復活させないでください。"),
				() -> assertFalse(payload.contains("Thumbnail"), "サムネイルをmultipartで送らないでください。"),
				() -> assertFalse(apiClient.contains("Thumbnail"), "サムネイルをAPI経由で取得しないでください。"),
				() -> assertFalse(renderer.contains("FetchClient"), "サムネイル描画で通信しないでください。"),
				() -> assertFalse(Files.exists(JAVA_SOURCE_ROOT
						.resolve("com/clip/ghost/pdfcontent/controller/PdfThumbnailController.java")),
						"サムネイル生成をサーバー側へ戻さないでください。"));
	}

	/**
	 * サムネイルがファイル選択と同時に描かれ、やり直しの導線も残っていることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("サムネイルはファイル選択と同時に描き、やり直しの導線も残す")
	void thumbnailsAreRenderedOnFileSelection() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String thumbnailList = read("static/js/components/pdf-thumbnail-list.js");

		assertAll(
				// アップロードを伴わなくなったため、利用者にボタンを押させる理由が無い。
				() -> assertTrue(pdfApp.contains("this.renderPdfThumbnails({ promptPassword: false });"),
						"ファイル選択時にサムネイルを描いてください。"),
				// 自動描画でパスワード入力欄を割り込ませない代わりに、押せばやり直せる導線を残す。
				() -> assertTrue(thumbnailList.contains("renderThumbnails()"),
						"サムネイルを描き直すボタンを残してください。"),
				() -> assertTrue(pdfApp.contains("ApiErrorUtils.isPasswordError([errorCode])"),
						"パスワード保護の判定は共通のapi-error-utils.jsを使ってください。"));
	}

	/**
	 * サムネイル画像とページ選択の見せ方が、既存のFE規約に沿っていることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("サムネイル表示がフロントエンド規約に沿う")
	void thumbnailRenderingFollowsFrontendConventions() throws IOException {
		String thumbnailList = read("static/js/components/pdf-thumbnail-list.js");
		String styles = read("static/css/main.css");

		assertAll(
				// data URIはinnerHTMLではなくsrcバインディングで渡す。
				() -> assertTrue(thumbnailList.contains(":src=\"page.dataUri\""),
						"サムネイル画像はsrcバインディングで渡してください。"),
				() -> assertFalse(thumbnailList.contains("innerHTML"), "HTMLを直接挿入しないでください。"),
				() -> assertTrue(thumbnailList.contains("v-for=\"page in thumbnailState.pages\" :key=\"page.pageNumber\""),
						"サムネイルの繰り返しにはkeyを付けてください。"),
				() -> assertTrue(thumbnailList.contains("pdf-thumbnail-list__item--selected"),
						"選択状態はclassで示してください。"),
				() -> assertTrue(styles.contains(".pdf-thumbnail-list__item--selected {"),
						"選択状態の見た目はmain.cssへ置いてください。"));
	}

	/**
	 * pdf.jsをCDNではなくvendor配置で持ち、日本語PDFに必要な資産まで同梱していることを確認する。
	 *
	 * @throws IOException resourceを読み込めない場合
	 */
	@Test
	@DisplayName("pdf.jsはCDNではなくvendor配置で、CMapと代替フォントまで同梱する")
	void pdfjsIsVendoredWithCmapsAndStandardFonts() throws IOException {
		String renderer = read("static/js/pdf/pdf-thumbnail-renderer.js");

		assertAll(
				() -> assertTrue(Files.exists(PDFJS_VENDOR_ROOT.resolve("build/pdf.min.mjs")), "pdf.js本体を同梱してください。"),
				() -> assertTrue(Files.exists(PDFJS_VENDOR_ROOT.resolve("build/pdf.worker.min.mjs")),
						"pdf.jsのworkerを同梱してください。"),
				// build配下だけ置くと、日本語PDFで文字が欠けたまま描画される。
				() -> assertTrue(containsFile(PDFJS_VENDOR_ROOT.resolve("cmaps")), "cmapsを同梱してください。"),
				() -> assertTrue(containsFile(PDFJS_VENDOR_ROOT.resolve("standard_fonts")),
						"standard_fontsを同梱してください。"),
				() -> assertTrue(renderer.contains("cMapUrl:"), "CMapの配信元をpdf.jsへ渡してください。"),
				() -> assertTrue(renderer.contains("standardFontDataUrl:"), "代替フォントの配信元をpdf.jsへ渡してください。"),
				() -> assertFalse(renderer.contains("https://"), "pdf.jsをCDNから読まないでください。"));
	}

	/**
	 * 同梱したpdf.jsのバージョン表記が、vendorのREADME・実装・型定義の置き場所で一致していることを確認する。
	 * <p>
	 * 本体とworkerのバージョンがずれるとpdf.jsは起動しない。差し替え時に片方だけ直す事故を防ぐため、
	 * 「どのバージョンを置いたか」の記録と実装の定数を突き合わせる。
	 * <p>
	 * 参照用の型定義（{@code docs/reference/pdfjs-<バージョン>}）も同じ値で突き合わせる。古い型定義は
	 * 「型定義が無い」よりたちが悪い。置いてあるものは正しいと見なして読まれるため、本体だけ更新すると
	 * 誤ったAPIが確信をもって使われる。
	 *
	 * @throws IOException resourceを読み込めない場合
	 */
	@Test
	@DisplayName("同梱したpdf.jsのバージョンがREADME・実装・型定義の置き場所で一致する")
	void pdfjsVersionIsRecordedConsistently() throws IOException {
		String renderer = read("static/js/pdf/pdf-thumbnail-renderer.js");
		String vendorReadme = Files.readString(PDFJS_VENDOR_ROOT.resolve("README.md"), StandardCharsets.UTF_8);

		Matcher rendererMatcher = RENDERER_VERSION.matcher(renderer);
		Matcher readmeMatcher = VENDOR_README_VERSION.matcher(vendorReadme);
		String rendererVersion = rendererMatcher.find() ? rendererMatcher.group(1) : "";
		Path typeReference = PDFJS_TYPE_REFERENCE_ROOT.resolve("pdfjs-" + rendererVersion);

		assertAll(
				() -> assertTrue(rendererMatcher.reset().find(),
						"pdf-thumbnail-renderer.jsへ PDFJS_VERSION を定義してください。"),
				() -> assertTrue(readmeMatcher.find(), "vendorのREADMEへバージョンを記録してください。"),
				() -> assertEquals(readmeMatcher.group(1).trim(), rendererVersion,
						"pdf.jsのバージョン表記をREADMEと実装でそろえてください。"),
				() -> assertTrue(Files.exists(typeReference.resolve("api.d.ts")),
						typeReference + "/api.d.ts を置いてください。本体を上げたら型定義も入れ替えます。"),
				() -> assertTrue(Files.exists(typeReference.resolve("pdf.d.ts")),
						typeReference + "/pdf.d.ts を置いてください。本体を上げたら型定義も入れ替えます。"),
				// 旧バージョンの型定義が残っていると、どちらが現物か分からなくなる。
				() -> assertEquals(1, countTypeReferenceDirectories(),
						"参照用の型定義は現在のバージョン1つだけにしてください。"));
	}

	/**
	 * 参照用の型定義ディレクトリ（{@code docs/reference/pdfjs-*}）の数を数える。
	 *
	 * @return ディレクトリ数
	 * @throws IOException ディレクトリを読めない場合
	 */
	private long countTypeReferenceDirectories() throws IOException {
		if (!Files.isDirectory(PDFJS_TYPE_REFERENCE_ROOT)) {
			return 0;
		}
		try (var entries = Files.list(PDFJS_TYPE_REFERENCE_ROOT)) {
			return entries.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().startsWith("pdfjs-")).count();
		}
	}

	/**
	 * 指定ディレクトリが1つ以上のファイルを持つか判定する。
	 *
	 * @param directory 判定するディレクトリ
	 * @return ファイルを持つ場合はtrue
	 * @throws IOException ディレクトリを読めない場合
	 */
	private boolean containsFile(Path directory) throws IOException {
		if (!Files.isDirectory(directory)) {
			return false;
		}
		try (var entries = Files.list(directory)) {
			return entries.anyMatch(Files::isRegularFile);
		}
	}

	/**
	 * 指定文字列の出現回数を数える。
	 *
	 * @param source 検索対象
	 * @param token  検索する文字列
	 * @return 出現回数
	 */
	private int countOccurrences(String source, String token) {
		int count = 0;
		int index = source.indexOf(token);
		while (index >= 0) {
			count++;
			index = source.indexOf(token, index + token.length());
		}
		return count;
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
