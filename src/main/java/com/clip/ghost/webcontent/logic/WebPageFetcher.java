package com.clip.ghost.webcontent.logic;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.webcontent.config.WebFetchProperties;
import com.clip.ghost.webcontent.exception.WebFetchException;

/**
 * 指定されたURLからWebページを取得するクラス。
 * <p>
 * <strong>アプリから任意の宛先へ接続するのはこのクラスだけ</strong>にする。取得の入口が複数あると、
 * どの経路がSSRF検査を通っているのかを追えなくなる。この限定は
 * {@code CodingConventionTest.httpClientIsLimitedToWebPageFetcher} が機械的に守る。
 * <p>
 * リダイレクトはHTTPクライアントに任せず（{@link Redirect#NEVER}）、自前のループで追う。
 * クライアント側に任せると、転送先の名前解決と接続がライブラリの内側で完結し、ホップごとに
 * {@link WebAddressValidator} を通す隙間が無くなる。公開ドメインから内部アドレスへ1回転送するだけで
 * 検査を素通りできてしまう。
 * <p>
 * 取得の制限は次の通り。
 * <ul>
 * <li>ホップごとに宛先を検査し、{@code max-redirects} 回を超えたら失敗にする。</li>
 * <li>{@code Content-Type} は {@code text/html} / {@code application/xhtml+xml} のみ受け付ける。
 * PDFやZIPを掴んで別処理へ回さないため。</li>
 * <li>本文は {@code Content-Length} の宣言値ではなく<strong>実読み取りバイト数</strong>で
 * {@code max-bytes} に抑える。宣言値だけを見る実装は、小さく詐称した応答やchunked応答で素通りする。</li>
 * <li>接続・読み取りのタイムアウト、User-Agentの明示（偽装しない）、連続実行の間隔制限。</li>
 * </ul>
 * <p>
 * <strong>DNS rebindingに対して完全ではない。</strong> 検査は「名前解決 → IP判定」で行い、その後の接続は
 * ホスト名で行うため、検査時と接続時で解決結果が入れ替わる攻撃（TOCTOU）は防げない。完全に塞ぐには
 * 検査で得たIPへ直接接続し、TLSのホスト名検証とHostヘッダーを手当てする必要があり、HTTPクライアントの
 * 内部へ踏み込む実装になる。この機能は「利用者が自分のために1ページ取り込む」個人用ツールの範囲であり、
 * 既定が無効で、攻撃者が任意のURLを送り込める立場にもない。動く範囲で守れるものを守り、守れない範囲を
 * ここへ明記するほうを選ぶ。第三者が運用するインスタンスでこの機能を有効にしないこと（{@code SECURITY.md}）。
 */
