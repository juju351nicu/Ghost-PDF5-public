package com.clip.ghost.webcontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jsoup.Jsoup;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.response.ApiResultType;
import com.clip.ghost.webcontent.config.WebFetchProperties;
import com.clip.ghost.webcontent.config.WebMarkdownProperties;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftRequest;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftResponse;
import com.clip.ghost.webcontent.dto.WebUrlMarkdownDraftRequest;
import com.clip.ghost.webcontent.exception.WebInputException;
import com.clip.ghost.webcontent.exception.WebUnavailableException;
import com.clip.ghost.webcontent.logic.FetchedWebPage;
import com.clip.ghost.webcontent.logic.WebMarkdownBuilder;
import com.clip.ghost.webcontent.logic.WebPageContent;
import com.clip.ghost.webcontent.logic.WebPageExtractor;
import com.clip.ghost.webcontent.logic.WebPageFetcher;

/**
 * {@link WebMarkdownService} の入力検証、出力上限、レスポンス組み立てを検証するテスト。
 */
@ExtendWith(MockitoExtension.class)
class WebMarkdownServiceTest {
	private static final String HTML_FILE_PART_NAME = "htmlFile";
	private static final String HTML_CONTENT = "<html><body><p>本文</p></body></html>";

	@Mock
	private WebPageExtractor webPageExtractor;

	@Mock
	private WebMarkdownBuilder webMarkdownBuilder;

	@Mock
	private WebPageFetcher webPageFetcher;

	private final WebMarkdownProperties webMarkdownProperties = new WebMarkdownProperties();

	private final WebFetchProperties webFetchProperties = new WebFetchProperties();

	/**
	 * テスト対象を生成する。
	 * <p>
	 * 出力文字数の上限はテストごとに変えるため、{@code @InjectMocks} ではなくメソッドで組み立てる。
	 *
	 * @return テスト対象
	 */
	private WebMarkdownService createService() {
		return new WebMarkdownService(webPageExtractor, webMarkdownBuilder, webPageFetcher, webMarkdownProperties,
				webFetchProperties);
	}

	@ParameterizedTest
	@ValueSource(strings = { "page.html", "page.htm", "PAGE.HTML" })
	@DisplayName("HTMLの拡張子ならLogicへ委譲し、タイトルとMarkdownをレスポンスへ含める")
	void generateMarkdownFromHtmlDelegatesToLogic(String fileName) {
		doReturn(createContent()).when(webPageExtractor).extract(any(), any(), any());
		doReturn("# 設計メモ\n\n本文").when(webMarkdownBuilder).build(any(), eq(fileName), any());

		ResponseEntity<ApiResult<WebMarkdownDraftResponse>> result = createService()
				.generateMarkdownFromHtml(createRequest(fileName, HTML_CONTENT, StringUtils.EMPTY));

		verify(webPageExtractor, times(1)).extract(any(), any(), any());
		assertEquals(HttpStatus.OK, result.getStatusCode());
		WebMarkdownDraftResponse response = result.getBody().getData();
		assertEquals("設計メモ", response.getTitle());
		assertEquals("# 設計メモ\n\n本文", response.getMarkdown());
		assertFalse(response.getTruncated());
	}

	@Test
	@DisplayName("アップロードからの変換では取得元URLを持たないためsourceUrlはnullになる")
	void generateMarkdownFromHtmlLeavesSourceUrlNull() {
		doReturn(createContent()).when(webPageExtractor).extract(any(), any(), any());
		doReturn("# 設計メモ").when(webMarkdownBuilder).build(any(), any(), any());

		ResponseEntity<ApiResult<WebMarkdownDraftResponse>> result = createService()
				.generateMarkdownFromHtml(createRequest("page.html", HTML_CONTENT, StringUtils.EMPTY));

		assertNull(result.getBody().getData().getSourceUrl());
	}

