package com.clip.ghost.webcontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.clip.ghost.webcontent.config.WebFetchProperties;
import com.clip.ghost.webcontent.exception.WebFetchBlockedException;
import com.clip.ghost.webcontent.exception.WebInputException;

/**
 * {@link WebAddressValidator} のSSRF検査を検証するテスト。
 * <p>
 * 名前解決はテスト用の {@link HostAddressResolver} で差し替える。実DNSに依存させると、
 * 実行環境のネットワークや社内DNSの設定で結果が変わり、「禁止範囲を拒否できているか」を
 * 確かめられなくなる。
 */
class WebAddressValidatorTest {
	private static final String PUBLIC_HOST = "example.test";
	private static final String PUBLIC_ADDRESS = "93.184.216.34";

	private final WebFetchProperties webFetchProperties = new WebFetchProperties();

	/**
	 * 指定したアドレスを返す検査器を生成する。
	 *
	 * @param addresses 名前解決の結果として返すアドレス文字列
	 * @return テスト対象
	 */
	private WebAddressValidator createValidator(String... addresses) {
		return new WebAddressValidator(webFetchProperties, host -> toAddresses(addresses));
	}

	/**
	 * アドレス文字列を {@link InetAddress} へ変換する。
	 * <p>
	 * IPアドレスの文字列表現からの変換は名前解決を伴わないため、DNSには出ない。
	 *
	 * @param addresses アドレス文字列
	 * @return 変換したアドレス
	 */
	private InetAddress[] toAddresses(String... addresses) {
		try {
			InetAddress[] resolved = new InetAddress[addresses.length];
			for (int index = 0; index < addresses.length; index++) {
				resolved[index] = InetAddress.getByName(addresses[index]);
			}
			return resolved;
		} catch (UnknownHostException e) {
			throw new IllegalStateException("テスト用アドレスを組み立てられませんでした。", e);
		}
	}

	@Test
	@DisplayName("公開アドレスのhttps URLはそのまま通す")
	void validateAcceptsPublicHttpsUrl() {
		URI uri = createValidator(PUBLIC_ADDRESS).validate("https://" + PUBLIC_HOST + "/article");

		assertEquals("https://" + PUBLIC_HOST + "/article", uri.toString());
	}

	@ParameterizedTest
	@DisplayName("ループバック・プライベート・リンクローカル・ワイルドカード・マルチキャストへの接続を拒否する")
	@ValueSource(strings = { "127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.169.254", "0.0.0.0",
			"::1", "fc00::1", "fe80::1", "224.0.0.1", "::ffff:127.0.0.1" })
	void validateRejectsBlockedAddresses(String address) {
		WebAddressValidator validator = createValidator(address);

		WebFetchBlockedException exception = assertThrows(WebFetchBlockedException.class,
				() -> validator.validate("https://" + PUBLIC_HOST + "/"));

		// 例外が持つのは利用者が指定したホスト名だけで、解決結果のIPは持たない。
		assertEquals(PUBLIC_HOST, exception.getHost());
	}

	@Test
	@DisplayName("解決結果が複数ある場合、1件でも禁止範囲なら拒否する")
	void validateRejectsWhenAnyResolvedAddressIsBlocked() {
		WebAddressValidator validator = createValidator(PUBLIC_ADDRESS, "127.0.0.1");

		assertThrows(WebFetchBlockedException.class, () -> validator.validate("https://" + PUBLIC_HOST + "/"));
	}

	@Test
	@DisplayName("allow-loopbackが有効な場合だけループバックへの接続を許す")
	void validateAcceptsLoopbackOnlyWhenAllowed() {
		webFetchProperties.setAllowLoopback(true);
		webFetchProperties.setAllowedPorts(List.of(80, 443, 8080));

		URI uri = createValidator("127.0.0.1").validate("http://localhost:8080/page");

		assertEquals("http://localhost:8080/page", uri.toString());
	}

	@Test
	@DisplayName("allow-loopbackが有効でもプライベートアドレスは拒否する")
	void validateStillRejectsPrivateAddressWhenLoopbackIsAllowed() {
		webFetchProperties.setAllowLoopback(true);
		WebAddressValidator validator = createValidator("10.0.0.1");

		assertThrows(WebFetchBlockedException.class, () -> validator.validate("https://" + PUBLIC_HOST + "/"));
	}

	@ParameterizedTest
	@DisplayName("http / https 以外のスキームを拒否する")
	@ValueSource(strings = { "file:///etc/passwd", "ftp://example.test/file", "jar:file:///tmp/a.jar!/b",
			"gopher://example.test/", "data:text/html,<p>a</p>" })
	void validateRejectsUnsupportedScheme(String url) {
		WebAddressValidator validator = createValidator(PUBLIC_ADDRESS);

		assertThrows(WebInputException.class, () -> validator.validate(url));
	}

	@Test
	@DisplayName("ユーザー情報付きURLを拒否する")
	void validateRejectsUrlWithUserInfo() {
		WebAddressValidator validator = createValidator(PUBLIC_ADDRESS);

		WebInputException exception = assertThrows(WebInputException.class,
				() -> validator.validate("http://user:pass@" + PUBLIC_HOST + "/"));

		assertEquals("ユーザー情報付きのURL（user:pass@host の形）は取得できません。", exception.getDisplayMessage());
	}

	@ParameterizedTest
	@DisplayName("許可されていないポートを拒否する")
	@ValueSource(strings = { "http://example.test:8080/", "https://example.test:8443/", "http://example.test:22/" })
	void validateRejectsDisallowedPort(String url) {
		WebAddressValidator validator = createValidator(PUBLIC_ADDRESS);

		assertThrows(WebInputException.class, () -> validator.validate(url));
	}

	@ParameterizedTest
	@DisplayName("URLとして解釈できない入力を拒否する")
	@ValueSource(strings = { "example.test/article", "https://", "   ", "ht tp://example.test/" })
	void validateRejectsMalformedUrl(String url) {
		WebAddressValidator validator = createValidator(PUBLIC_ADDRESS);

		assertThrows(WebInputException.class, () -> validator.validate(url));
	}

	@Test
	@DisplayName("名前解決できないホストは入力エラーとして扱う")
	void validateRejectsUnknownHost() {
		WebAddressValidator validator = new WebAddressValidator(webFetchProperties, host -> {
			throw new UnknownHostException(host);
		});

		assertThrows(WebInputException.class, () -> validator.validate("https://" + PUBLIC_HOST + "/"));
	}
}
