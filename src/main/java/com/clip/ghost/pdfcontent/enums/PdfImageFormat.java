package com.clip.ghost.pdfcontent.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

/**
 * PDFページを画像化するときの出力フォーマットを表すenum。
 * <p>
 * 4形式ともJDK標準のImageIOだけで書き出せる。PNG / JPEG / BMPは従来から、TIFFもJava 9以降で
 * 書き出せるため、画像ライブラリを追加していない。
 * <p>
 * JPEGとBMPはアルファチャンネルを持てない。透過を含む {@code BufferedImage} をそのまま渡すと、
 * JPEGは色が化け、BMPは書き出しに失敗する。この違いを呼び出し側のif文へ散らさず
 * {@link #requiresOpaqueImage()} としてenumへ持たせる。
 */
@AllArgsConstructor
public enum PdfImageFormat implements CodeEnum<String> {
	/** 可逆圧縮。文字と線画がにじまないため既定にする。 */
	PNG("PNG", "PNG（可逆・文字がにじまない）", "png", "png", "image/png", false),

	/** 非可逆圧縮。写真主体のPDFでファイルサイズが小さくなる。 */
	JPG("JPG", "JPG（非可逆・写真向き）", "jpg", "jpg", "image/jpeg", true),

	/** 複数ページを1ファイルへまとめられる形式だが、ここでは1ページ1ファイルで出力する。 */
	TIFF("TIFF", "TIFF（可逆・印刷向き）", "tiff", "tif", "image/tiff", false),

	/** 無圧縮。互換性重視の古い業務システム向け。 */
	BMP("BMP", "BMP（無圧縮）", "bmp", "bmp", "image/bmp", true);

	private static final String INVALID_KEY_MESSAGE = "画像形式はPNG、JPG、TIFF、BMPのいずれかで指定してください。";

	/** APIやフォームで扱う画像形式コード。 */
	private final String key;

	/** 画面表示やログ説明に使うラベル。 */
	private final String value;

	/** ImageIOの書き出しフォーマット名。 */
	private final String imageIoFormatName;

	/** 出力ファイルの拡張子。 */
	private final String fileExtension;

	/** 出力ファイルのMIME type。 */
	private final String mimeType;

	/** アルファチャンネルを持てず、不透明な画像へ変換してから書き出す必要がある場合true。 */
	private final boolean opaqueRequired;

	/** キー値からenumを取得するためのMap。 */
	private static final Map<String, PdfImageFormat> KEY_MAP = Arrays.stream(values())
			.collect(Collectors.toUnmodifiableMap(PdfImageFormat::getKey, format -> format));

	/**
	 * APIやフォームで扱う外向きのコード値を取得する。
	 *
	 * @return 画像形式コード
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
	 * ImageIOの書き出しフォーマット名を取得する。
	 *
	 * @return ImageIOのフォーマット名
	 */
	public String getImageIoFormatName() {
		return imageIoFormatName;
	}

	/**
	 * 出力ファイルの拡張子を取得する。
	 *
	 * @return 拡張子（ドットを含まない）
	 */
	public String getFileExtension() {
		return fileExtension;
	}

	/**
	 * 出力ファイルのMIME typeを取得する。
	 *
	 * @return MIME type
	 */
	public String getMimeType() {
		return mimeType;
	}

	/**
	 * 書き出し前に不透明な画像へ変換する必要があるか判定する。
	 *
	 * @return アルファチャンネルを持てない形式の場合true
	 */
	public boolean requiresOpaqueImage() {
		return opaqueRequired;
	}

	/**
	 * コード値が不正な場合に利用者へ返す説明を取得する。
	 *
	 * @return 画像形式コードが不正な場合の説明
	 */
	@Override
	public String getInvalidKeyMessage() {
		return INVALID_KEY_MESSAGE;
	}

	/**
	 * キー値から画像形式を取得する。
	 * <p>
	 * 大文字小文字は無視する。{@code format=png} のような小文字指定を受け付けるため。
	 *
	 * @param key 画像形式コード
	 * @return キー値に対応する画像形式
	 * @throws IllegalArgumentException 対応する画像形式が存在しない場合
	 */
	@JsonCreator
	public static PdfImageFormat fromKey(String key) {
		return KEY_MAP.values().stream().filter(format -> Strings.CI.equals(format.getKey(), key)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(INVALID_KEY_MESSAGE));
	}
}
