package com.clip.ghost.webcontent.logic;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import com.clip.ghost.webcontent.config.WebFetchProperties;
import com.clip.ghost.webcontent.exception.WebFetchBlockedException;
import com.clip.ghost.webcontent.exception.WebInputException;

import lombok.RequiredArgsConstructor;

/**
 * 取得先URLが接続してよい宛先かを検査するクラス。
 * <p>
 * SSRF（サーバーに任意の宛先へ接続させる攻撃）を止める中心。URLの形だけを見る検査と、
 * 名前解決の結果を見る検査の両方をここへ集める。{@link WebPageFetcher} はリダイレクトの
 * ホップごとにこのクラスを通す。1回目のURLしか検査しないと、公開ドメインから内部アドレスへ
 * 転送するだけで検査を素通りできてしまう。
 * <p>
 * 検査は次の順で行う。
 * <ol>
 * <li>スキームが {@code http} / {@code https} のみ（{@code file} / {@code ftp} / {@code jar} /
 * {@code data} / {@code gopher} などは拒否）。</li>
 * <li>ポートが {@code ghost.web.fetch.allowed-ports}（既定80 / 443）に含まれること。</li>
 * <li>ユーザー情報付きURL（{@code https://user:pass@host/}）でないこと。</li>
 * <li>名前解決した全アドレスが、ループバック・プライベート・リンクローカル・ワイルドカード・
 * マルチキャストのいずれにも該当しないこと。1件でも該当したら拒否する。</li>
 * </ol>
 * 4の判定は、IPv4射影IPv6（{@code ::ffff:x.x.x.x}）を射影元のIPv4として見る。射影表記を
 * そのまま {@code Inet6Address} として判定すると、{@code ::ffff:127.0.0.1} のような表記で
 * ループバック判定を抜けられる。
 */
@Component
@RequiredArgsConstructor
public class WebAddressValidator {
	private static final String SCHEME_HTTP = "http";
	private static final String SCHEME_HTTPS = "https";
	private static final int DEFAULT_HTTP_PORT = 80;
	private static final int DEFAULT_HTTPS_PORT = 443;
	private static final int IPV4_MAPPED_PREFIX_LENGTH = 12;
	private static final int IPV6_ADDRESS_LENGTH = 16;
	private static final int UNIQUE_LOCAL_PREFIX_MASK = 0xFE;
	private static final int UNIQUE_LOCAL_PREFIX_VALUE = 0xFC;
	private static final String INVALID_URL_MESSAGE = "URLの書式が正しくありません。http:// または https:// で始まるURLを指定してください。";
	private static final String INVALID_SCHEME_MESSAGE = "http / https 以外のURLは取得できません。";
	private static final String INVALID_PORT_MESSAGE = "このポートへの取得は許可されていません。標準のポート（80 / 443）のURLを指定してください。";
	private static final String USER_INFO_MESSAGE = "ユーザー情報付きのURL（user:pass@host の形）は取得できません。";
	private static final String UNKNOWN_HOST_MESSAGE = "指定されたホスト名を解決できませんでした。URLを確認してください。";

	private final WebFetchProperties webFetchProperties;
	private final HostAddressResolver hostAddressResolver;

	/**
	 * 取得先URLを検査し、接続してよいURIを返す。
	 *
	 * @param url 取得先URL
	 * @return 検査を通したURI
	 * @throws WebInputException        URLの書式、スキーム、ポート、ユーザー情報が許可されない場合
	 * @throws WebFetchBlockedException 名前解決の結果が接続を許さない範囲だった場合
	 */
	public URI validate(String url) {
		URI uri = parseUri(url);
		validateScheme(uri);
		validateUserInfo(uri);
		validatePort(uri);
		validateAddresses(uri.getHost());
		return uri;
	}

	/**
	 * URL文字列をURIへ変換する。
	 *
	 * @param url 取得先URL
	 * @return 変換したURI
	 * @throws WebInputException URLとして解釈できない、または絶対URLでない場合
	 */
	private URI parseUri(String url) {
		if (StringUtils.isBlank(url)) {
			throw new WebInputException(INVALID_URL_MESSAGE);
		}
		try {
			URI uri = new URI(StringUtils.trim(url));
			if (!uri.isAbsolute() || StringUtils.isBlank(uri.getHost())) {
				throw new WebInputException(INVALID_URL_MESSAGE);
			}
			return uri;
		} catch (URISyntaxException e) {
			throw new WebInputException(INVALID_URL_MESSAGE, e);
		}
	}

	/**
	 * スキームを検査する。
	 *
	 * @param uri 取得先URI
	 * @throws WebInputException {@code http} / {@code https} 以外の場合
	 */
	private void validateScheme(URI uri) {
		if (!Strings.CI.equalsAny(uri.getScheme(), SCHEME_HTTP, SCHEME_HTTPS)) {
			throw new WebInputException(INVALID_SCHEME_MESSAGE);
		}
	}

	/**
	 * ユーザー情報が付いていないことを確認する。
	 * <p>
	 * 資格情報を含むURLをサーバーが代理で叩くと、その資格情報がサーバーのログや
	 * 取得先のアクセスログへ残る。取得元の認証を代行しない方針とも合わない。
	 *
	 * @param uri 取得先URI
	 * @throws WebInputException ユーザー情報が含まれる場合
	 */
	private void validateUserInfo(URI uri) {
		if (StringUtils.isNotBlank(uri.getUserInfo())) {
			throw new WebInputException(USER_INFO_MESSAGE);
		}
	}