	@Test
	@DisplayName("セレクタは前後の空白を落としてLogicへ渡す")
	void generateMarkdownFromHtmlTrimsSelector() {
		doReturn(createContent()).when(webPageExtractor).extract(any(), any(), eq("article"));
		doReturn("# 設計メモ").when(webMarkdownBuilder).build(any(), any(), any());

		createService().generateMarkdownFromHtml(createRequest("page.html", HTML_CONTENT, "  article  "));

		verify(webPageExtractor, times(1)).extract(any(), any(), eq("article"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "page.pdf", "page.txt", "page" })
	@DisplayName("HTML以外の拡張子はLogicを呼ばずWebInputExceptionになる")
	void generateMarkdownFromHtmlThrowsWhenExtensionIsNotSupported(String fileName) {
		WebMarkdownDraftRequest form = createRequest(fileName, HTML_CONTENT, StringUtils.EMPTY);
		WebMarkdownService service = createService();

		WebInputException exception = assertThrows(WebInputException.class,
				() -> service.generateMarkdownFromHtml(form));

		assertTrue(Strings.CS.contains(exception.getDisplayMessage(), ".html / .htm"));
		verify(webPageExtractor, never()).extract(any(), any(), any());
	}

	@Test
	@DisplayName("中身が空のファイルはLogicを呼ばずWebInputExceptionになる")
	void generateMarkdownFromHtmlThrowsWhenFileIsEmpty() {
		WebMarkdownDraftRequest form = createRequest("page.html", StringUtils.EMPTY, StringUtils.EMPTY);
		WebMarkdownService service = createService();

		WebInputException exception = assertThrows(WebInputException.class,
				() -> service.generateMarkdownFromHtml(form));

		assertTrue(Strings.CS.contains(exception.getDisplayMessage(), "空"));
		verify(webPageExtractor, never()).extract(any(), any(), any());
	}

	@Test
	@DisplayName("出力が上限を超えた場合は切り落とし、truncatedとWARNINGで通知する")
	void generateMarkdownFromHtmlTruncatesWhenOutputExceedsLimit() {
		webMarkdownProperties.setMaxOutputCharacters(10);
		doReturn(createContent()).when(webPageExtractor).extract(any(), any(), any());
		doReturn("0123456789abcdef").when(webMarkdownBuilder).build(any(), any(), any());

		ResponseEntity<ApiResult<WebMarkdownDraftResponse>> result = createService()
				.generateMarkdownFromHtml(createRequest("page.html", HTML_CONTENT, StringUtils.EMPTY));

		ApiResult<WebMarkdownDraftResponse> apiResult = result.getBody();
		assertEquals("0123456789", apiResult.getData().getMarkdown());
		assertTrue(apiResult.getData().getTruncated());
		assertEquals(ApiResultType.WARNING, apiResult.getResultType());
		assertEquals("webMarkdownTruncated", apiResult.getMessageList().get(0).code());
	}

	@Test
	@DisplayName("URL取得が無効な場合は取得を試みずWebUnavailableExceptionになる")
	void generateMarkdownFromUrlThrowsWhenFetchIsDisabled() {
		WebUrlMarkdownDraftRequest request = createUrlRequest("https://example.test/article", StringUtils.EMPTY);
		WebMarkdownService service = createService();

		assertThrows(WebUnavailableException.class, () -> service.generateMarkdownFromUrl(request));

		verify(webPageFetcher, never()).fetch(any());
	}

	@Test
	@DisplayName("URL取得が有効な場合は取得結果を解析し、最終URLをsourceUrlと出典に使う")
	void generateMarkdownFromUrlUsesFinalUrl() {
		webFetchProperties.setEnabled(true);
		doReturn(new FetchedWebPage("https://example.test/final", "UTF-8", HTML_CONTENT.getBytes(StandardCharsets.UTF_8)))
				.when(webPageFetcher).fetch("https://example.test/article");
		doReturn(createContent()).when(webPageExtractor).extract(any(), eq("https://example.test/final"), any(), any());
		doReturn("# 設計メモ").when(webMarkdownBuilder).build(any(), eq("https://example.test/final"), any());

		ResponseEntity<ApiResult<WebMarkdownDraftResponse>> result = createService()
				.generateMarkdownFromUrl(createUrlRequest("https://example.test/article", StringUtils.EMPTY));

		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("https://example.test/final", result.getBody().getData().getSourceUrl());
		assertEquals("# 設計メモ", result.getBody().getData().getMarkdown());
	}

	@Test
	@DisplayName("URL取得では応答の文字コードとセレクタをそのまま解析へ渡す")
	void generateMarkdownFromUrlPassesCharsetAndSelector() {
		webFetchProperties.setEnabled(true);
		doReturn(new FetchedWebPage("https://example.test/article", "Shift_JIS",
				HTML_CONTENT.getBytes(StandardCharsets.UTF_8))).when(webPageFetcher).fetch(any());
		doReturn(createContent()).when(webPageExtractor).extract(any(), any(), eq("article"), eq("Shift_JIS"));
		doReturn("# 設計メモ").when(webMarkdownBuilder).build(any(), any(), any());

		createService().generateMarkdownFromUrl(createUrlRequest("https://example.test/article", " article "));

		verify(webPageExtractor, times(1)).extract(any(), any(), eq("article"), eq("Shift_JIS"));
	}

	/**
	 * URLからのMarkdown下書きリクエストを生成する。
	 *
	 * @param url      取得先URL
	 * @param selector 本文を絞り込むCSSセレクタ
	 * @return Markdown下書きリクエスト
	 */
	private WebUrlMarkdownDraftRequest createUrlRequest(String url, String selector) {
		WebUrlMarkdownDraftRequest request = new WebUrlMarkdownDraftRequest();
		request.setUrl(url);
		request.setSelector(selector);
		return request;
	}

	/**
	 * Logicが返す抽出結果を生成する。
	 *
	 * @return 抽出結果
	 */
	private WebPageContent createContent() {
		return new WebPageContent("設計メモ", "説明文", Jsoup.parse(HTML_CONTENT).body());
	}

	/**
	 * HTMLファイルからのMarkdown下書きリクエストを生成する。
	 *
	 * @param fileName    アップロードファイル名
	 * @param content     ファイルの中身
	 * @param selector    本文を絞り込むCSSセレクタ
	 * @return Markdown下書きリクエスト
	 */
	private WebMarkdownDraftRequest createRequest(String fileName, String content, String selector) {
		WebMarkdownDraftRequest form = new WebMarkdownDraftRequest();
		form.setHtmlFile(new MockMultipartFile(HTML_FILE_PART_NAME, fileName, "text/html",
				content.getBytes(StandardCharsets.UTF_8)));
		form.setSelector(selector);
		return form;
	}
}
