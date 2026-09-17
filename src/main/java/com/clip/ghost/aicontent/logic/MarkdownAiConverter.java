package com.clip.ghost.aicontent.logic;

import com.clip.ghost.aicontent.enums.AiTaskType;

/**
 * Markdown本文をAIで整形・要約する変換器の抽象。
 * <p>
 * {@code imagecontent.logic.ImageToMarkdownConverter} と同じ形にし、引数だけ画像専用の
 * {@code byte[] imageBytes, String mediaType} からテキスト専用の {@code String markdown, AiTaskType taskType} へ変える。
 * Controller / Serviceはこのinterface越しに使い、実装差し替えの影響を受けないようにする。外部AIのSDK依存は、
 * 実装クラスの内部に閉じ込める。
 */
public interface MarkdownAiConverter {
	/**
	 * 変換が利用可能かを返す。
	 * <p>
	 * 機能の有効化フラグや必要な資格情報の有無を含めて判定する。
	 *
	 * @return 利用可能な場合はtrue
	 */
	boolean isEnabled();

	/**
	 * 実装の識別文字列を返す。ログや診断に用いる。資格情報やMarkdown本文は含めない。
	 *
	 * @return 実装を説明する文字列
	 */
	String describe();

	/**
	 * Markdown本文を指定タスクでAI変換する。
	 *
	 * @param markdown 変換対象のMarkdown本文
	 * @param taskType 変換タスク（整形／要約）
	 * @return 変換結果のMarkdown
	 */
	String transform(String markdown, AiTaskType taskType);

	/**
	 * この変換器のprovider識別子を返す。設定 {@code ghost.ai.provider} と突き合わせて選択に使う。
	 *
	 * @return provider識別子（例: {@code anthropic} / {@code openai}）
	 */
	String provider();
}
