package com.clip.ghost.pdfcontent.constant;

/**
 * PDF操作で使用する定数を集約するクラス。
 * <p>
 * ファイル入出力の保存先は環境差が出やすいため、このクラスには固定の絶対パスを置かない。
 */
public final class PdfConstants {
	/** サンプル画像・PDFを配置しているclasspath上のディレクトリ。 */
	public static final String TEST_FILE_DIRECTORY = "src/main/resources/static/img";

	/** PDF拡張子。 */
	public static final String FILE_PDF = "pdf";

	/** PNG data URL prefix。 */
	public static final String BASE64_PNG = "data:image/png;base64,";

	/** PDF data URL prefix。 */
	public static final String BASE64_PDF = "data:application/pdf;base64,";

	/** 画面・内部処理で扱う最初のページ番号。 */
	public static final int START_PAGE = 1;

	/**
	 * アップロードを許容するPDF 1ファイルの上限（byte）。この値以上は413で拒否する。
	 * <p>
	 * 利用者から見える上限をこの1箇所に集約する。{@code spring.servlet.multipart.max-file-size} は
	 * この値より大きく設定し、コンテナが接続を切る前にアプリ側が413を返せるようにする。
	 * フロントエンドの {@code const.js} の {@code FILE_SIZE.MAX_PDF_BYTES} は同じ値を保つ
	 * （`FrontendUploadSizeContractTest` が一致を検証する）。
	 */
	public static final long MAX_PDF_FILE_SIZE_BYTES = 20_971_520L;

	/** 差し込みオプション: 対象ページの後に差し込む。 */
	public static final int OPTION_INSERT = 1;

	/** 差し込みオプション: 対象ページと差し替える。 */
	public static final int OPTION_REPLACE = 2;

	/** 差し込みオプション: 最後のページへ差し込む。 */
	public static final int OPTION_LAST_INSERT = 3;
}
