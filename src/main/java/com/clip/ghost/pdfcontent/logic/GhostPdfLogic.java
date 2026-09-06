package com.clip.ghost.pdfcontent.logic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.exception.PdfProcessingException;
import com.clip.ghost.pdfcontent.dto.GhostPdfDto;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;

import lombok.NoArgsConstructor;

/**
 * PDFBoxを使うPDF操作を既存public APIのまま提供するFacade。
 * <p>
 * publicメソッドの入出力は既存controller/serviceから利用されているため維持する。PDFのページ番号は画面・リクエストと同じ1始まりで受け取り、
 * PDFBoxへ渡す直前に0始まりへ変換する。文書解析、一時ファイル操作、ページ基本操作、差し込み処理は責務別の内部クラスへ委譲する。
 */
@Component
@NoArgsConstructor
public class GhostPdfLogic {
	/** PDFメタデータ取得とテキスト抽出を担当する内部ロジック。 */
	private final PdfDocumentAnalysisLogic documentAnalysisLogic = new PdfDocumentAnalysisLogic();

	/** ページ削除、抽出、結合、分割を担当する内部ロジック。 */
	private final PdfPageOperationLogic pageOperationLogic = new PdfPageOperationLogic();

	/** 差し込み、置換、末尾挿入を担当する内部ロジック。 */
	private final PdfInsertLogic insertLogic = new PdfInsertLogic();

	/** PDF一時保存先ディレクトリ。 */
	@Value("${spring.servlet.multipart.location}")
	private String tmpDirectory;

	/**
	 * アップロードされたPDFを一時保存し、その保存先パスを返却する。
	 *
	 * @param multipartFile アップロードされたPDFファイル
	 * @return 一時保存先のパス
	 * @throws PdfProcessingException PDFではない、空ファイル、または保存に失敗した場合
	 */
	public Path loadPdf(MultipartFile multipartFile) {
		return temporaryFileStorage().saveUploadedPdf(multipartFile);
	}

	/**
	 * 一時保存されたPDFをbyte配列に変換して返却する。
	 *
	 * @param originalFilePath 読み込むPDFのパス
	 * @return PDFのbyte配列
	 * @throws PdfProcessingException PDFの読み込みに失敗した場合
	 */
	public byte[] convertPdf(Path originalFilePath) {
		return convertTemporaryFile(originalFilePath);
	}

	/**
	 * 一時保存されたファイルをbyte配列に変換して返却する。
	 *
	 * @param temporaryFilePath 読み込む一時ファイルのパス
	 * @return 一時ファイルのbyte配列
	 * @throws PdfProcessingException 一時ファイルの読み込みに失敗した場合
	 */
	public byte[] convertTemporaryFile(Path temporaryFilePath) {
		PdfTemporaryFileStorage temporaryFileStorage = temporaryFileStorage();
		try {
			return temporaryFileStorage.readBytes(temporaryFilePath);
		} finally {
			temporaryFileStorage.delete(temporaryFilePath);
		}
	}

