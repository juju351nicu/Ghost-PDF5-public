package com.clip.ghost.officecontent.logic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.exception.OfficeProcessingException;

import lombok.NoArgsConstructor;

/**
 * PowerPointプレゼンテーション（.pptx）をスライド画像経由でPDFへ変換する内部ロジック。
 * <p>
 * Word / Excel はMarkdownを経由してPDFにするが、PowerPointだけはスライドを描画してページへ貼る。
 * スライドは図形の位置関係そのものが情報であり、テキストだけ抜き出すと資料として意味をなさないため。
 * 代わりに出力PDFのテキストは選択・検索できない。この割り切りはAPIの説明文へ明記する。
 * <p>
 * PDFの組み立ては {@code pdfcontent.logic.ImagesToPdfLogic} と似た処理になるが再利用しない。
 * あちらは {@code pdfcontent.service} からのみ参照できる内部クラスで（{@code CodingConventionTest} が依存方向を固定）、
 * 入力もアップロード済みファイルのパスであり、メモリ上の {@code BufferedImage} を渡す口を持たない。
 */
@Component
@NoArgsConstructor
public class PowerPointPdfLogic {
	private static final Logger LOGGER = LoggerFactory.getLogger(PowerPointPdfLogic.class);

	/**
	 * スライドの描画倍率。
	 * <p>
	 * POIのスライド寸法はポイント単位（96dpi相当ではなく72dpi相当）で返る。等倍で描くと文字がつぶれるため、
	 * 2倍で描いてからページへ縮小して貼り、実効的に144dpi相当の解像度を得る。
	 */
	private static final int RENDER_SCALE = 2;

	/**
	 * アップロードされたPowerPointプレゼンテーションをPDFのbyte配列へ変換する。
	 *
	 * @param officeFile アップロードされたプレゼンテーション
	 * @param fileName   エラーメッセージへ出すファイル名
	 * @return PDFのbyte配列
	 * @throws OfficeInputException      プレゼンテーションとして読み込めない場合
	 * @throws OfficeProcessingException 描画またはPDF組み立てに失敗した場合
	 */
	public byte[] renderPdf(MultipartFile officeFile, String fileName) {
		try (InputStream inputStream = officeFile.getInputStream();
				XMLSlideShow slideShow = new XMLSlideShow(inputStream);
				PDDocument document = new PDDocument();
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			Dimension slideSize = slideShow.getPageSize();
			List<XSLFSlide> slides = slideShow.getSlides();
			for (XSLFSlide slide : slides) {
				addSlidePage(document, slide, slideSize);
			}
			LOGGER.info("PowerPointからPDFを生成しました。slideCount={}", slides.size());
			document.save(outputStream);
			return outputStream.toByteArray();
		} catch (IOException | IllegalArgumentException | POIXMLException e) {
			// OOXMLではない・壊れている・空ファイルをPOIはこの3系統で知らせる。利用者が直せるため400扱いにする。
			throw new OfficeInputException(fileName, e);
		} catch (RuntimeException e) {
			throw new OfficeProcessingException("PowerPointからのPDF生成に失敗しました。fileName=" + fileName, e);
		}
	}

	/**
	 * 1スライドを描画してPDFのページとして追加する。
	 * <p>
	 * 1スライド描くごとに {@code BufferedImage} の参照を捨てる。全スライドを先に描くと、
	 * 枚数の多い資料でヒープを使い切る。
	 *
	 * @param document  追加先のPDFドキュメント
	 * @param slide     スライド
	 * @param slideSize スライドの寸法（ポイント）
	 * @throws IOException 画像の埋め込みまたはページ描画に失敗した場合
	 */
	private void addSlidePage(PDDocument document, XSLFSlide slide, Dimension slideSize) throws IOException {
		BufferedImage image = renderSlideImage(slide, slideSize);
		PDRectangle pageRectangle = new PDRectangle((float) slideSize.getWidth(), (float) slideSize.getHeight());
		PDPage page = new PDPage(pageRectangle);
		document.addPage(page);

		PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
		try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
			contentStream.drawImage(pdImage, 0, 0, pageRectangle.getWidth(), pageRectangle.getHeight());
		}
	}

	/**
	 * スライドを画像へ描画する。
	 *
	 * @param slide     スライド
	 * @param slideSize スライドの寸法（ポイント）
	 * @return 描画したスライド画像
	 */
	private BufferedImage renderSlideImage(XSLFSlide slide, Dimension slideSize) {
		int width = (int) Math.ceil(slideSize.getWidth() * RENDER_SCALE);
		int height = (int) Math.ceil(slideSize.getHeight() * RENDER_SCALE);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		try {
			// 背景を白で塗る。塗らないと未描画部分が黒のまま残る。
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.scale(RENDER_SCALE, RENDER_SCALE);
			slide.draw(graphics);
		} finally {
			graphics.dispose();
		}
		return image;
	}
}