@Component
public class WebPageFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebPageFetcher.class);
	private static final String USER_AGENT_HEADER = "User-Agent";
	private static final String ACCEPT_HEADER = "Accept";
	private static final String ACCEPT_HEADER_VALUE = "text/html,application/xhtml+xml";
	private static final String CONTENT_TYPE_HEADER = "Content-Type";
	private static final String LOCATION_HEADER = "Location";
	private static final String CHARSET_PARAMETER = "charset=";
	private static final List<String> SUPPORTED_CONTENT_TYPES = List.of("text/html", "application/xhtml+xml");
	private static final int STATUS_OK = 200;
	private static final int STATUS_REDIRECT_MIN = 300;
	private static final int STATUS_REDIRECT_MAX = 399;
	private static final String INTERVAL_MESSAGE = "取得の間隔が短すぎます。少し待ってからもう一度実行してください。";
	private static final String REQUEST_FAILURE_MESSAGE = "Webページを取得できませんでした。URLと接続を確認してください。";
	private static final String INTERRUPTED_MESSAGE = "Webページの取得が中断されました。";
	private static final String STATUS_MESSAGE = "Webページの取得に失敗しました。取得先の応答ステータス: %d";
	private static final String CONTENT_TYPE_MESSAGE = "HTML以外の応答のため取り込めません。取得先の種別: %s";
	private static final String UNKNOWN_CONTENT_TYPE = "不明";
	private static final String REDIRECT_LIMIT_MESSAGE = "リダイレクトが多すぎます。転送先のURLを直接指定してください。";
	private static final String REDIRECT_LOCATION_MESSAGE = "リダイレクト先が示されていないため取得できません。";
	private static final String SIZE_LIMIT_MESSAGE = "取得したページが上限の %d バイトを超えました。";
	private static final String READ_FAILURE_MESSAGE = "取得したページを読み取れませんでした。";

	private final WebFetchProperties webFetchProperties;
	private final WebAddressValidator webAddressValidator;
	private final HttpClient httpClient;

	/**
	 * 設定と宛先検査を指定して取得クラスを生成する。
	 * <p>
	 * HTTPクライアントはこのクラスの中だけで作る。外から渡せるようにすると、リダイレクト追跡を
	 * 有効にしたクライアントを差し込めてしまい、ホップごとの検査という前提が崩れる。
	 *
	 * @param webFetchProperties  取得の設定
	 * @param webAddressValidator 宛先の検査
	 */
	public WebPageFetcher(WebFetchProperties webFetchProperties, WebAddressValidator webAddressValidator) {
		this.webFetchProperties = webFetchProperties;
		this.webAddressValidator = webAddressValidator;
		this.httpClient = HttpClient.newBuilder().followRedirects(Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(webFetchProperties.getTimeoutSeconds())).build();
	}

	/** 直近の取得を開始した時刻（ミリ秒）。連続実行の間隔制限に使う。 */
	private final AtomicLong lastRequestMillis = new AtomicLong(0L);

	/**
	 * 指定されたURLからWebページを取得する。
	 *
	 * @param url 取得先URL
	 * @return 取得したページ
	 * @throws WebFetchException 取得に失敗した場合、または取得内容が制限を超えた場合
	 */
	public FetchedWebPage fetch(String url) {
		enforceMinInterval();
		URI currentUri = webAddressValidator.validate(url);
		for (int hop = 0; hop <= webFetchProperties.getMaxRedirects(); hop++) {
			HttpResponse<InputStream> response = send(currentUri);
			try (InputStream body = response.body()) {
				if (isRedirect(response.statusCode())) {
					currentUri = resolveRedirect(currentUri, response);
					continue;
				}
				validateStatus(response);
				validateContentType(response);
				LOGGER.info("Webページを取得しました。status={}", response.statusCode());
				return buildPage(currentUri, response, body);
			} catch (IOException e) {
				throw new WebFetchException(READ_FAILURE_MESSAGE, e);
			}
		}
		throw new WebFetchException(REDIRECT_LIMIT_MESSAGE);
	}

	/**
	 * 直近の取得からの経過時間を確認する。
	 * <p>
	 * 連打や繰り返し実行で取得先へ負荷を掛けないための、最小限の抑制。スケジューラもライブラリも
	 * 増やさず、直近の実行時刻だけで判定する。判定後は失敗しても時刻を進める（安全側に倒す）。
	 *
	 * @throws WebFetchException 直近の取得から設定値の時間が経っていない場合
	 */
	private void enforceMinInterval() {
		long now = System.currentTimeMillis();
		long previous = lastRequestMillis.getAndSet(now);
		if (previous > 0L && now - previous < webFetchProperties.getMinIntervalMillis()) {
			throw new WebFetchException(INTERVAL_MESSAGE);
		}
	}

	/**
	 * 1ホップ分のリクエストを送信する。
	 *
	 * @param uri 取得先URI
	 * @return 応答
	 * @throws WebFetchException 送信に失敗した、またはタイムアウトした場合
	 */
	private HttpResponse<InputStream> send(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri).GET()
				.header(USER_AGENT_HEADER, webFetchProperties.getUserAgent())
				.header(ACCEPT_HEADER, ACCEPT_HEADER_VALUE)
				.timeout(Duration.ofSeconds(webFetchProperties.getTimeoutSeconds())).build();
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (IOException e) {
			// 例外メッセージに取得先の解決済みIPが載らないよう、原因の詳細はログのdebugにも出さず包むだけにする。
			throw new WebFetchException(REQUEST_FAILURE_MESSAGE, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new WebFetchException(INTERRUPTED_MESSAGE, e);
		}
	}

	/**
	 * リダイレクト応答かを判定する。
	 *
	 * @param statusCode 応答ステータス
	 * @return リダイレクトの場合true
	 */
	private boolean isRedirect(int statusCode) {
		return statusCode >= STATUS_REDIRECT_MIN && statusCode <= STATUS_REDIRECT_MAX;
	}

	/**
	 * リダイレクト先を決め、宛先として許されるかを検査する。
	 *
	 * @param currentUri 現在のURI
	 * @param response   リダイレクト応答
	 * @return 検査を通したリダイレクト先URI
	 * @throws WebFetchException リダイレクト先が示されていない場合
	 */
	private URI resolveRedirect(URI currentUri, HttpResponse<InputStream> response) {
		String location = header(response, LOCATION_HEADER);
		if (StringUtils.isBlank(location)) {
			throw new WebFetchException(REDIRECT_LOCATION_MESSAGE);
		}
		// 相対パスのLocationも受け取るため、現在のURIを基準に解決してから検査する。
		return webAddressValidator.validate(currentUri.resolve(StringUtils.trim(location)).toString());
	}

	/**
	 * 応答ステータスが200であることを確認する。
	 *
	 * @param response 応答
	 * @throws WebFetchException 200以外の場合
	 */
	private void validateStatus(HttpResponse<InputStream> response) {
		if (response.statusCode() != STATUS_OK) {
			// ステータスコードは取得先が誰にでも返す値で、内部構成の情報にはならないため画面へ出してよい。
			throw new WebFetchException(STATUS_MESSAGE.formatted(response.statusCode()));
		}
	}

	/**
	 * 応答のContent-TypeがHTMLであることを確認する。
	 *
	 * @param response 応答
	 * @throws WebFetchException HTML以外の場合
	 */
	private void validateContentType(HttpResponse<InputStream> response) {
		String contentType = header(response, CONTENT_TYPE_HEADER);
		String mediaType = StringUtils.trim(StringUtils.substringBefore(contentType, ";"));
		if (!Strings.CI.equalsAny(mediaType, SUPPORTED_CONTENT_TYPES.toArray(String[]::new))) {
			throw new WebFetchException(
					CONTENT_TYPE_MESSAGE.formatted(StringUtils.defaultIfBlank(mediaType, UNKNOWN_CONTENT_TYPE)));
		}
	}

	/**
	 * 取得結果を組み立てる。
	 *
	 * @param currentUri 最終URI
	 * @param response   応答
	 * @param body       応答本文
	 * @return 取得したページ
	 * @throws WebFetchException 本文がサイズ上限を超えた、または読み取れなかった場合
	 */
	private FetchedWebPage buildPage(URI currentUri, HttpResponse<InputStream> response, InputStream body) {
		return new FetchedWebPage(currentUri.toString(), resolveCharsetName(response), readBoundedBody(body));
	}

	/**
	 * 応答本文を上限付きで読み取る。
	 * <p>
	 * 上限より1バイトだけ多く読む。読み切れてしまった場合と、上限で止まった場合を区別するため。
	 * 途中までのHTMLを正常な取得結果として返すと、本文が欠けたMarkdownが出来上がり、
	 * 利用者はそれが欠けていることに気付けない。上限を超えた時点で失敗として扱う。
	 *
	 * @param body 応答本文
	 * @return 読み取ったバイト列
	 * @throws WebFetchException サイズ上限を超えた、または読み取れなかった場合
	 */
	private byte[] readBoundedBody(InputStream body) {
		long maxBytes = webFetchProperties.getMaxBytes();
		try (BoundedInputStream boundedStream = BoundedInputStream.builder().setInputStream(body)
				.setMaxCount(maxBytes + 1L).get()) {
			byte[] content = IOUtils.toByteArray(boundedStream);
			if (content.length > maxBytes) {
				throw new WebFetchException(SIZE_LIMIT_MESSAGE.formatted(maxBytes));
			}
			return content;
		} catch (IOException e) {
			throw new WebFetchException(READ_FAILURE_MESSAGE, e);
		}
	}

	/**
	 * 応答のContent-Typeから文字コード名を取り出す。
	 *
	 * @param response 応答
	 * @return 文字コード名。指定が無ければnull
	 */
	private String resolveCharsetName(HttpResponse<InputStream> response) {
		String contentType = header(response, CONTENT_TYPE_HEADER);
		int parameterIndex = Strings.CI.indexOf(contentType, CHARSET_PARAMETER);
		if (parameterIndex < 0) {
			return null;
		}
		String charsetName = StringUtils.substring(contentType, parameterIndex + CHARSET_PARAMETER.length());
		// charset="utf-8" のように引用符で囲む応答もあるため、囲みを外してから渡す。
		return StringUtils.trimToNull(StringUtils.strip(StringUtils.substringBefore(charsetName, ";"), "\"'"));
	}

	/**
	 * 応答ヘッダーの値を1件取り出す。
	 *
	 * @param response   応答
	 * @param headerName ヘッダー名
	 * @return ヘッダーの値。無ければ空文字
	 */
	private String header(HttpResponse<InputStream> response, String headerName) {
		Optional<String> value = response.headers().firstValue(headerName);
		return value.orElse(StringUtils.EMPTY);
	}
}
