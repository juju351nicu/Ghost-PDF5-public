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
import com.clip.ghost.webcontent.config.WebMarkdownProperties;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftRequest;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftResponse;
import com.clip.ghost.webcontent.exception.WebInputException;
import com.clip.ghost.webcontent.logic.WebMarkdownBuilder;
import com.clip.ghost.webcontent.logic.WebPageContent;
import com.clip.ghost.webcontent.logic.WebPageExtractor;

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

	private final WebMarkdownProperties webMarkdownProperties = new WebMarkdownProperties();

	/**
	 * テスト対象を生成する。
	 * <p>
	 * 出力文字数の上限はテストごとに変えるため、{@code @InjectMocks} ではなくメソッドで組み立てる。
	 *
	 * @return テスト対象
	 */
	private WebMarkdownService createService() {
		return new WebMarkdownService(webPageExtractor, webMarkdownBuilder, webMarkdownProperties);
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
