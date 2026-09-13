package com.clip.ghost.officecontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.clip.ghost.pdfcontent.enums.CodeEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * 読み取り対象のOffice文書形式を表すenum。
 * <p>
 * 対応するのはOOXML（{@code .docx} / {@code .xlsx} / {@code .pptx}）だけにする。
 * 旧形式（{@code .doc} / {@code .xls} / {@code .ppt}）は {@code poi-scratchpad} が必要になるうえ、
 * 設計書資産の主流がOOXMLへ移っているため、依存を増やす価値が薄い。
 * <p>
 * 形式はリクエストで指定させず、アップロードされたファイルの拡張子から決める。
 * 利用者に自分で形式を選ばせると、選び間違いが「読めない」ではなく「壊れた変換結果」として出てしまう。
 */
@AllArgsConstructor
public enum OfficeDocumentType implements CodeEnum<String> {
	/** Word文書（OOXML）。 */
	DOCX("DOCX", "Word文書", "docx"),

	/** Excelブック（OOXML）。 */
	XLSX("XLSX", "Excelブック", "xlsx"),

	/** PowerPointプレゼンテーション（OOXML）。 */
	PPTX("PPTX", "PowerPointプレゼンテーション", "pptx");

	private static final String INVALID_KEY_MESSAGE = "Office形式はDOCX、XLSX、PPTXのいずれかで指定してください。";

	/** APIやフォームで扱うOffice形式コード。 */
	private final String key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** 対応するファイル拡張子（ドットを含まない）。 */
	private final String fileExtension;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<String, OfficeDocumentType> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(OfficeDocumentType::getKey, type -> type));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return Office形式コード
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
	 * 対応するファイル拡張子を取得する。
	 *
	 * @return 拡張子（ドットを含まない）
	 */
	public String getFileExtension() {
		return fileExtension;
	}

	/**
	 * コード値が不正な場合に利用者へ返す説明を取得する。
	 *
	 * @return Office形式コードが不正な場合の説明
	 */
	@Override
	public String getInvalidKeyMessage() {
		return INVALID_KEY_MESSAGE;
	}

	/**
	 * キー値からOffice形式を取得する。
	 *
	 * @param key Office形式コード
	 * @return キー値に対応するOffice形式
	 * @throws IllegalArgumentException 対応するOffice形式が存在しない場合
	 */
	@JsonCreator
	public static OfficeDocumentType fromKey(String key) {
		return KEY_MAP.values().stream().filter(type -> Strings.CI.equals(type.getKey(), key)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(INVALID_KEY_MESSAGE));
	}

	/**
	 * ファイル名の拡張子からOffice形式を判定する。
	 *
	 * @param fileName 判定対象のファイル名
	 * @return 拡張子に対応するOffice形式。対応する形式が無い場合はnull
	 */
	public static OfficeDocumentType fromFileName(String fileName) {
		return KEY_MAP.values().stream()
				.filter(type -> Strings.CI.endsWith(fileName, "." + type.getFileExtension())).findFirst().orElse(null);
	}
}