	/**
	 * 一時保存されたPDFの基本メタデータを取得する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param fileName  アップロード時の元ファイル名
	 * @param fileSize  アップロードファイルサイズ
	 * @return PDFの基本情報
	 * @throws PdfProcessingException PDFの読み込みに失敗した場合
	 */
	public PdfMetadataResponse getPdfMetadata(Path inputPath, String fileName, long fileSize) {
		try {
			return documentAnalysisLogic.getPdfMetadata(inputPath, fileName, fileSize);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
	}

	/**
	 * 一時保存されたPDFからテキストを抽出する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param fileName  アップロード時の元ファイル名
	 * @param fileSize  アップロードファイルサイズ
	 * @return PDFから抽出したテキストと基本情報
	 * @throws PdfProcessingException PDFの読み込みまたはテキスト抽出に失敗した場合
	 */
	public PdfTextResponse extractPdfText(Path inputPath, String fileName, long fileSize) {
		try {
			return documentAnalysisLogic.extractPdfText(inputPath, fileName, fileSize);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
	}

	/**
	 * 一時保存されたPDFからページ単位でテキストを抽出する。
	 * <p>
	 * 成功・失敗にかかわらず、呼び出し後に入力一時ファイルを削除する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @return PDF順のページ単位テキスト
	 * @throws PdfProcessingException PDFの読み込みまたはテキスト抽出に失敗した場合
	 */
	public List<String> extractPdfPageTexts(Path inputPath) {
		try {
			return documentAnalysisLogic.extractPdfPageTexts(inputPath);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
	}

	/**
	 * 一時保存されたPDFからページ単位の内容を抽出し、文字を取得できないページはPNGへ画像化して返す。
	 * <p>
	 * 成功・失敗にかかわらず、呼び出し後に入力一時ファイルを削除する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param renderDpi 文字が無いページを画像化する解像度（DPI）
	 * @return PDF順のページ内容（テキストと、必要なページのPNGバイト列）
	 * @throws PdfProcessingException PDFの読み込み、テキスト抽出、または画像化に失敗した場合
	 */
	public List<PdfPageContent> extractPdfPageContents(Path inputPath, int renderDpi) {
		try {
			return documentAnalysisLogic.extractPdfPageContents(inputPath, renderDpi);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
	}

	/**
	 * PDFから指定ページを削除し、削除後PDFの一時保存先パスを返却する。
	 *
	 * @param deletePages 削除するページ番号のリスト
	 * @param inputPath   読み込むPDFのパス
	 * @return 削除後PDFの一時保存先パス
	 */
	public Path deletePdf(List<Integer> deletePages, Path inputPath) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath(inputPath.getFileName().toString());
		try {
			pageOperationLogic.deletePdf(deletePages, inputPath, outputPath);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
		return outputPath;
	}

	/**
	 * 一時保存されたPDFから指定ページだけを抽出し、抽出後PDFの一時保存先パスを返却する。
	 *
	 * @param extractPages 抽出するページ番号のリスト
	 * @param inputPath    読み込むPDFのパス
	 * @return 抽出後PDFの一時保存先パス
	 */
	public Path extractPdf(List<Integer> extractPages, Path inputPath) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath(inputPath.getFileName().toString());
		try {
			pageOperationLogic.extractPdf(extractPages, inputPath, outputPath);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
		return outputPath;
	}

	/**
	 * 一時保存された複数PDFを指定順に結合し、結合後PDFの一時保存先パスを返却する。
	 *
	 * @param inputPaths 結合対象PDFの一時保存先パス
	 * @return 結合後PDFの一時保存先パス
	 */
	public Path mergePdf(List<Path> inputPaths) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath("merge.pdf");
		try {
			pageOperationLogic.mergePdf(inputPaths, outputPath);
		} finally {
			deleteTemporaryFiles(inputPaths);
		}
		return outputPath;
	}

	/**
	 * 一時保存されたPDFを1ページずつ分割し、分割後PDFを格納したZIPの一時保存先パスを返却する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @return 分割後PDFを格納したZIPの一時保存先パス
	 */
	public Path splitPdf(Path inputPath) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath("split.zip");
		try {
			pageOperationLogic.splitPdf(inputPath, outputPath);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
		return outputPath;
	}

	/**
	 * 編集元PDFに差し込みPDFを挿入または差し替えし、結合後PDFの一時保存先パスを返却する。
	 *
	 * @param originalFilePath 編集元PDFのパス
	 * @param insertPdfDtos    差し込みPDFのパス、対象ページ、挿入オプション
	 * @return 結合後PDFの一時保存先パス
	 */
	public Path insertPdf(Path originalFilePath, List<GhostPdfDto> insertPdfDtos) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath(originalFilePath.getFileName().toString());
		try {
			insertLogic.insertPdf(originalFilePath, insertPdfDtos, outputPath);
		} finally {
			temporaryFileStorage().delete(originalFilePath);
			deleteInsertTemporaryFiles(insertPdfDtos);
		}
		return outputPath;
	}

	/**
	 * 差し込みPDFとして一時保存したファイルを削除する。
	 *
	 * @param insertPdfDtos 差し込みPDFの一時保存先を持つDTOリスト
	 */
	private void deleteInsertTemporaryFiles(List<GhostPdfDto> insertPdfDtos) {
		PdfTemporaryFileStorage temporaryFileStorage = temporaryFileStorage();
		CollectionUtils.emptyIfNull(insertPdfDtos).stream().filter(Objects::nonNull)
				.forEach(insertPdfDto -> temporaryFileStorage.delete(insertPdfDto.getInsertPath()));
	}

	/**
	 * 複数のPDF一時ファイルを削除する。
	 *
	 * @param paths 削除対象の一時ファイルパス
	 */
	private void deleteTemporaryFiles(List<Path> paths) {
		temporaryFileStorage().deleteAll(paths);
	}

	/**
	 * 現在設定されている一時保存先を使用するファイル操作クラスを作成する。
	 *
	 * @return PDF一時ファイル操作クラス
	 */
	private PdfTemporaryFileStorage temporaryFileStorage() {
		return new PdfTemporaryFileStorage(tmpDirectory);
	}
}