	/**
	 * ポートを検査する。
	 *
	 * @param uri 取得先URI
	 * @throws WebInputException 許可されていないポートの場合
	 */
	private void validatePort(URI uri) {
		int port = uri.getPort() < 0 ? defaultPort(uri.getScheme()) : uri.getPort();
		List<Integer> allowedPorts = webFetchProperties.getAllowedPorts();
		if (CollectionUtils.isEmpty(allowedPorts) || !allowedPorts.contains(port)) {
			throw new WebInputException(INVALID_PORT_MESSAGE);
		}
	}

	/**
	 * スキームの既定ポートを返す。
	 *
	 * @param scheme スキーム
	 * @return 既定ポート
	 */
	private int defaultPort(String scheme) {
		return Strings.CI.equals(scheme, SCHEME_HTTPS) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
	}

	/**
	 * ホスト名を解決し、全アドレスが接続を許す範囲かを確認する。
	 *
	 * @param host 取得先ホスト名
	 * @throws WebInputException        名前解決できない場合
	 * @throws WebFetchBlockedException 1件でも接続を許さない範囲だった場合
	 */
	private void validateAddresses(String host) {
		InetAddress[] addresses = resolve(host);
		for (InetAddress address : addresses) {
			if (isBlocked(address)) {
				// 例外にも、この後のログにも、解決結果のIPは載せない。内部ネットワークの構成を外へ返さないため。
				throw new WebFetchBlockedException(host);
			}
		}
	}

	/**
	 * ホスト名を解決する。
	 *
	 * @param host 取得先ホスト名
	 * @return 解決したアドレス
	 * @throws WebInputException 名前解決できない場合
	 */
	private InetAddress[] resolve(String host) {
		try {
			InetAddress[] addresses = hostAddressResolver.resolve(host);
			if (ArrayUtils.isEmpty(addresses)) {
				throw new WebInputException(UNKNOWN_HOST_MESSAGE);
			}
			return addresses;
		} catch (UnknownHostException e) {
			throw new WebInputException(UNKNOWN_HOST_MESSAGE, e);
		}
	}

	/**
	 * 接続を許さないアドレスかを判定する。
	 * <p>
	 * リンクローカルの判定には、クラウドのメタデータendpoint（{@code 169.254.169.254}）も含まれる。
	 * ここを通すと、インスタンスの資格情報を取得させる典型的なSSRFが成立する。
	 *
	 * @param address 判定対象のアドレス
	 * @return 接続を許さない場合true
	 */
	private boolean isBlocked(InetAddress address) {
		InetAddress target = unwrapIpv4Mapped(address);
		if (target.isLoopbackAddress()) {
			return !webFetchProperties.isAllowLoopback();
		}
		return target.isAnyLocalAddress() || target.isSiteLocalAddress() || target.isLinkLocalAddress()
				|| target.isMulticastAddress() || isUniqueLocalIpv6(target);
	}

	/**
	 * IPv4射影IPv6を射影元のIPv4として扱う。
	 *
	 * @param address 判定対象のアドレス
	 * @return 射影元のIPv4アドレス。射影でない場合は入力そのもの
	 */
	private InetAddress unwrapIpv4Mapped(InetAddress address) {
		if (!(address instanceof Inet6Address inet6Address)) {
			return address;
		}
		byte[] bytes = inet6Address.getAddress();
		if (!isIpv4MappedPrefix(bytes)) {
			return address;
		}
		try {
			byte[] ipv4Bytes = new byte[4];
			System.arraycopy(bytes, IPV4_MAPPED_PREFIX_LENGTH, ipv4Bytes, 0, ipv4Bytes.length);
			return InetAddress.getByAddress(ipv4Bytes);
		} catch (UnknownHostException e) {
			// getByAddressは長さ4のバイト列で失敗しないが、失敗したときは判定できないものとして元の値で続ける。
			return address;
		}
	}

	/**
	 * IPv4射影IPv6（{@code ::ffff:0:0/96}）のprefixかを判定する。
	 *
	 * @param bytes IPv6アドレスのバイト列
	 * @return 射影prefixの場合true
	 */
	private boolean isIpv4MappedPrefix(byte[] bytes) {
		if (bytes.length != IPV6_ADDRESS_LENGTH) {
			return false;
		}
		for (int index = 0; index < 10; index++) {
			if (bytes[index] != 0) {
				return false;
			}
		}
		return bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
	}

	/**
	 * IPv6のユニークローカルアドレス（{@code fc00::/7}）かを判定する。
	 * <p>
	 * {@code isSiteLocalAddress} はIPv6では {@code fec0::/10}（廃止済みのサイトローカル）しか
	 * 見ないため、現在の内部ネットワークで使われる {@code fc00::/7} を別途判定する。
	 *
	 * @param address 判定対象のアドレス
	 * @return ユニークローカルの場合true
	 */
	private boolean isUniqueLocalIpv6(InetAddress address) {
		if (!(address instanceof Inet6Address inet6Address)) {
			return false;
		}
		byte[] bytes = inet6Address.getAddress();
		return bytes.length == IPV6_ADDRESS_LENGTH
				&& (bytes[0] & UNIQUE_LOCAL_PREFIX_MASK) == UNIQUE_LOCAL_PREFIX_VALUE;
	}
}
