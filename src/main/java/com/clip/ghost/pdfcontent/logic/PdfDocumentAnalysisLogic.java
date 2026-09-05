package com.clip.ghost.pdfcontent.logic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * PDFドキュメントのメタデータ取得とテキスト抽出を扱う内部ロジック。
 * <p>
 * 入力ファイルのライフサイクルは呼び出し元が管理し、このクラスはPDFBoxドキュメントのcloseだけを担当する。
 */
@NoArgsConstructor
final class PdfDocumentAnalysisLogic {

	/**
	 * PDFのファイル名、サイズ、ページ数、暗号化状態を取得する。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param fileName  アップロード時の元ファイル名
	 * @param fileSize  アップロードファイルサイズ
	 * @return PDFの基本情報
	 * @throws PdfProcessingException PDFの読み込みまたはメタデータ取得に失敗した場合
	 */
	PdfMetadataResponse getPdfMetadata(Path inputPath, String fileName, long fileSize) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			PdfMetadataResponse response = new PdfMetadataResponse();
			response.setFileName(fileName);
			response.setFileSize(fileSize);
			response.setPageCount(document.getNumberOfPages());
			response.setEncrypted(document.isEncrypted());
			return response;
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFメタデータの取得に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFの全ページからテキストを抽出し、基本情報とともに返す。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param fileName  アップロード時の元ファイル名
	 * @param fileSize  アップロードファイルサイズ
	 * @return PDFから抽出したテキストと基本情報
	 * @throws PdfProcessingException PDFの読み込みまたはテキスト抽出に失敗した場合
	 */
	PdfTextResponse extractPdfText(Path inputPath, String fileName, long fileSize) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			PDFTextStripper textStripper = new PDFTextStripper();
			PdfTextResponse response = new PdfTextResponse();
			response.setFileName(fileName);
			response.setFileSize(fileSize);
			response.setPageCount(document.getNumberOfPages());
			response.setText(textStripper.getText(document));
			return response;
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFテキスト抽出に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * PDFからページ単位でテキストを抽出する。
	 * <p>
	 * PDFBoxのページ指定は1始まりのため、開始・終了ページを同じ値に設定してPDF順に抽出する。
	 * 空ページも省略せず、PDFの総ページ数と同じ要素数を返す。入力ファイルは削除しない。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @return PDF順のページ単位テキスト
	 * @throws PdfProcessingException PDFの読み込みまたはテキスト抽出に失敗した場合
	 */
	List<String> extractPdfPageTexts(Path inputPath) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			PDFTextStripper textStripper = new PDFTextStripper();
			int totalPages = document.getNumberOfPages();
			List<String> pageTexts = new ArrayList<>(totalPages);
			for (int pageNumber = PdfConstants.START_PAGE; pageNumber <= totalPages; pageNumber++) {
				textStripper.setStartPage(pageNumber);
				textStripper.setEndPage(pageNumber);
				pageTexts.add(textStripper.getText(document));
			}
			return pageTexts;
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFページ単位テキスト抽出に失敗しました。path=" + inputPath, e);
		}
	}
}
