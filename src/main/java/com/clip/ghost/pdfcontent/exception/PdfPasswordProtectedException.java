package com.clip.ghost.pdfcontent.exception;

/**
 * パスワードで保護されたPDFを開けなかった場合に送出する例外。
 * <p>
 * 利用者が自分で対処できるエラーのため400として返し、{@code PdfProcessingException}（500）とは分ける。
 * この区別が無いと「PDF処理に失敗しました」とだけ表示され、保護されたPDFを指定したことが利用者に伝わらない。
 * <p>
 * 対象はPDFを開くために必要なユーザーパスワードだけで、印刷や編集だけを制限する所有者パスワードは対象外。
 * 所有者パスワードのみのPDFは空パスワードで開けるため、従来どおり処理を継続する。
 */
public class PdfPasswordProtectedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** パスワードを指定したうえで開けなかった場合true。 */
	private final boolean passwordProvided;

	/**
	 * エラーメッセージと原因例外を指定して例外を生成する。
	 * <p>
	 * 利用者が入力したパスワードをメッセージへ含めない。この例外はログへ出力されるため、
	 * 含めると認証情報がログに残る。
	 *
	 * @param message          エラーメッセージ
	 * @param cause            原因例外
	 * @param passwordProvided パスワードを指定したうえで開けなかった場合true
	 */
	public PdfPasswordProtectedException(String message, Throwable cause, boolean passwordProvided) {
		super(message, cause);
		this.passwordProvided = passwordProvided;
	}

	/**
	 * パスワードを指定したうえで開けなかったか判定する。
	 * <p>
	 * 「パスワードが必要」と「指定されたパスワードが違う」を利用者向けに書き分けるために使う。
	 * PDFBoxはどちらも同じ例外・同じメッセージで通知するため、指定の有無を呼び出し側で保持する。
	 *
	 * @return パスワードを指定したうえで開けなかった場合true
	 */
	public boolean isPasswordProvided() {
		return passwordProvided;
	}
}
