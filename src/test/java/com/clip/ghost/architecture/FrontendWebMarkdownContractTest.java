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

import com.clip.ghost.webcontent.enums.WebMarkdownDraftMode;

/**
 * Webページ取り込みについて、サーバとフロントエンドの取り決めが食い違わないことを固定するテスト。
 * <p>
 * ずれは「その操作を選んだときだけ落ちる」形で出るため、手で試す限り見逃しやすい。
 * URLとモードの取り決め、機能が既定で無効であること、画面の注意書きを機械的に確認する。
 * <p>
 * npmなしのWebJar Vue構成のため、JavaScriptのソースを文字列として読む方式を既存の契約テストから踏襲する。
 */
class FrontendWebMarkdownContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
	private static final Path CONTROLLER_SOURCE = Paths
			.get("src/main/java/com/clip/ghost/webcontent/controller/WebMarkdownController.java");
	private static final String FORM_STATE_PATH = "static/js/models/pdf-form-state.js";
	private static final String CONST_PATH = "static/js/const.js";
	private static final String FORM_COMPONENT_PATH = "static/js/components/web-markdown-form.js";
	private static final String API_CLIENT_PATH = "static/js/api/markdown-api-client.js";
	private static final String APPLICATION_YAML_PATH = "application.yml";
	private static final Path PATH_UTILS_SOURCE = Paths.get("src/main/java/com/clip/ghost/common/utils/PathUtils.java");

	/**
	 * ファイル名に使えない文字。サーバ（{@code PathUtils}）とフロントエンドで同じものを弾く。
	 * <p>
	 * 比較するのは文字そのもので、ソース上のエスケープの書き方（{@code \\} か {@code \\\\} か）は問わない。
	 * 正規表現の書き方はJavaとJavaScriptで違うため、そこまで一致させると規則の同一性を確認できない。
	 */
	private static final List<String> UNSAFE_FILE_NAME_CHARS = List.of("\\", "/", ":", "*", "?", "\"", "<", ">", "|");

	/**
	 * 出力モードの選択肢が {@link WebMarkdownDraftMode} のコード値と一致することを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("出力モードの選択肢がWebMarkdownDraftModeのコード値と一致する")
	void modeChoicesMatchServerEnum() throws IOException {
		String formState = read(FORM_STATE_PATH);

		assertTrue(formState.contains("const createWebMarkdownModeItems"));
		Arrays.stream(WebMarkdownDraftMode.values()).forEach(
				mode -> assertTrue(formState.contains("id: \"" + mode.getKey() + "\""),
						() -> "出力モード " + mode.getKey() + " を画面の選択肢へ追加してください。"));
	}

	/**
	 * 画面が呼ぶURLが、サーバが公開しているパスと一致することを確認する。
	 *
	 * @throws IOException ソースを読み込めない場合
	 */
	@Test
	@DisplayName("Webページ取り込みのURLがサーバのendpointと一致する")
	void requestPathsMatchServerEndpoints() throws IOException {
		String constants = read(CONST_PATH);
		String controller = Files.readString(CONTROLLER_SOURCE, StandardCharsets.UTF_8);

		assertAll(() -> assertTrue(constants.contains("MARKDOWN_DRAFT_HTML: \"/markdownDraftHtml\"")),
				() -> assertTrue(constants.contains("MARKDOWN_DRAFT_URL: \"/markdownDraftUrl\"")),
				() -> assertTrue(controller.contains("value = \"/markdownDraftHtml\"")),
				() -> assertTrue(controller.contains("value = \"/markdownDraftUrl\"")));
	}

	/**
	 * 取り込み操作が責務別JavaScript境界を通ってAPIへ接続されることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("HTML取り込みとURL取り込みが責務別JavaScript境界を通ってAPIへ接続される")
	void webMarkdownOperationsUseExistingFrontendBoundaries() throws IOException {
		String mainTemplate = read("templates/main.html");
		String formComponent = read(FORM_COMPONENT_PATH);
		String apiClient = read(API_CLIENT_PATH);
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(() -> assertTrue(mainTemplate.contains("@request-web-markdown=\"requestWebMarkdownDraft\"")),
				() -> assertTrue(mainTemplate.contains("@request-web-markdown-url=\"requestWebMarkdownUrlDraft\"")),
				() -> assertTrue(formComponent.contains("this.$emit(\"request-web-markdown\")")),
				() -> assertTrue(formComponent.contains("this.$emit(\"request-web-markdown-url\")")),
				() -> assertTrue(apiClient.contains("const requestHtmlMarkdownDraft")),
				() -> assertTrue(apiClient.contains("const requestUrlMarkdownDraft")),
				() -> assertTrue(pdfApp.contains("MarkdownApiClient.requestHtmlMarkdownDraft(")),
				() -> assertTrue(pdfApp.contains("MarkdownApiClient.requestUrlMarkdownDraft(")));
	}

	/**
	 * URL取得が既定で無効のままであることを確認する。
	 * <p>
	 * サーバが利用者指定の宛先へ接続する機能のため、既定値を有効へ変えるのは方針の変更にあたる。
	 * 設定ファイルの書き換えだけで静かに既定が変わらないよう、ここで固定する。
	 *
	 * @throws IOException 設定ファイルを読み込めない場合
	 */
	@Test
	@DisplayName("URL取得は設定の既定値が無効のまま")
	void urlFetchStaysDisabledByDefault() throws IOException {
		String applicationYaml = read(APPLICATION_YAML_PATH);

		assertTrue(applicationYaml.contains("enabled: ${GHOST_WEB_FETCH_ENABLED:false}"),
				"URL取得の既定値は無効のままにしてください。");
	}

	/**
	 * 取得対象の線引きを画面が利用者へ伝えていることを確認する。
	 * <p>
	 * 何を取り込んでよいかの判断は利用者に委ねるため、注意書きが消えると判断材料が無くなる。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	@DisplayName("取得してよいページの線引きを画面に表示する")
	void formShowsUsageNotice() throws IOException {
		String formComponent = read(FORM_COMPONENT_PATH);

		assertAll(() -> assertTrue(formComponent.contains("ログインが必要なページ")),
				() -> assertTrue(formComponent.contains("利用規約")),
				() -> assertTrue(formComponent.contains("権利")));
	}

	/**
	 * 保存候補名の不正文字を、サーバの保存時と同じ規則で置き換えていることを確認する。
	 * <p>
	 * ページタイトルには {@code |} や {@code :} が普通に含まれる。規則がずれると、画面に出したファイル名と
	 * 実際に保存される名前が食い違い、保存後に一覧から探せなくなる。
	 *
	 * @throws IOException ソースを読み込めない場合
	 */
	@Test
	@DisplayName("保存候補名の不正文字をサーバと同じ規則で置き換える")
	void fileNameSanitizingMatchesServerRule() throws IOException {
		String pdfApp = read("static/js/pdf/pdf-app.js");
		String pathUtils = Files.readString(PATH_UTILS_SOURCE, StandardCharsets.UTF_8);

		assertAll(() -> assertTrue(pdfApp.contains("toFileNameSafeText")),
				() -> assertTrue(pdfApp.contains("this.toFileNameSafeText(title)")),
				() -> assertTrue(UNSAFE_FILE_NAME_CHARS.stream().allMatch(pdfApp::contains),
						"サーバと同じ禁止文字をフロントエンドでも置き換えてください。"),
				() -> assertTrue(UNSAFE_FILE_NAME_CHARS.stream().allMatch(pathUtils::contains),
						"サーバ側の禁止文字が変わりました。フロントエンドの置き換え規則も合わせてください。"));
	}

	/**
	 * フロントエンドresourceを読み込む。
	 *
	 * @param relativePath {@code src/main/resources} からの相対パス
	 * @return ファイル内容
	 * @throws IOException 読み込めない場合
	 */
	private String read(String relativePath) throws IOException {
		return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
