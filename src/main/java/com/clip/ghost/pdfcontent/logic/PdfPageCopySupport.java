package com.clip.ghost.pdfcontent.logic;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFCloneUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * PDFBoxのページ辞書、表示領域、継承リソースを出力PDF向けに複製する内部クラス。
 * <p>
 * {@link PDFCloneUtility} の複製キャッシュを安全に共有するため、同じ複製元PDFのページ群ごとに1インスタンスを使用する。
 * 複製元PDFが変わる場合は新しいインスタンスを作成する。
 */
final class PdfPageCopySupport {
	/** ページ追加先のPDFドキュメント。 */
	private final PDDocument outputDocument;

	/** 同じ複製元PDF内で共有するPDFBoxクローン処理。 */
	private final PDFCloneUtility pageCloner;

	/**
	 * 指定された出力PDF向けのページ複製処理を初期化する。
	 *
	 * @param outputDocument ページ追加先のPDFドキュメント
	 */
	PdfPageCopySupport(PDDocument outputDocument) {
		this.outputDocument = outputDocument;
		this.pageCloner = new PageCloner(outputDocument);
	}

	/**
	 * 指定PDFの全ページを出力PDFへ追加する。
	 *
	 * @param sourcePath 複製元PDFのパス
	 * @throws IOException PDFの読み込みまたはページ追加に失敗した場合
	 */
	void appendDocument(Path sourcePath) throws IOException {
		try (PDDocument sourceDocument = Loader.loadPDF(sourcePath.toFile())) {
			for (int pageIndex = 0; pageIndex < sourceDocument.getNumberOfPages(); pageIndex++) {
				appendPage(sourceDocument.getPage(pageIndex));
			}
		}
	}

	/**
	 * ページ本体と継承リソースを出力PDFへ複製する。
	 *
	 * @param sourcePage 複製元ページ
	 * @throws IOException ページまたはリソースの複製に失敗した場合
	 */
	void appendPage(PDPage sourcePage) throws IOException {
		PDPage outputPage = clonePage(sourcePage);
		copyPageLayout(sourcePage, outputPage);
		copyPageResources(sourcePage, outputPage);
		outputDocument.addPage(outputPage);
	}

	/**
	 * 複製元ページの辞書を出力PDF向けに複製する。
	 *
	 * @param sourcePage 複製元ページ
	 * @return 出力PDF向けに複製したページ
	 * @throws IOException ページ辞書の複製に失敗した場合
	 */
	private PDPage clonePage(PDPage sourcePage) throws IOException {
		COSDictionary pageDictionary = new COSDictionary(sourcePage.getCOSObject());
		// 親参照は複製元PDFのページツリーを指すため、別ドキュメントへ持ち込まない。
		pageDictionary.removeItem(COSName.PARENT);
		return new PDPage(pageCloner.cloneForNewDocument(pageDictionary));
	}

	/**
	 * ページの表示領域と回転を複製する。
	 *
	 * @param sourcePage 複製元ページ
	 * @param outputPage 出力PDFへ追加するページ
	 */
	private void copyPageLayout(PDPage sourcePage, PDPage outputPage) {
		outputPage.setCropBox(new PDRectangle(sourcePage.getCropBox().getCOSArray()));
		outputPage.setMediaBox(new PDRectangle(sourcePage.getMediaBox().getCOSArray()));
		outputPage.setRotation(sourcePage.getRotation());
	}

	/**
	 * ページから解決したリソースを出力PDF向けに複製する。
	 *
	 * @param sourcePage 複製元ページ
	 * @param outputPage 出力PDFへ追加するページ
	 * @throws IOException リソースの複製に失敗した場合
	 */
	private void copyPageResources(PDPage sourcePage, PDPage outputPage) throws IOException {
		// getResources()でページツリーから継承されたフォントや画像も解決する。
		PDResources resources = sourcePage.getResources();
		if (resources != null) {
			outputPage.setResources(new PDResources(pageCloner.cloneForNewDocument(resources.getCOSObject())));
		}
	}

	/**
	 * protectedのPDFBoxコンストラクタを、この内部処理から利用するためのラッパー。
	 */
	private static final class PageCloner extends PDFCloneUtility {
		/**
		 * 指定された出力PDF向けのPDFBoxクローン処理を初期化する。
		 *
		 * @param outputDocument 複製先PDFドキュメント
		 */
		private PageCloner(PDDocument outputDocument) {
			super(outputDocument);
		}
	}
}
