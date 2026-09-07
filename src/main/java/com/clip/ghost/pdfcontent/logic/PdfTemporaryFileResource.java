package com.clip.ghost.pdfcontent.logic;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;

/**
 * レスポンス送信後に一時ファイルを削除する {@link org.springframework.core.io.Resource}。
 * <p>
 * ファイル内容をbyte配列へ読み込まずにストリームで返すため、出力サイズに比例したヒープ消費が起きない。
 * <p>
 * 削除タイミングが要点になる。ストリームで返す場合、Serviceのメソッドを抜けた時点ではまだ送信中のため、
 * その場で削除するとレスポンスが壊れる。Springはレスポンス本文を書き終えた後に入力ストリームを閉じるので、
 * {@code close()} に削除を寄せることで送信完了後に削除できる。
 * <p>
 * {@link FileSystemResource} を継承しているため {@code contentLength()} はファイルサイズを返し、
 * ストリームを消費しない。{@code InputStreamResource} ではContent-Length取得のために内容を読み切ってしまう。
 * <p>
 * エラー経路で {@code close()} が呼ばれず一時ファイルが残る可能性はゼロにできないが、対象は
 * {@code spring.servlet.multipart.location} 配下の一時ファイルであり、稀な残留は許容する
 * （利用者の成果物とは扱いが異なる。{@code docs/coding-guidelines.md} の「保存先ディレクトリのルール」参照）。
 */
final class PdfTemporaryFileResource extends FileSystemResource {
	/** 一時ファイル削除のログ出力。 */
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfTemporaryFileResource.class);

	/** レスポンス送信後に削除する一時ファイルのパス。 */
	private final Path temporaryFilePath;

	/**
	 * 一時ファイルパスを指定してレスポンス用リソースを生成する。
	 *
	 * @param temporaryFilePath レスポンスへ流す一時ファイルのパス
	 */
	PdfTemporaryFileResource(Path temporaryFilePath) {
		super(temporaryFilePath);
		this.temporaryFilePath = temporaryFilePath;
	}

	/**
	 * 一時ファイルの読み込みストリームを返す。
	 * <p>
	 * 返すストリームは {@code close()} 時に一時ファイルを削除する。
	 *
	 * @return close時に一時ファイルを削除する読み込みストリーム
	 * @throws IOException 一時ファイルを開けない場合
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return new FilterInputStream(super.getInputStream()) {
			/**
			 * ストリームを閉じ、続けて一時ファイルを削除する。
			 *
			 * @throws IOException ストリームのcloseに失敗した場合
			 */
			@Override
			public void close() throws IOException {
				try {
					super.close();
				} finally {
					deleteTemporaryFile();
				}
			}
		};
	}

	/**
	 * 一時ファイルを削除する。
	 * <p>
	 * 後処理の失敗でレスポンス送信の結果を上書きしないよう、削除失敗は警告ログに記録して例外を送出しない。
	 */
	private void deleteTemporaryFile() {
		LOGGER.debug("レスポンス送信後にPDF一時ファイルを削除します。path={}", temporaryFilePath);
		try {
			Files.deleteIfExists(temporaryFilePath);
		} catch (IOException e) {
			LOGGER.warn("レスポンス送信後のPDF一時ファイル削除に失敗しました。path={}", temporaryFilePath, e);
		}
	}
}
