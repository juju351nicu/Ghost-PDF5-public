package com.clip.ghost.webcontent.logic;

/**
 * URLから取得したWebページの中身。
 * <p>
 * 解析はここでは行わない。取得（{@link WebPageFetcher}）と解析（{@link WebPageExtractor}）を
 * 分けることで、HTMLファイルのアップロード経路と同じ解析処理を使い回せる。
 *
 * @param finalUrl    リダイレクトを追い終えた最終URL。相対URLの絶対化と出典表示に使う
 * @param charsetName 応答の {@code Content-Type} が示す文字コード。指定が無ければnull
 * @param content     取得した本文のバイト列
 */
public record FetchedWebPage(String finalUrl, String charsetName, byte[] content) {
}
