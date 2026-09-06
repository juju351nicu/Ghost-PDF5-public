package com.clip.ghost.pdfcontent.logic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfPageContent;
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

	/**
	 * PDFからページ単位でテキストを抽出し、文字を取得できないページはPNGへ画像化して返す。
	 * <p>
	 * 1つの {@code PDDocument} でテキスト抽出とレンダリングを行う。文字レイヤーが空白のページだけを画像化し、
	 * それ以外のページの {@code imageBytes} はnullにする。ページ順を維持し、空ページも省略しない。入力ファイルは削除しない。
	 *
	 * @param inputPath 読み込むPDFのパス
	 * @param renderDpi 画像化する解像度（DPI）
	 * @return PDF順のページ内容（テキストと、必要なページのPNGバイト列）
	 * @throws PdfProcessingException PDFの読み込み、テキスト抽出、または画像化に失敗した場合
	 */
	List<PdfPageContent> extractPdfPageContents(Path inputPath, int renderDpi) {
		try (PDDocument document = Loader.loadPDF(inputPath.toFile())) {
			PDFTextStripper textStripper = new PDFTextStripper();
			PDFRenderer renderer = new PDFRenderer(document);
			int totalPages = document.getNumberOfPages();
			List<PdfPageContent> contents = new ArrayList<>(totalPages);
			for (int pageNumber = PdfConstants.START_PAGE; pageNumber <= totalPages; pageNumber++) {
				textStripper.setStartPage(pageNumber);
				textStripper.setEndPage(pageNumber);
				String text = textStripper.getText(document);
				// 文字レイヤーが空白のページだけ画像化する。PDFBoxのレンダリングは0始まりのため1を引く。
				byte[] imageBytes = text.isBlank() ? renderPageToPng(renderer, pageNumber - 1, renderDpi) : null;
				contents.add(new PdfPageContent(pageNumber, text, imageBytes));
			}
			return contents;
		} catch (IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFページ内容の抽出に失敗しました。path=" + inputPath, e);
		}
	}

	/**
	 * 指定ページをPNGバイト列へ画像化する。
	 *
	 * @param renderer  PDFレンダラー
	 * @param pageIndex 0始まりのページインデックス
	 * @param renderDpi 解像度（DPI）
	 * @return PNGバイト列
	 * @throws IOException レンダリングまたはPNGエンコードに失敗した場合
	 */
	private byte[] renderPageToPng(PDFRenderer renderer, int pageIndex, int renderDpi) throws IOException {
		BufferedImage image = renderer.renderImageWithDPI(pageIndex, renderDpi, ImageType.RGB);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(image, "png", outputStream);
		return outputStream.toByteArray();
	}
}
