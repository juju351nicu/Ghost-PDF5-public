package com.clip.ghost.pdfcontent.logic;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import com.clip.ghost.pdfcontent.exception.PdfPasswordProtectedException;

/**
 * 一時保存されたPDFの読み込みを1箇所へ集約する内部クラス。
 * <p>
 * PDFBoxはユーザーパスワード付きのPDFに対して {@link InvalidPasswordException} を送出するが、これは
 * {@link IOException} のサブクラスのため、各ロジックの {@code catch (IllegalStateException | IOException e)} に
 * そのまま吸われて「PDF処理に失敗しました」という汎用メッセージ（HTTP 500）になっていた。
 * 読み込み口をここへ集約し、保護されたPDFだけを専用の例外へ振り替える。
 * <p>
 * {@link PdfPasswordProtectedException} は非検査例外のため、呼び出し元の {@code catch} には捕まらず
 * そのまま例外ハンドラーへ届く。
 */
final class PdfDocumentLoader {
	/** 例外メッセージ。パスワードそのものは含めないため、ログへ出しても認証情報は残らない。 */
	private static final String PASSWORD_REQUIRED_MESSAGE = "パスワードで保護されたPDFのため開けません。path=";

	/** パスワード指定時の例外メッセージ。指定された値そのものは含めない。 */
	private static final String PASSWORD_INCORRECT_MESSAGE = "指定されたパスワードではPDFを開けません。path=";

	/**
	 * ユーティリティクラスのためインスタンス化させない。
	 */
	private PdfDocumentLoader() {
	}

	/**
	 * 一時保存されたPDFをパスワードなしで読み込む。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @return 読み込んだPDFドキュメント
	 * @throws PdfPasswordProtectedException ユーザーパスワードで保護されている場合
	 * @throws IOException                   PDFとして読み込めない場合
	 */
	static PDDocument load(Path inputPath) throws IOException {
		return load(inputPath, null);
	}

	/**
	 * 一時保存されたPDFをパスワード付きで読み込む。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param password  PDFを開くためのパスワード。未指定の場合はnullまたは空文字
	 * @return 読み込んだPDFドキュメント
	 * @throws PdfPasswordProtectedException パスワードが必要、または指定されたパスワードで開けない場合
	 * @throws IOException                   PDFとして読み込めない場合
	 */
	static PDDocument load(Path inputPath, String password) throws IOException {
		boolean passwordProvided = StringUtils.isNotEmpty(password);
		try {
			return Loader.loadPDF(inputPath.toFile(), StringUtils.defaultString(password));
		} catch (InvalidPasswordException e) {
			String message = passwordProvided ? PASSWORD_INCORRECT_MESSAGE : PASSWORD_REQUIRED_MESSAGE;
			throw new PdfPasswordProtectedException(message + inputPath, e, passwordProvided);
		}
	}
}
