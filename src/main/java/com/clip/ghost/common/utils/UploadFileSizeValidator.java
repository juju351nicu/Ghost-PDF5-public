package com.clip.ghost.common.utils;

import java.util.Collection;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

/**
 * アップロードファイルのサイズ上限を検証するユーティリティクラス。
 * <p>
 * 上限超過は {@link MultipartException} として投げ、共通例外ハンドラーが413へ変換する。
 * 判定はControllerがServiceを呼ぶ前に行い、上限超過のファイルが一時保存や外部送信まで進まないようにする。
 * <p>
 * 各Controllerが同じ判定を書き写していたため、ここへ集約した。書き写しが増えると、
 * 「上限以上」と「上限超過」のような境界のずれが取り込み口ごとに入り込む。
 */
public final class UploadFileSizeValidator {
	private static final String SIZE_EXCEEDED_MESSAGE = "サイズの超過";

	private UploadFileSizeValidator() {
	}

	/**
	 * アップロードファイルが上限未満であることを検証する。
	 * <p>
	 * 上限ちょうどのサイズも拒否する。既存APIの境界をそのまま保つため。
	 *
	 * @param file             アップロードファイル
	 * @param maxFileSizeBytes 許容する上限（byte）。この値以上を拒否する
	 * @throws MultipartException 許容サイズ以上の場合
	 */
	public static void validate(MultipartFile file, long maxFileSizeBytes) {
		if (file != null && file.getSize() >= maxFileSizeBytes) {
			throw new MultipartException(SIZE_EXCEEDED_MESSAGE);
		}
	}

	/**
	 * 複数のアップロードファイルがいずれも上限未満であることを検証する。
	 *
	 * @param files            アップロードファイル。未指定の場合は検証しない
	 * @param maxFileSizeBytes 許容する上限（byte）。この値以上を拒否する
	 * @throws MultipartException いずれかが許容サイズ以上の場合
	 */
	public static void validateAll(Collection<MultipartFile> files, long maxFileSizeBytes) {
		CollectionUtils.emptyIfNull(files).forEach(file -> validate(file, maxFileSizeBytes));
	}
}
