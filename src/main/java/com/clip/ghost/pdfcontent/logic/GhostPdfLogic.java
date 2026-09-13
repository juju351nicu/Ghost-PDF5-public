package com.clip.ghost.pdfcontent.logic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.enums.PdfMarkdownDraftMode;
import com.clip.ghost.pdfcontent.enums.PdfImageFormat;
import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.clip.ghost.pdfcontent.enums.PdfRotation;
import com.clip.ghost.pdfcontent.exception.PdfImageInputException;
import com.clip.ghost.pdfcontent.exception.PdfPageLimitExceededException;
import com.clip.ghost.pdfcontent.exception.PdfPasswordProtectedException;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;
import com.clip.ghost.pdfcontent.dto.GhostPdfDto;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfImageSource;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
import com.clip.ghost.pdfcontent.dto.PdfPageImage;
import com.clip.ghost.pdfcontent.dto.PdfPageThumbnail;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.dto.PdfUploadResult;

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
	/** アップロード時のファイル名が取得できなかった場合に、エラーメッセージへ出す代替名。 */
	private static final String UNKNOWN_IMAGE_FILE_NAME = "（ファイル名不明）";

	/** PDFメタデータ取得とテキスト抽出を担当する内部ロジック。 */
	private final PdfDocumentAnalysisLogic documentAnalysisLogic = new PdfDocumentAnalysisLogic();

	/** ページ削除、抽出、結合、分割を担当する内部ロジック。 */
	private final PdfPageOperationLogic pageOperationLogic = new PdfPageOperationLogic();

	/** ページの画像化を担当する内部ロジック。 */
	private final PdfImageExportLogic imageExportLogic = new PdfImageExportLogic();

	/** 画像からのPDF生成を担当する内部ロジック。 */
	private final ImagesToPdfLogic imagesToPdfLogic = new ImagesToPdfLogic();

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
		return loadPdf(multipartFile, null);
	}

	/**
	 * アップロードされたPDFを一時保存し、その保存先パスを返却する。
	 * <p>
	 * パスワードが指定された場合は、保護を外した一時ファイルを作ってそのパスを返す。以降のPDF操作は
	 * 保護の無いPDFとして同じ経路を通るため、ページ削除や分割などの各methodはパスワードを受け取らない。
	 * <p>
	 * パスワードを指定しても保護されていなかった場合は、そのまま保存したファイルを使う。
	 *
	 * @param multipartFile アップロードされたPDFファイル
	 * @param password      PDFを開くためのパスワード。未指定の場合はnullまたは空文字
	 * @return 一時保存先のパス
	 * @throws PdfPasswordProtectedException 指定されたパスワードでPDFを開けない場合
	 * @throws PdfProcessingException        PDFではない、空ファイル、または保存に失敗した場合
	 */
	public Path loadPdf(MultipartFile multipartFile, String password) {
		return loadPdfUpload(multipartFile, password).path();
	}

	/**
	 * アップロードされたPDFを一時保存し、保存結果を返却する。
	 * <p>
	 * 保護を外したかどうかまで必要な場合に使う。保存先のPDFを読んでも、利用者がアップロードしたファイルが
	 * 保護されていたかどうかは分からなくなるため、保存時点の事実を持ち回る。
	 *
	 * @param multipartFile アップロードされたPDFファイル
	 * @param password      PDFを開くためのパスワード。未指定の場合はnullまたは空文字
	 * @return 一時保存先のパスと、元のPDFが保護されていたかどうか
	 * @throws PdfPasswordProtectedException 指定されたパスワードでPDFを開けない場合
	 * @throws PdfProcessingException        PDFではない、空ファイル、または保存に失敗した場合
	 */
	public PdfUploadResult loadPdfUpload(MultipartFile multipartFile, String password) {
		Path inputPath = temporaryFileStorage().saveUploadedPdf(multipartFile);
		if (StringUtils.isEmpty(password)) {
			return new PdfUploadResult(inputPath, false);
		}
		return new PdfDecryptionSupport(temporaryFileStorage()).removePasswordProtection(inputPath, password);
	}

	/**
	 * 一時保存されたファイルを、レスポンス送信後に削除されるリソースとして開く。
	 * <p>
	 * byte配列へ読み込まずにストリームで返すため、出力サイズに比例したヒープ消費が起きない。
	 * 一時ファイルの削除は、他のpublicメソッドと違いこのメソッドから戻った時点では行われない。
	 * レスポンス本文の送信が終わり、リソースの読み込みストリームがcloseされた時点で削除される。
	 *
	 * @param temporaryFilePath レスポンスへ流す一時ファイルのパス
	 * @return レスポンス送信後に一時ファイルを削除するリソース
	 * @throws PdfProcessingException 一時ファイルが存在しない場合
	 */
	public Resource openTemporaryFileForResponse(Path temporaryFilePath) {
		return temporaryFileStorage().openForResponse(temporaryFilePath);
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
	 * 一時保存されたPDFからページ単位の内容を抽出し、変換対象のページは画像化して変換器へ渡す。
	 * <p>
	 * 変換対象はモードが決める。{@code AUTO} は文字を取得できないページだけ、{@code VISION} は全ページを対象にする。
	 * <p>
	 * 成功・失敗にかかわらず、呼び出し後に入力一時ファイルを削除する。ページ上限を超えて拒否した場合も削除する。
	 * テキスト抽出と画像化は1つの {@code PDDocument} 内で2段に分けて行うため、上限超過時は変換器が1度も呼ばれない。
	 *
	 * @param inputPath          読み込むPDFのパス
	 * @param renderDpi          変換対象ページを画像化する解像度（DPI）
	 * @param maxPages           画像変換にかけるページ数の上限
	 * @param mode               変換対象ページを決める変換モード
	 * @param pageImageConverter 画像化した1ページ分をテキストへ変換する処理
	 * @return PDF順のページ内容（テキストと、変換したページの変換結果）
	 * @throws PdfPageLimitExceededException 変換対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込み、テキスト抽出、または画像化に失敗した場合
	 */
	public List<PdfPageContent> extractPdfPageContents(Path inputPath, int renderDpi, int maxPages,
			PdfMarkdownDraftMode mode, PdfPageImageConverter pageImageConverter) {
		try {
			return documentAnalysisLogic.extractPdfPageContents(inputPath, renderDpi, maxPages, mode,
					pageImageConverter);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
	}

	/**
	 * 一時保存されたPDFの全ページから、ページ選択UI用のサムネイルを生成する。
	 * <p>
	 * 成功・失敗にかかわらず、呼び出し後に入力一時ファイルを削除する。ページ上限を超えて拒否した場合も削除する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param renderDpi 画像化する解像度（DPI）
	 * @param maxPages  サムネイルを返すページ数の上限
	 * @return PDF順のページ単位サムネイル
	 * @throws PdfPageLimitExceededException 総ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込みまたは画像化に失敗した場合
	 */
	public List<PdfPageThumbnail> extractPdfThumbnails(Path inputPath, int renderDpi, int maxPages) {
		try {
			return documentAnalysisLogic.extractPdfThumbnails(inputPath, renderDpi, maxPages);
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
	 * 一時保存されたPDFの指定ページを回転し、回転後PDFの一時保存先パスを返却する。
	 *
	 * @param rotation    加える回転角
	 * @param rotatePages 回転するページ番号のリスト。未指定の場合は全ページを回転する
	 * @param inputPath   読み込むPDFのパス
	 * @return 回転後PDFの一時保存先パス
	 */
	public Path rotatePdf(PdfRotation rotation, List<Integer> rotatePages, Path inputPath) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath(inputPath.getFileName().toString());
		try {
			pageOperationLogic.rotatePdf(rotation, rotatePages, inputPath, outputPath);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
		return outputPath;
	}

	/**
	 * 一時保存されたPDFのページを画像化し、画像を格納したZIPの一時保存先パスを返却する。
	 * <p>
	 * 成功・失敗にかかわらず入力一時ファイルを削除する。ページ上限を超えて拒否した場合も削除する。
	 *
	 * @param inputPath  読み込むPDFのパス
	 * @param format     出力する画像形式
	 * @param renderDpi  画像化する解像度（DPI）
	 * @param maxPages   画像化するページ数の上限
	 * @param imagePages 画像化するページ番号のリスト。未指定の場合は全ページを画像化する
	 * @return 画像を格納したZIPの一時保存先パス
	 * @throws PdfPageLimitExceededException 対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込みまたは画像化に失敗した場合
	 */
	public Path exportPdfImages(Path inputPath, PdfImageFormat format, int renderDpi, int maxPages,
			List<Integer> imagePages) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath("images.zip");
		try {
			imageExportLogic.exportPdfImages(inputPath, outputPath, format, renderDpi, maxPages, imagePages);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
		return outputPath;
	}

	/**
	 * 一時保存されたPDFのページを画像化して読み出す。
	 * <p>
	 * 結果をZIPではなくメモリ上のリストで返す。PowerPointのスライドのように、全ページ分を1つのファイルへ
	 * 組み立てる用途で使う。成功・失敗にかかわらず入力一時ファイルを削除する。
	 *
	 * @param inputPath  読み込むPDFのパス
	 * @param format     画像のエンコード形式
	 * @param renderDpi  画像化する解像度（DPI）
	 * @param maxPages   画像化するページ数の上限
	 * @param imagePages 画像化するページ番号のリスト。未指定の場合は全ページを画像化する
	 * @return PDF順のページ画像
	 * @throws PdfPageLimitExceededException 対象ページ数が上限を超えた場合
	 * @throws PdfProcessingException        PDFの読み込みまたは画像化に失敗した場合
	 */
	public List<PdfPageImage> readPdfPageImages(Path inputPath, PdfImageFormat format, int renderDpi, int maxPages,
			List<Integer> imagePages) {
		try {
			return imageExportLogic.readPdfPageImages(inputPath, format, renderDpi, maxPages, imagePages);
		} finally {
			temporaryFileStorage().delete(inputPath);
		}
	}

	/**
	 * アップロードされた複数の画像を一時保存し、1つのPDFへまとめた一時保存先パスを返却する。
	 * <p>
	 * 画像は渡された順にページへ並べる。成功・失敗にかかわらず画像の一時ファイルを削除する。
	 *
	 * @param imageFiles アップロードされた画像ファイル（並べる順）
	 * @param pageSize   ページサイズの決め方
	 * @return 生成したPDFの一時保存先パス
	 * @throws PdfImageInputException 画像として読めないファイルが含まれる場合
	 * @throws PdfProcessingException PDFの組み立てまたは保存に失敗した場合
	 */
	public Path createPdfFromImages(List<MultipartFile> imageFiles, PdfImagePageSize pageSize) {
		List<PdfImageSource> imageSources = CollectionUtils.emptyIfNull(imageFiles).stream()
				.map(imageFile -> new PdfImageSource(temporaryFileStorage().saveUploadedImage(imageFile),
						StringUtils.defaultIfBlank(imageFile.getOriginalFilename(), UNKNOWN_IMAGE_FILE_NAME)))
				.toList();
		Path outputPath = temporaryFileStorage().createTemporaryFilePath("images.pdf");
		try {
			imagesToPdfLogic.createPdfFromImages(imageSources, outputPath, pageSize);
		} finally {
			deleteTemporaryFiles(imageSources.stream().map(PdfImageSource::path).toList());
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
	 * 一時保存されたPDFを分割し、分割後PDFを格納したZIPの一時保存先パスを返却する。
	 * <p>
	 * {@code splitRanges} が未指定なら従来どおり1ページずつ分割する。成功・失敗にかかわらず入力一時ファイルを削除する。
	 *
	 * @param inputPath   読み込むPDFのパス
	 * @param splitRanges 範囲ごとに分割する場合の {@code 1-5} 形式のページ範囲。未指定時は1ページずつ分割する
	 * @return 分割後PDFを格納したZIPの一時保存先パス
	 */
	public Path splitPdf(Path inputPath, List<String> splitRanges) {
		Path outputPath = temporaryFileStorage().createTemporaryFilePath("split.zip");
		try {
			pageOperationLogic.splitPdf(inputPath, outputPath, splitRanges);
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
