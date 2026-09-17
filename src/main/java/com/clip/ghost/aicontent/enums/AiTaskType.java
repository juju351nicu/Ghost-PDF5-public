package com.clip.ghost.aicontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.clip.ghost.pdfcontent.enums.CodeEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * Markdown本文のAI変換タスクを表すenum。
 * <p>
 * APIが受け取る文字列（{@code SUMMARIZE} / {@code REFINE}）はJavaコード上の分岐だけこのenumへ寄せる。
 * {@code PdfMarkdownDraftMode} と同じ {@link CodeEnum} の形にそろえる。
 * 各タスクのプロンプト文言は {@code MarkdownAiPromptBuilder} に集約し、provider実装間で
 * プロンプトがずれないようにする。
 */
@AllArgsConstructor
public enum AiTaskType implements CodeEnum<String> {
	/** 要約：原文の要点を落とさず短くする。数値・固有名詞・結論は保持する。 */
	SUMMARIZE("SUMMARIZE", "要約"),

	/** 整形：意味を変えずに誤字脱字・表記ゆれ・Markdown構文の乱れを直す。原文の情報は削らない。 */
	REFINE("REFINE", "整形");

	private static final String INVALID_KEY_MESSAGE = "taskはSUMMARIZEまたはREFINEで指定してください。";

	/** APIやフォームで扱うタスクコード。 */
	private final String key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<String, AiTaskType> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(AiTaskType::getKey, task -> task));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return タスクコード
	 */
	@JsonValue
	@Override
	public String getKey() {
		return key;
	}

	/**
	 * 画面表示やログ説明に使うラベルを取得する。
	 *
	 * @return 表示ラベル
	 */
	@Override
	public String getValue() {
		return value;
	}

	/**
	 * コード値が不正な場合に利用者へ返す説明を取得する。
	 *
	 * @return タスクコードが不正な場合の説明
	 */
	@Override
	public String getInvalidKeyMessage() {
		return INVALID_KEY_MESSAGE;
	}

	/**
	 * キー値からタスクを取得する。
	 * <p>
	 * 他のCodeEnum実装（{@code PdfMarkdownDraftMode} 等）と同じく、大文字小文字は無視する。
	 *
	 * @param key タスクコード
	 * @return キー値に対応するタスク
	 * @throws IllegalArgumentException 対応するタスクが存在しない場合
	 */
	@JsonCreator
	public static AiTaskType fromKey(String key) {
		return KEY_MAP.values().stream().filter(task -> Strings.CI.equals(task.getKey(), key)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(INVALID_KEY_MESSAGE));
	}
}
