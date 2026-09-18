package com.clip.ghost.webcontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Webページ取り込みで生成するMarkdownの出力サイズに関する設定。
 * <p>
 * Webページは1枚でも本文・コメント・関連記事が延々と続くことがあり、そのままMarkdown欄へ入れると
 * ブラウザの編集が重くなるうえ、後続のAI整形（{@code POST /markdownAiTransform}）の入力上限
 * （既定24,000文字）も一度に超える。上限で切り落とし、切ったことを利用者へ伝える。
 * <p>
 * {@code ghost.ai.markdown} と同じく、prefix単位で1クラスにする。Stage 2（URL取得）で追加する
 * {@code ghost.web.fetch.*} は、この設定と用途が異なるため別クラスとして足す。
 */
@ConfigurationProperties(prefix = "ghost.web.markdown")
@Getter
@Setter
public class WebMarkdownProperties {
	/** Webページから起こすMarkdownの出力文字数上限。超過分は切り落とし、切ったことを通知する。 */
	private int maxOutputCharacters = 100_000;
}
