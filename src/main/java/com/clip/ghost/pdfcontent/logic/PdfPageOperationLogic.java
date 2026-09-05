package com.clip.ghost.pdfcontent.logic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import com.clip.ghost.common.utils.PageUtils;
import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * PDFのページ削除、抽出、結合、分割を担当する内部クラス。
 * <p>
 * 入出力パスに対するPDF加工だけを行い、一時ファイルの生成や削除はFacade側へ委ねる。
 */
@NoArgsConstructor
final class PdfPageOperationLogic {

	/**
	 * PDFから指定ページを削除する。
	 *
	 * @param deletePages 削除する1始まりのページ番号
	 * @param inputPath   読み込むPDFのパス
	 * @param outputPath  削除後PDFの出力先パス
	 * @throws PdfProcessingException PDFの読み込み、ページ削除、保存に失敗した場合
	 */
	void deletePdf(List<Integer> deletePages, Path inputPath, Path outputPath) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			List<Integer> remainingPageNumbers = selectRemainingPageNumbers(document, deletePages);
			removePagesExcept(document, remainingPageNumbers);
			document.save(outputPath.toFile());
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFのページ削除に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFから指定ページだけを抽出する。
	 *
	 * @param extractPages 抽出する1始まりのページ番号
	 * @param inputPath    読み込むPDFのパス
	 * @param outputPath   抽出後PDFの出力先パス
	 * @throws PdfProcessingException PDFの読み込み、ページ抽出、保存に失敗した場合
	 */
	void extractPdf(List<Integer> extractPages, Path inputPath, Path outputPath) {
		try (PDDocument outputDocument = new PDDocument();
				PDDocument inputDocument = Loader.loadPDF(inputPath.toFile())) {
			List<Integer> extractPageNumbers = selectExtractPageNumbers(inputDocument, extractPages);
			PdfPageCopySupport pageCopySupport = new PdfPageCopySupport(outputDocument);
			for (Integer pageNumber : extractPageNumbers) {
				pageCopySupport.appendPage(inputDocument.getPage(toPdfBoxPageIndex(pageNumber)));
			}
			outputDocument.save(outputPath.toFile());
		} catch (IllegalArgumentException | IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFのページ抽出に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * 複数PDFを指定順に結合する。
	 *
	 * @param inputPaths 結合対象PDFのパス
	 * @param outputPath 結合後PDFの出力先パス
	 * @throws PdfProcessingException PDFの読み込み、結合、保存に失敗した場合
	 */
	void mergePdf(List<Path> inputPaths, Path outputPath) {
		try (PDDocument outputDocument = new PDDocument()) {
			for (Path inputPath : selectMergeInputPaths(inputPaths)) {
				new PdfPageCopySupport(outputDocument).appendDocument(inputPath);
			}
			outputDocument.save(outputPath.toFile());
		} catch (IllegalArgumentException | IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFの結合に失敗しました。", e);
		}
	}

	/**
	 * PDFを1ページずつ分割し、単ページPDFをZIPへ格納する。
	 *
	 * @param inputPath  読み込むPDFのパス
	 * @param outputPath 分割後ZIPの出力先パス
	 * @throws PdfProcessingException PDFの読み込み、分割、ZIP保存に失敗した場合
	 */
	void splitPdf(Path inputPath, Path outputPath) {
		try (PDDocument inputDocument = Loader.loadPDF(inputPath.toFile());
				OutputStream outputStream = Files.newOutputStream(outputPath);
				ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
			int totalPages = inputDocument.getNumberOfPages();
			for (int pageNumber = PdfConstants.START_PAGE; pageNumber <= totalPages; pageNumber++) {
				addSplitPageToZip(zipOutputStream, inputDocument.getPage(toPdfBoxPageIndex(pageNumber)), pageNumber);
			}
		} catch (IllegalArgumentException | IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFの分割に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * 分割後の単ページPDFをZIPへ追加する。
	 *
	 * @param zipOutputStream 追加先ZIP
	 * @param sourcePage      分割対象ページ
	 * @param pageNumber      画面・API仕様の1始まりページ番号
	 * @throws IOException PDF生成またはZIP書き込みに失敗した場合
	 */
	private void addSplitPageToZip(ZipOutputStream zipOutputStream, PDPage sourcePage, int pageNumber)
			throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry(buildSplitPdfFileName(pageNumber)));
		zipOutputStream.write(createSinglePagePdf(sourcePage));
		zipOutputStream.closeEntry();
	}

	/**
	 * 分割対象ページから単ページPDFのbyte配列を生成する。
	 *
	 * @param sourcePage 分割対象ページ
	 * @return 単ページPDFのbyte配列
	 * @throws IOException ページ複製またはPDF保存に失敗した場合
	 */
	private byte[] createSinglePagePdf(PDPage sourcePage) throws IOException {
		try (PDDocument splitDocument = new PDDocument();
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			new PdfPageCopySupport(splitDocument).appendPage(sourcePage);
			splitDocument.save(outputStream);
			return outputStream.toByteArray();
		}
	}

	/**
	 * 分割後PDFのZIP内ファイル名を作成する。
	 *
	 * @param pageNumber 画面・API仕様の1始まりページ番号
	 * @return 3桁のページ番号を含むZIP内ファイル名
	 */
	private String buildSplitPdfFileName(int pageNumber) {
		return String.format("split-%03d.pdf", pageNumber);
	}

	/**
	 * PDF結合対象のパスを取得する。
	 * <p>
	 * service/controller側のvalidationを通らない直接呼び出しでも、空入力ではPDFBox処理へ進まないようにする。
	 *
	 * @param inputPaths 結合対象PDFのパス
	 * @return nullを除外した結合対象PDFのパス
	 * @throws IllegalArgumentException 結合対象が1件もない場合
	 */
	private List<Path> selectMergeInputPaths(List<Path> inputPaths) {
		List<Path> mergeInputPaths = CollectionUtils.emptyIfNull(inputPaths).stream().filter(Objects::nonNull).toList();
		if (mergeInputPaths.isEmpty()) {
			throw new IllegalArgumentException("結合対象PDFが指定されていません。");
		}
		return mergeInputPaths;
	}

	/**
	 * 抽出対象ページ番号をページ番号順・重複なしで取得する。
	 * <p>
	 * 画面・API仕様の1始まりページ番号を維持し、PDFに存在しないページ番号は不正入力として扱う。
	 *
	 * @param document     読み込んだPDFドキュメント
	 * @param extractPages 抽出するページ番号
	 * @return 抽出対象の1始まりページ番号
	 * @throws IllegalArgumentException 抽出対象が空、またはページ範囲外の場合
	 */
	private List<Integer> selectExtractPageNumbers(PDDocument document, List<Integer> extractPages) {
		int totalPages = document.getNumberOfPages();
		List<Integer> extractPageNumbers = CollectionUtils.emptyIfNull(extractPages).stream().distinct().sorted()
				.collect(Collectors.toList());
		if (extractPageNumbers.isEmpty()
				|| extractPageNumbers.stream().anyMatch(pageNumber -> isOutsidePageRange(pageNumber, totalPages))) {
			throw new IllegalArgumentException("抽出ページ番号がPDFのページ範囲外です。");
		}
		return extractPageNumbers;
	}

	/**
	 * 画面・API仕様の1始まりページ番号がPDFの範囲外か判定する。
	 *
	 * @param pageNumber 画面・API仕様の1始まりページ番号
	 * @param totalPages PDFの総ページ数
	 * @return ページ番号がnullまたは範囲外の場合はtrue
	 */
	private boolean isOutsidePageRange(Integer pageNumber, int totalPages) {
		return pageNumber == null || pageNumber < PdfConstants.START_PAGE || pageNumber > totalPages;
	}

	/**
	 * 画面・API仕様の1始まりページ番号をPDFBoxの0始まりpage indexへ変換する。
	 *
	 * @param pageNumber 画面・API仕様の1始まりページ番号
	 * @return PDFBoxの0始まりpage index
	 */
	private int toPdfBoxPageIndex(int pageNumber) {
		return pageNumber - PdfConstants.START_PAGE;
	}

	/**
	 * 削除対象ページを除いた、出力PDFへ残すページ番号を計算する。
	 * <p>
	 * {@link PageUtils} は画面・リクエストと同じ1始まりのページ番号を返す。
	 *
	 * @param document    読み込んだPDFドキュメント
	 * @param deletePages 削除するページ番号
	 * @return 出力PDFへ残す1始まりページ番号
	 */
	private List<Integer> selectRemainingPageNumbers(PDDocument document, List<Integer> deletePages) {
		return PageUtils.selectPageNumbers(PdfConstants.START_PAGE, document.getNumberOfPages(), deletePages);
	}

	/**
	 * 残すページ番号に含まれないページをPDFBoxドキュメントから削除する。
	 *
	 * @param document             編集対象のPDFドキュメント
	 * @param remainingPageNumbers 出力PDFへ残す1始まりページ番号
	 */
	private void removePagesExcept(PDDocument document, List<Integer> remainingPageNumbers) {
		// 後方から削除し、未処理ページの0始まりindexがずれないようにする。
		for (int pageIndex = document.getNumberOfPages() - 1; pageIndex >= 0; pageIndex--) {
			int requestPageNumber = toRequestPageNumber(pageIndex);
			if (!remainingPageNumbers.contains(requestPageNumber)) {
				document.removePage(pageIndex);
			}
		}
	}

	/**
	 * PDFBoxの0始まりpage indexを画面・リクエスト仕様の1始まりページ番号へ変換する。
	 *
	 * @param pdfBoxPageIndex PDFBoxの0始まりpage index
	 * @return 画面・リクエスト仕様の1始まりページ番号
	 */
	private int toRequestPageNumber(int pdfBoxPageIndex) {
		return pdfBoxPageIndex + PdfConstants.START_PAGE;
	}
}
