package com.clip.ghost.webcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.webcontent.config.WebFetchProperties;
import com.clip.ghost.webcontent.exception.WebFetchBlockedException;
import com.clip.ghost.webcontent.exception.WebFetchException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * {@link WebPageFetcher} の取得挙動を、ループバックで起動したHTTPサーバー相手に検証するテスト。
 * <p>
 * 外部サイトへは接続しない。実在サイトを相手にすると、相手の都合で結果が変わるうえ、
 * リダイレクト回数やContent-Typeのような条件を狙って作れない。
 * <p>
 * ループバック宛はSSRF検査で既定拒否のため、このテストだけ {@code allow-loopback} を有効にする。
 * 併せて、標準ポート以外を使うため {@code allowed-ports} にテストサーバーのポートを入れる。
 */
class WebPageFetcherIntegrationTest {
	private static final String HTML_BODY = "<html><head><title>テストページ</title></head><body><p>本文</p></body></html>";
	private static final String HTML_CONTENT_TYPE = "text/html; charset=UTF-8";
	private static final int STATUS_OK = 200;
	private static final int STATUS_FOUND = 302;
	private static final int STATUS_NOT_FOUND = 404;

	private HttpServer httpServer;
	private WebFetchProperties webFetchProperties;
	private final AtomicReference<String> lastUserAgent = new AtomicReference<>();

	@BeforeEach
	void setUp() throws IOException {
		httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		registerHandlers();
		httpServer.start();
		webFetchProperties = new WebFetchProperties();
		webFetchProperties.setAllowLoopback(true);
		webFetchProperties.setAllowedPorts(List.of(httpServer.getAddress().getPort()));
		// 間隔制限は専用のテストだけで確かめる。他のテストが直前の実行に引きずられないよう既定は0にする。
		webFetchProperties.setMinIntervalMillis(0L);
	}

	@AfterEach
	void tearDown() {
		httpServer.stop(0);
	}

