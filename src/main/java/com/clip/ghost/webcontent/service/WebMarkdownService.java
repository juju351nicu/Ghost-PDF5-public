package com.clip.ghost.webcontent.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.response.ApiMessage;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.utils.PathUtils;
import com.clip.ghost.webcontent.config.WebFetchProperties;
import com.clip.ghost.webcontent.config.WebMarkdownProperties;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftRequest;
import com.clip.ghost.webcontent.dto.WebMarkdownDraftResponse;
import com.clip.ghost.webcontent.dto.WebUrlMarkdownDraftRequest;
import com.clip.ghost.webcontent.enums.WebMarkdownDraftMode;
import com.clip.ghost.webcontent.exception.WebInputException;
import com.clip.ghost.webcontent.exception.WebProcessingException;
import com.clip.ghost.webcontent.exception.WebUnavailableException;
import com.clip.ghost.webcontent.logic.FetchedWebPage;
import com.clip.ghost.webcontent.logic.WebMarkdownBuilder;
import com.clip.ghost.webcontent.logic.WebPageContent;
import com.clip.ghost.webcontent.logic.WebPageExtractor;
import com.clip.ghost.webcontent.logic.WebPageFetcher;

import lombok.RequiredArgsConstructor;

/**
 * WebページからMarkdown下書きを起こすサービス。
 * <p>
 * 入口は2つある。HTMLファイルのアップロード（ネットワークへ出ない）と、URLからの取得
 * （{@code ghost.web.fetch.enabled} が有効なときだけ）。取得・解析・組み立てはLogicへ委譲し、
 * このクラスは入力の検証、機能の有効判定、出力文字数の上限、レスポンスDTOの組み立てを担当する。
 * <p>
 * 抽出とMarkdown組み立ては2つの入口で共有する。取得元がファイルかURLかで結果が変わると、
 * 「ブラウザで保存して取り込んだ場合」と「URLを渡した場合」で出力の説明が二重になる。
 * <p>
 * 結果は自動保存しない。取り込んだ内容をそのまま保存すると、利用者が中身を確認する前に
 * 他者のページの複製が手元へ残ることになる。保存は利用者が明示的に実行したときだけ行う。
 */
