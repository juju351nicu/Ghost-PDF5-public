package com.clip.ghost.common.constant;

/**
 * アップロード受け入れの共通上限を集約するクラス。
 * <p>
 * PDF・Office文書・HTMLのいずれも同じ上限で受け入れる。形式ごとに上限を分けると、
 * 413を受け取った利用者がどの上限に当たったのか判断できなくなる。
 * <p>
 * 以前はPDF専用の {@code PdfConstants} が持っていたが、PDF以外の取り込み口
 * （Office文書・Webページ取り込み）からも同じ値を参照しており、置き場所と用途が食い違っていた。
 */
public final class UploadConstants {
	/**
	 * アップロードを許容する1ファイルの上限（byte）。この値以上は413で拒否する。
	 * <p>
	 * 利用者から見える上限をこの1箇所に集約する。{@code spring.servlet.multipart.max-file-size} は
	 * この値より大きく設定し、コンテナが接続を切る前にアプリ側が413を返せるようにする。
	 * フロントエンドの {@code const.js} の {@code FILE_SIZE.MAX_PDF_BYTES} は同じ値を保つ
	 * （`FrontendUploadSizeContractTest` が一致を検証する）。
	 */
	public static final long MAX_UPLOAD_FILE_SIZE_BYTES = 20_971_520L;

	private UploadConstants() {
	}
}
