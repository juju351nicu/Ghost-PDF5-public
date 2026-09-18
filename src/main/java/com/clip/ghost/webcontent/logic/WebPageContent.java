package com.clip.ghost.webcontent.logic;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * HTMLから取り出した、Markdownへ写す対象。
 * <p>
 * 本文は要素の一覧ではなく「本文の根の要素」で持つ。Webページの本文は {@code div} が何重にも
 * 入れ子になっているのが普通で、平坦な一覧へ落とすと入れ子のリスト・表・引用の対応関係が消える。
 * <p>
 * {@code root} は不要な要素（{@code script} / {@code nav} 等）を除去し、セレクタ指定があれば
 * その配下へ絞り込んだ後のもの。{@code document} は<strong>除去前</strong>の完全なDOMで、
 * 構造レポート（{@code mode=STRUCTURE}）が使う。本文から外したいナビゲーションやフッターこそ、
 * 「このサイトがページをどう組み立てているか」を見るときの対象になるため、両方を持つ。
 * <p>
 * 参照先のDOMは呼び出し元で変更しない前提とする。
 *
 * @param title       ページタイトル。{@code <title>} が空なら最初の {@code <h1>}
 * @param description {@code <meta name="description">} の内容。無ければ空文字
 * @param root        Markdownへ写す本文の根の要素（不要要素の除去・セレクタ絞り込み済み）
 * @param document    除去前の完全なDOM。構造レポート用
 */
public record WebPageContent(String title, String description, Element root, Document document) {
}
