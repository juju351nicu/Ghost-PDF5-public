package com.clip.ghost.pdfcontent.logic;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clip.ghost.pdfcontent.dto.PdfUploadResult;
import com.clip.ghost.pdfcontent.exception.PdfPasswordProtectedException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.RequiredArgsConstructor;

/**
 * パスワードで保護されたPDFを、以降の処理が扱える形へ直す内部クラス。
 * <p>
 * 保護されたPDFを扱うのに、PDF操作の各methodへパスワードを配って回らない。アップロード直後に一度だけ
 * 保護を外した一時ファイルを作り、以降は保護の無いPDFとして同じ経路を通す。
 * ページ削除、抽出、結合、分割、差し込み、サムネイル、Markdown下書きのすべてが、パスワードを知らないまま動く。
 * <p>
 * 保護を外した一時ファイルは他のPDF一時ファイルと同じ扱いで、処理の完了時に削除される。
 */
@RequiredArgsConstructor
final class PdfDecryptionSupport {
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfDecryptionSupport.class);

	/** 一時ファイルの作成と削除を担当するクラス。 */
	private final PdfTemporaryFileStorage temporaryFileStorage;

	/**
	 * パスワードで保護されたPDFの保護を外した一時ファイルを作る。
	 * <p>
	 * 保護されていなかった場合は何もせず、入力のパスをそのまま返す。パスワードを誤って指定しても、
	 * 保護の無いPDFは従来どおり処理できる。
	 * <p>
	 * 失敗した場合は、この処理が扱っていた一時ファイルをすべて削除してから例外を送出する。
	 * 呼び出し元はパスを受け取れないため、ここで消さないと一時ファイルが残り続ける。
	 *
	 * @param inputPath 保護を外す対象のPDFパス
	 * @param password  PDFを開くためのパスワード
	 * @return 保護を外したPDFのパスと、元のPDFが保護されていたかどうか
	 * @throws PdfPasswordProtectedException 指定されたパスワードでPDFを開けない場合
	 * @throws PdfProcessingException        PDFの読み込みまたは保存に失敗した場合
	 */
	PdfUploadResult removePasswordProtection(Path inputPath, String password) {
		Path decryptedPath = temporaryFileStorage.createTemporaryFilePath(inputPath.getFileName().toString());
		boolean succeeded = false;
		try (PDDocument document = PdfDocumentLoader.load(inputPath, password)) {
			if (!document.isEncrypted()) {
				succeeded = true;
				return new PdfUploadResult(inputPath, false);
			}
			document.setAllSecurityToBeRemoved(true);
			document.save(decryptedPath.toFile());
			succeeded = true;
			LOGGER.info("パスワード保護を外したPDFを作成しました。");
			temporaryFileStorage.delete(inputPath);
			return new PdfUploadResult(decryptedPath, true);
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("パスワード保護を外す処理に失敗しました。path=" + inputPath, e);
		} finally {
			// 保護を外せた場合は保存先を返すため消さない。失敗時だけ、書きかけの保存先と入力の両方を消す。
			if (!succeeded) {
				temporaryFileStorage.delete(decryptedPath);
				temporaryFileStorage.delete(inputPath);
			}
		}
	}
}