@Service
@RequiredArgsConstructor
public class WebMarkdownService {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebMarkdownService.class);
	private static final String UNKNOWN_FILE_NAME = "（ファイル名不明）";
	private static final List<String> SUPPORTED_EXTENSIONS = List.of("html", "htm");
	private static final String EMPTY_FILE_MESSAGE = "HTMLファイルの中身が空です。";
	private static final String UNSUPPORTED_EXTENSION_MESSAGE = "HTMLとして読み込めないファイルです。.html / .htm を指定してください。";
	private static final String READ_FAILURE_MESSAGE = "アップロードされたHTMLを読み取れませんでした。";
	private static final String TRUNCATED_MESSAGE_CODE = "webMarkdownTruncated";
	private static final String TRUNCATED_MESSAGE = "取り込み結果が上限の %d 文字を超えたため、以降を切り落としました。必要な部分だけを取り込むにはセレクタを指定してください。";

	private final WebPageExtractor webPageExtractor;
	private final WebMarkdownBuilder webMarkdownBuilder;
	private final WebPageFetcher webPageFetcher;
	private final WebMarkdownProperties webMarkdownProperties;
	private final WebFetchProperties webFetchProperties;

	/**
	 * アップロードされたHTMLファイルからMarkdown下書きを起こす。
	 *
	 * @param form 変換対象のHTMLファイルとセレクタを含むフォーム
	 * @return 起こしたMarkdown下書きを含むレスポンス
	 * @throws WebInputException      HTMLとして扱えない、または本文を取り出せない場合
	 * @throws WebProcessingException アップロードファイルを読み取れなかった場合
	 */
	public ResponseEntity<ApiResult<WebMarkdownDraftResponse>> generateMarkdownFromHtml(
			WebMarkdownDraftRequest form) {
		Objects.requireNonNull(form, "form must not be null.");
		MultipartFile htmlFile = form.getHtmlFile();
		String fileName = StringUtils.defaultIfBlank(htmlFile.getOriginalFilename(), UNKNOWN_FILE_NAME);
		validateHtmlFile(htmlFile, fileName);
		WebPageContent content = extractContent(htmlFile, form.getSelector());
		// 取得日時は「いつ時点のページか」を示す出典情報のため、変換のたびに実時刻を入れる。
		String markdown = webMarkdownBuilder.build(content, fileName, LocalDateTime.now(),
				resolveMode(form.getMode()));
		LOGGER.info("HTMLからMarkdown下書きを起こしました。markdownLength={}", StringUtils.length(markdown));
		return buildResult(content.title(), markdown, null);
	}

	/**
	 * 指定されたURLのWebページからMarkdown下書きを起こす。
	 * <p>
	 * 取得は {@link WebPageFetcher} だけが行い、抽出と組み立てはHTMLアップロード経路と同じものを使う。
	 * 同じページなら、ファイルから読んでもURLから取っても同じMarkdownになる。
	 *
	 * @param request 取得先URLとセレクタを含むリクエスト
	 * @return 起こしたMarkdown下書きを含むレスポンス
	 * @throws WebUnavailableException URL取得機能が無効な場合
	 */
	public ResponseEntity<ApiResult<WebMarkdownDraftResponse>> generateMarkdownFromUrl(
			WebUrlMarkdownDraftRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		if (!webFetchProperties.isEnabled()) {
			throw new WebUnavailableException("URLからのWebページ取得機能は無効です。");
		}
		FetchedWebPage page = webPageFetcher.fetch(request.getUrl());
		WebPageContent content = extractFetchedContent(page, request.getSelector());
		String markdown = webMarkdownBuilder.build(content, page.finalUrl(), LocalDateTime.now(),
				resolveMode(request.getMode()));
		LOGGER.info("URLからMarkdown下書きを起こしました。markdownLength={}", StringUtils.length(markdown));
		return buildResult(content.title(), markdown, page.finalUrl());
	}

	/**
	 * 取得したページを解析し、Markdownへ写す対象を取り出す。
	 * <p>
	 * 基準URLにはリダイレクトを追い終えた最終URLを使う。転送前のURLを基準にすると、
	 * 転送先のページに書かれた相対リンクが別のパスへ向いてしまう。
	 *
	 * @param page     取得したページ
	 * @param selector 本文を絞り込むCSSセレクタ
	 * @return Markdownへ写す対象
	 */
	private WebPageContent extractFetchedContent(FetchedWebPage page, String selector) {
		try (InputStream inputStream = new ByteArrayInputStream(page.content())) {
			return webPageExtractor.extract(inputStream, page.finalUrl(), StringUtils.trim(selector),
					page.charsetName());
		} catch (IOException e) {
			throw new WebProcessingException(READ_FAILURE_MESSAGE, e);
		}
	}

	/**
	 * 出力モードを決める。
	 * <p>
	 * 未指定なら本文だけ（{@link WebMarkdownDraftMode#ARTICLE}）。既定値をenumやリクエストDTOへ
	 * 持たせず、ここで決める。「未指定」をenumの値にすると、APIが受け取れる文字列が増えてしまう。
	 *
	 * @param mode リクエストで指定された出力モード。未指定ならnull
	 * @return 適用する出力モード
	 */
	private WebMarkdownDraftMode resolveMode(WebMarkdownDraftMode mode) {
		return Objects.isNull(mode) ? WebMarkdownDraftMode.ARTICLE : mode;
	}

	/**
	 * アップロードファイルがHTMLとして扱えるかを検証する。
	 * <p>
	 * 中身がHTMLかどうかは拡張子だけでは決まらないが、対象外の拡張子をここで落とすことで、
	 * PDFやZIPを解析に掛けて壊れた結果を返すことは避けられる。
	 *
	 * @param htmlFile アップロードされたHTMLファイル
	 * @param fileName アップロードファイルの名前
	 * @throws WebInputException 中身が空、または対応していない拡張子の場合
	 */
	private void validateHtmlFile(MultipartFile htmlFile, String fileName) {
		if (htmlFile.isEmpty()) {
			throw new WebInputException(EMPTY_FILE_MESSAGE);
		}
		String extension = PathUtils.getExtension(fileName);
		if (!Strings.CI.equalsAny(extension, SUPPORTED_EXTENSIONS.toArray(String[]::new))) {
			throw new WebInputException(UNSUPPORTED_EXTENSION_MESSAGE);
		}
	}

	/**
	 * アップロードファイルを解析し、Markdownへ写す対象を取り出す。
	 * <p>
	 * 基準URLは空文字を渡す。アップロードされたHTMLには取得元URLが無いため、相対URLの絶対化は
	 * HTML内の {@code <base href>} がある場合だけ効く。取得元URLを基準にできるのは Stage 2 から。
	 *
	 * @param htmlFile アップロードされたHTMLファイル
	 * @param selector 本文を絞り込むCSSセレクタ
	 * @return Markdownへ写す対象
	 * @throws WebProcessingException アップロードファイルを読み取れなかった場合
	 */
	private WebPageContent extractContent(MultipartFile htmlFile, String selector) {
		try (InputStream inputStream = htmlFile.getInputStream()) {
			return webPageExtractor.extract(inputStream, StringUtils.EMPTY, StringUtils.trim(selector));
		} catch (IOException e) {
			throw new WebProcessingException(READ_FAILURE_MESSAGE, e);
		}
	}

	/**
	 * 出力文字数の上限を適用し、レスポンスを組み立てる。
	 * <p>
	 * 上限で切った場合はWARNINGとして通知する。黙って切ると、利用者は「ページの後半が無い」ことに
	 * 気付かないままMarkdownメモを完成品として扱ってしまう。
	 *
	 * @param title     取り込んだページのタイトル
	 * @param markdown  起こしたMarkdown本文
	 * @param sourceUrl 取得元URL。アップロードからの変換ではnull
	 * @return 共通ラッパーで包んだレスポンス
	 */
	private ResponseEntity<ApiResult<WebMarkdownDraftResponse>> buildResult(String title, String markdown,
			String sourceUrl) {
		int maxCharacters = webMarkdownProperties.getMaxOutputCharacters();
		boolean truncated = StringUtils.length(markdown) > maxCharacters;
		String outputMarkdown = truncated ? StringUtils.substring(markdown, 0, maxCharacters) : markdown;
		WebMarkdownDraftResponse response = buildResponse(title, outputMarkdown, truncated, sourceUrl);
		if (truncated) {
			LOGGER.info("取り込み結果を上限で切り落としました。maxCharacters={}", maxCharacters);
			return ResponseEntity.ok(ApiResult.warning(response,
					List.of(new ApiMessage(TRUNCATED_MESSAGE_CODE, TRUNCATED_MESSAGE.formatted(maxCharacters)))));
		}
		return ResponseEntity.ok(ApiResult.of(response));
	}

	/**
	 * レスポンスDTOを組み立てる。
	 *
	 * @param title     取り込んだページのタイトル
	 * @param markdown  起こしたMarkdown本文
	 * @param truncated 上限で切り落としたか
	 * @param sourceUrl 取得元URL。アップロードからの変換ではnull
	 * @return レスポンスDTO
	 */
	private WebMarkdownDraftResponse buildResponse(String title, String markdown, boolean truncated,
			String sourceUrl) {
		WebMarkdownDraftResponse response = new WebMarkdownDraftResponse();
		response.setMarkdown(markdown);
		response.setTitle(title);
		response.setTruncated(truncated);
		response.setSourceUrl(sourceUrl);
		return response;
	}
}