	@Test
	@DisplayName("HTMLを取得し、最終URLと文字コードと本文を返す")
	void fetchReturnsHtmlContent() {
		FetchedWebPage page = createFetcher().fetch(url("/page"));

		assertEquals(url("/page"), page.finalUrl());
		assertEquals("UTF-8", page.charsetName());
		assertEquals(HTML_BODY, new String(page.content(), StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("User-Agentを設定値どおりに送り、ブラウザを偽装しない")
	void fetchSendsConfiguredUserAgent() {
		createFetcher().fetch(url("/page"));

		assertEquals("Ghost-PDF5", lastUserAgent.get());
	}

	@Test
	@DisplayName("リダイレクトを上限回数まで追い、最終URLを返す")
	void fetchFollowsRedirectsUpToLimit() {
		FetchedWebPage page = createFetcher().fetch(url("/redirect1"));

		assertEquals(url("/page"), page.finalUrl());
	}

	@Test
	@DisplayName("リダイレクトが上限を超えたら取得しない")
	void fetchRejectsTooManyRedirects() {
		WebPageFetcher fetcher = createFetcher();

		WebFetchException exception = assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/loop1")));

		assertTrue(Strings.CS.contains(exception.getMessage(), "リダイレクト"));
	}

	@Test
	@DisplayName("相対パスのLocationも現在のURLを基準に解決する")
	void fetchResolvesRelativeRedirect() {
		FetchedWebPage page = createFetcher().fetch(url("/relative-redirect"));

		assertEquals(url("/page"), page.finalUrl());
	}

	@Test
	@DisplayName("Locationの無いリダイレクト応答は失敗として扱う")
	void fetchRejectsRedirectWithoutLocation() {
		WebPageFetcher fetcher = createFetcher();

		assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/no-location")));
	}

	@Test
	@DisplayName("HTML以外のContent-Typeは取り込まない")
	void fetchRejectsNonHtmlContentType() {
		WebPageFetcher fetcher = createFetcher();

		WebFetchException exception = assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/pdf")));

		assertTrue(Strings.CS.contains(exception.getMessage(), "application/pdf"));
	}

	@Test
	@DisplayName("200以外の応答は取得先のステータス付きで失敗として扱う")
	void fetchRejectsNonOkStatus() {
		WebPageFetcher fetcher = createFetcher();

		WebFetchException exception = assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/not-found")));

		assertTrue(Strings.CS.contains(exception.getMessage(), "404"));
	}

	@Test
	@DisplayName("Content-Lengthを宣言しないchunked応答でも、実読み取りバイト数で上限を判定する")
	void fetchRejectsOversizedChunkedBody() {
		webFetchProperties.setMaxBytes(64L);
		WebPageFetcher fetcher = createFetcher();

		WebFetchException exception = assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/chunked-large")));

		assertTrue(Strings.CS.contains(exception.getMessage(), "上限"));
	}

	@Test
	@DisplayName("Content-Lengthを宣言した応答でも、実読み取りバイト数で上限を判定する")
	void fetchRejectsOversizedDeclaredBody() {
		webFetchProperties.setMaxBytes(64L);
		WebPageFetcher fetcher = createFetcher();

		assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/declared-large")));
	}

	@Test
	@DisplayName("応答が返らない場合はタイムアウトで打ち切る")
	void fetchTimesOutWhenResponseIsSlow() {
		webFetchProperties.setTimeoutSeconds(1);
		WebPageFetcher fetcher = createFetcher();

		assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/slow")));
	}

	@Test
	@DisplayName("直近の取得から間隔が空いていない場合は取得しない")
	void fetchRejectsWhenIntervalIsTooShort() {
		webFetchProperties.setMinIntervalMillis(10_000L);
		WebPageFetcher fetcher = createFetcher();
		fetcher.fetch(url("/page"));

		WebFetchException exception = assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/page")));

		assertTrue(Strings.CS.contains(exception.getMessage(), "間隔"));
	}

	@Test
	@DisplayName("Content-Typeが示す文字コードを取得結果へ渡す")
	void fetchKeepsCharsetFromContentType() {
		FetchedWebPage page = createFetcher().fetch(url("/shift-jis"));

		assertEquals("Shift_JIS", page.charsetName());
	}

	@Test
	@DisplayName("扱えない文字コードが宣言されていても取得し、判定はHTML側へ委ねる")
	void fetchIgnoresUnsupportedCharset() {
		FetchedWebPage page = createFetcher().fetch(url("/bogus-charset"));

		// 存在しない文字コード名をそのまま解析へ渡すと、解析側が例外になり500へ落ちる。
		assertNull(page.charsetName());
	}

	@Test
	@DisplayName("圧縮された応答は、中身を取り違えないよう失敗として扱う")
	void fetchRejectsCompressedResponse() {
		WebPageFetcher fetcher = createFetcher();

		WebFetchException exception = assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/gzip")));

		assertTrue(Strings.CS.contains(exception.getMessage(), "圧縮"));
	}

	@Test
	@DisplayName("パーセントエンコードされたリダイレクト先も追える")
	void fetchFollowsEncodedRedirect() {
		FetchedWebPage page = createFetcher().fetch(url("/redirect-encoded"));

		assertTrue(Strings.CS.contains(page.finalUrl(), "%E6%97%A5%E6%9C%AC%E8%AA%9E"));
	}

	@Test
	@DisplayName("規格に反する生の非ASCIIのLocationは、取得先の失敗（502相当）として扱う")
	void fetchRejectsRawNonAsciiRedirect() {
		WebPageFetcher fetcher = createFetcher();

		// ヘッダーはASCIIで送る決まりのため、生の日本語を書く取得先とはそもそも通信が成立しない。
		// 500（アプリの障害）ではなく取得失敗として扱えていることを確かめる。
		assertThrows(WebFetchException.class, () -> fetcher.fetch(url("/redirect-non-ascii")));
	}

	@Test
	@DisplayName("日本語を含むURLをそのまま渡しても取得できる")
	void fetchAcceptsNonAsciiUrl() {
		FetchedWebPage page = createFetcher().fetch(url("/日本語"));

		assertEquals(HTML_BODY, new String(page.content(), StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("allow-loopbackが無効ならループバック宛の取得を拒否する")
	void fetchRejectsLoopbackWhenNotAllowed() {
		webFetchProperties.setAllowLoopback(false);
		WebPageFetcher fetcher = createFetcher();

		assertThrows(WebFetchBlockedException.class, () -> fetcher.fetch(url("/page")));
	}

	/**
	 * テスト対象を生成する。
	 *
	 * @return テスト対象
	 */
	private WebPageFetcher createFetcher() {
		return new WebPageFetcher(webFetchProperties,
				new WebAddressValidator(webFetchProperties, new SystemHostAddressResolver()));
	}

	/**
	 * テストサーバーのURLを組み立てる。
	 *
	 * @param path パス
	 * @return URL
	 */
	private String url(String path) {
		return "http://127.0.0.1:" + httpServer.getAddress().getPort() + path;
	}

	/**
	 * テスト用のハンドラーを登録する。
	 */
	private void registerHandlers() {
		httpServer.createContext("/page", exchange -> {
			lastUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
			respond(exchange, STATUS_OK, HTML_CONTENT_TYPE, HTML_BODY.getBytes(StandardCharsets.UTF_8));
		});
		httpServer.createContext("/redirect1", exchange -> redirect(exchange, url("/redirect2")));
		httpServer.createContext("/redirect2", exchange -> redirect(exchange, url("/redirect3")));
		httpServer.createContext("/redirect3", exchange -> redirect(exchange, url("/page")));
		httpServer.createContext("/loop1", exchange -> redirect(exchange, url("/loop2")));
		httpServer.createContext("/loop2", exchange -> redirect(exchange, url("/loop3")));
		httpServer.createContext("/loop3", exchange -> redirect(exchange, url("/loop4")));
		httpServer.createContext("/loop4", exchange -> redirect(exchange, url("/page")));
		httpServer.createContext("/relative-redirect", exchange -> redirect(exchange, "/page"));
		httpServer.createContext("/no-location", exchange -> {
			exchange.sendResponseHeaders(STATUS_FOUND, -1);
			exchange.close();
		});
		httpServer.createContext("/pdf",
				exchange -> respond(exchange, STATUS_OK, "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)));
		httpServer.createContext("/not-found",
				exchange -> respond(exchange, STATUS_NOT_FOUND, HTML_CONTENT_TYPE, new byte[0]));
		httpServer.createContext("/chunked-large", exchange -> {
			exchange.getResponseHeaders().set("Content-Type", HTML_CONTENT_TYPE);
			// 応答長に0を指定するとchunked転送になり、Content-Lengthは送られない。
			exchange.sendResponseHeaders(STATUS_OK, 0);
			try (OutputStream body = exchange.getResponseBody()) {
				body.write(new byte[4096]);
			}
			exchange.close();
		});
		httpServer.createContext("/declared-large",
				exchange -> respond(exchange, STATUS_OK, HTML_CONTENT_TYPE, new byte[4096]));
		httpServer.createContext("/shift-jis", exchange -> respond(exchange, STATUS_OK, "text/html; charset=Shift_JIS",
				HTML_BODY.getBytes(StandardCharsets.UTF_8)));
		httpServer.createContext("/bogus-charset", exchange -> respond(exchange, STATUS_OK,
				"text/html; charset=utf-8-bogus", HTML_BODY.getBytes(StandardCharsets.UTF_8)));
		httpServer.createContext("/gzip", exchange -> {
			exchange.getResponseHeaders().set("Content-Encoding", "gzip");
			respond(exchange, STATUS_OK, HTML_CONTENT_TYPE, HTML_BODY.getBytes(StandardCharsets.UTF_8));
		});
		httpServer.createContext("/redirect-non-ascii", exchange -> redirect(exchange, "/日本語"));
		httpServer.createContext("/redirect-encoded",
				exchange -> redirect(exchange, "/%E6%97%A5%E6%9C%AC%E8%AA%9E"));
		httpServer.createContext("/日本語",
				exchange -> respond(exchange, STATUS_OK, HTML_CONTENT_TYPE, HTML_BODY.getBytes(StandardCharsets.UTF_8)));
		httpServer.createContext("/slow", exchange -> {
			sleepQuietly();
			respond(exchange, STATUS_OK, HTML_CONTENT_TYPE, HTML_BODY.getBytes(StandardCharsets.UTF_8));
		});
	}

	/**
	 * 応答を返す。
	 *
	 * @param exchange    やり取り
	 * @param status      応答ステータス
	 * @param contentType Content-Type
	 * @param body        応答本文
	 * @throws IOException 応答に失敗した場合
	 */
	private void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
		if (body.length > 0) {
			try (OutputStream responseBody = exchange.getResponseBody()) {
				responseBody.write(body);
			}
		}
		exchange.close();
	}

	/**
	 * リダイレクト応答を返す。
	 *
	 * @param exchange やり取り
	 * @param location 転送先
	 * @throws IOException 応答に失敗した場合
	 */
	private void redirect(HttpExchange exchange, String location) throws IOException {
		exchange.getResponseHeaders().set("Location", location);
		exchange.sendResponseHeaders(STATUS_FOUND, -1);
		exchange.close();
	}

	/**
	 * タイムアウトを起こすために応答を遅らせる。
	 */
	private void sleepQuietly() {
		try {
			Thread.sleep(3_000L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
