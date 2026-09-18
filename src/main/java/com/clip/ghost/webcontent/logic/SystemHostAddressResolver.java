package com.clip.ghost.webcontent.logic;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

import lombok.NoArgsConstructor;

/**
 * OSの名前解決をそのまま使う {@link HostAddressResolver} の実装。
 */
@Component
@NoArgsConstructor
public class SystemHostAddressResolver implements HostAddressResolver {
	/**
	 * ホスト名に対応するIPアドレスをすべて返す。
	 * <p>
	 * 1件目だけでなく全件を返す。1件目だけを見て判定すると、複数のAレコードのうち1つが
	 * 内部アドレスを指す設定（DNS rebindingの典型）を見逃す。
	 *
	 * @param host ホスト名またはIPアドレス文字列
	 * @return 対応するIPアドレス
	 * @throws UnknownHostException 名前解決できない場合
	 */
	@Override
	public InetAddress[] resolve(String host) throws UnknownHostException {
		return InetAddress.getAllByName(host);
	}
}
