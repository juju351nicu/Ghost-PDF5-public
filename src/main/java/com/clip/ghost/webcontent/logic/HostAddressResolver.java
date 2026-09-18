package com.clip.ghost.webcontent.logic;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ホスト名をIPアドレスへ解決する処理の入口。
 * <p>
 * SSRF検査の要は「名前解決の結果がどのIPか」であり、そこが実際のDNSに依存していると、
 * 禁止範囲の判定を自動テストで確かめられない（社内DNSやネットワーク環境ごとに結果が変わる）。
 * 解決だけをこのinterfaceへ出し、テストでは任意のアドレス列を差し込めるようにする。
 * <p>
 * 本番実装は {@link SystemHostAddressResolver} の1つだけで、切り替えのための抽象ではない。
 */
public interface HostAddressResolver {
	/**
	 * ホスト名に対応するIPアドレスをすべて返す。
	 *
	 * @param host ホスト名またはIPアドレス文字列
	 * @return 対応するIPアドレス
	 * @throws UnknownHostException 名前解決できない場合
	 */
	InetAddress[] resolve(String host) throws UnknownHostException;
}
