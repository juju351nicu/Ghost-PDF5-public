package com.clip.ghost.pdfcontent.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.utils.PathUtils;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.RequiredArgsConstructor;

/**
 * PDF処理で使用する一時ファイルの保存、読込、パス生成、削除を扱う内部クラス。
 */
@RequiredArgsConstructor
final class PdfTemporaryFileStorage {
	/** 一時ファイル操作のログ出力。 */
	private static final Logger LOGGER = LoggerFactory.getLogger(PdfTemporaryFileStorage.class);

	/** PDF一時保存先ディレクトリ。 */
	private final String temporaryDirectory;

	/**
	 * アップロードされたPDFを検証して一時保存する。
	 *
	 * @param multipartFile アップロードされたPDFファイル
	 * @return 一時保存先のパス
	 * @throws PdfProcessingException PDFではない、空ファイル、または保存に失敗した場合
	 */
	Path saveUploadedPdf(MultipartFile multipartFile) {
		if (isInvalidPdfUpload(multipartFile)) {
			LOGGER.warn("PDFではない、または空のアップロードファイルです。fileName={}", getOriginalFileName(multipartFile));
			throw new PdfProcessingException("PDFファイルを指定してください。");
		}
		Path outputPath = createTemporaryFilePath(multipartFile.getOriginalFilename());
		try {
			multipartFile.transferTo(outputPath);
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("アップロードされたPDFの一時保存に失敗しました。", e);
		}
		return outputPath;
	}

	/**
	 * 一時ファイルを、レスポンス送信後に削除されるリソースとして開く。
	 * <p>
	 * 内容をbyte配列へ読み込まないため、出力サイズに比例したヒープ消費が起きない。
	 * 削除はリソースの読み込みストリームがcloseされた時点で行われる。
	 *
	 * @param filePath レスポンスへ流す一時ファイルのパス
	 * @return レスポンス送信後に一時ファイルを削除するリソース
	 * @throws PdfProcessingException 一時ファイルが存在しない場合
	 */
	Resource openForResponse(Path filePath) {
		if (filePath == null || !Files.isRegularFile(filePath)) {
			throw new PdfProcessingException("レスポンスへ流すPDF一時ファイルが見つかりません。path=" + filePath);
		}
		return new PdfTemporaryFileResource(filePath);
	}

	/**
	 * 元ファイル名の拡張子を保った一意な一時ファイルパスを作成する。
	 * <p>
	 * 保存先ディレクトリが存在しない場合は、このメソッド内で作成する。
	 *
	 * @param originalFileName アップロード時の元ファイル名
	 * @return 一意な一時ファイルパス
	 * @throws PdfProcessingException 一時保存先ディレクトリを作成できない場合
	 */
	Path createTemporaryFilePath(String originalFileName) {
		Path folderPath = Paths.get(temporaryDirectory);
		createTemporaryDirectory(folderPath);
		Path outputPath = folderPath.resolve(UUID.randomUUID() + "." + PathUtils.getExtension(originalFileName));
		LOGGER.debug("PDF一時ファイルパスを作成しました。path={}", outputPath);
		return outputPath;
	}

	/**
	 * 複数の一時ファイルを削除する。
	 *
	 * @param paths 削除対象パス。nullの場合は何もしない
	 */
	void deleteAll(List<Path> paths) {
		if (paths != null) {
			paths.forEach(this::delete);
		}
	}

	/**
	 * 一時ファイルが存在する場合に削除する。
	 * <p>
	 * 後処理の失敗で本来のPDF処理結果を上書きしないよう、削除失敗は警告ログに記録して例外を送出しない。
	 *
	 * @param path 削除対象パス。nullの場合は何もしない
	 */
	void delete(Path path) {
		if (path == null) {
			return;
		}
		LOGGER.debug("PDF一時ファイルを削除します。path={}", path);
		// 一時ファイル削除の失敗は主処理の成功・失敗を上書きしない。
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.warn("PDF一時ファイルの削除に失敗しました。path={}", path, e);
		}
	}

	/**
	 * アップロード内容がPDFとして受け付けられない状態か判定する。
	 *
	 * @param multipartFile アップロードファイル
	 * @return null、空ファイル、またはPDF拡張子ではない場合はtrue
	 */
	private boolean isInvalidPdfUpload(MultipartFile multipartFile) {
		return multipartFile == null || multipartFile.isEmpty()
				|| !PathUtils.isPdfFileName(multipartFile.getOriginalFilename());
	}

	/**
	 * nullを許容してアップロード時の元ファイル名を取得する。
	 *
	 * @param multipartFile アップロードファイル
	 * @return 元ファイル名。ファイルがnullの場合はnull
	 */
	private String getOriginalFileName(MultipartFile multipartFile) {
		return multipartFile == null ? null : multipartFile.getOriginalFilename();
	}

	/**
	 * PDF一時保存先ディレクトリを必要に応じて作成する。
	 *
	 * @param folderPath 一時保存先ディレクトリ
	 * @throws PdfProcessingException ディレクトリ作成に失敗した場合
	 */
	private void createTemporaryDirectory(Path folderPath) {
		try {
			Files.createDirectories(folderPath);
		} catch (IOException e) {
			throw new PdfProcessingException("PDF一時保存先ディレクトリの作成に失敗しました。path=" + folderPath, e);
		}
	}
}
