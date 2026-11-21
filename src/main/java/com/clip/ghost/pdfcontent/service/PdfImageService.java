package com.clip.ghost.pdfcontent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.clip.ghost.common.utils.FileInfoUtils;
import com.clip.ghost.common.utils.FileOperationUtils;
import com.clip.ghost.common.utils.PathUtils;
import com.clip.ghost.common.utils.ResponseUtils;
import com.clip.ghost.pdfcontent.config.PdfImageProperties;
import com.clip.ghost.pdfcontent.dto.PdfFromImagesRequest;
import com.clip.ghost.pdfcontent.dto.PdfImagesRequest;
import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.clip.ghost.pdfcontent.enums.PdfRotation;
import com.clip.ghost.pdfcontent.exception.PdfImageInputException;
import com.clip.ghost.pdfcontent.exception.PdfRenderDpiException;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;

import lombok.RequiredArgsConstructor;

/**
 * PDFと画像の相互変換を担当するサービス。
 * <p>
 * PDFの保存、画像化、画像からのPDF生成は {@link GhostPdfLogic} へ委譲し、このクラスは
 * 解像度とページサイズの既定値補完、および解像度の上限検証を担当する。
 * 解像度とページ数上限は {@link PdfImageProperties}（{@code ghost.pdf.image}）から取る。
 * ローカル画像フォルダの変換では、対象画像の選択・並び順と完成したPDFの保存も担当する。
 */
@Service
@RequiredArgsConstructor
public class PdfImageService {
	private static final String IMAGES_ZIP_FILE_NAME = "images.zip";

	private final GhostPdfLogic pdfLogic;
	private final PdfImageProperties properties;

	/**
	 * HTTPや画像化設定を使わないローカル画像フォルダ変換用のサービスを生成する。
	 *
	 * @return Spring Contextを起動せずに利用できるサービス
	 */
	public static PdfImageService createForLocalFiles() {
		return new PdfImageService(new GhostPdfLogic(), new PdfImageProperties());
	}

	/**
	 * アップロードされたPDFのページを画像化し、ZIPレスポンスとして返却する。
	 *
	 * @param form 画像化元PDF、画像形式、解像度、対象ページを含むフォーム
	 * @return 画像を格納したZIPのダウンロードレスポンス
	 * @throws PdfRenderDpiException 指定された解像度が {@code ghost.pdf.image.max-dpi} を超えた場合
	 */
	public ResponseEntity<Resource> exportPdfImages(PdfImagesRequest form) {
		int renderDpi = resolveRenderDpi(form.getDpi());
		Path inputPath = pdfLogic.loadPdf(form.getOriginalFile(), form.getPassword());
		Path imagesZipPath = pdfLogic.exportPdfImages(inputPath, form.getFormat(), renderDpi, properties.getMaxPages(),
				form.getImagePages());
		return ResponseUtils.downloadZip(IMAGES_ZIP_FILE_NAME, pdfLogic.openTemporaryFileForResponse(imagesZipPath));
	}

	/**
	 * アップロードされた画像を1つのPDFへまとめ、PDFレスポンスとして返却する。
	 *
	 * @param form PDF化する画像とページサイズを含むフォーム
	 * @return 生成したPDFのinline表示レスポンス
	 * @throws PdfImageInputException 画像として読めないファイルが含まれる場合
	 */
	public ResponseEntity<Resource> createPdfFromImages(PdfFromImagesRequest form) {
		PdfImagePageSize pageSize = Objects.isNull(form.getPageSize()) ? PdfImagePageSize.A4 : form.getPageSize();
		Path pdfPath = pdfLogic.createPdfFromImages(form.getImageFiles(), pageSize);
		return ResponseUtils.inlinePdf(pdfLogic.openTemporaryFileForResponse(pdfPath));
	}

	/**
	 * フォルダ直下の画像をファイル名の辞書順でPDFへまとめる。入力画像と既存の出力は変更しない。
	 * <p>
	 * 完成するまで同じ保存先の一時ファイルへ書き、成功時だけ出力名へ移す。
	 * 画像が途中で読めなくても、不完全なPDFを成果物として残さないため。
	 *
	 * @param directory  画像フォルダ。サブフォルダは対象外
	 * @param outputPath PDFの保存先。既存ファイルは上書きしない
	 * @param pageSize   ページサイズの決め方
	 * @param rotation   表示回転角。nullの場合は回転しない
	 * @throws IllegalArgumentException 入力がフォルダでない、画像がない、または出力先が不正な場合
	 * @throws IOException PDFの保存に失敗した場合
	 */
	public void createPdfFromImageDirectory(Path directory, Path outputPath, PdfImagePageSize pageSize,
			PdfRotation rotation) throws IOException {
		if (!Files.isDirectory(directory)) {
			throw new IllegalArgumentException("画像フォルダが見つかりません: " + directory);
		}
		Path destination = outputPath.toAbsolutePath().normalize();
		if (!PathUtils.isPdfFileName(destination.getFileName().toString())) {
			throw new IllegalArgumentException("出力ファイルの拡張子は.pdfにしてください。");
		}
		if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
			throw new IllegalArgumentException("出力先は既に存在します。別のファイル名を指定してください: " + destination);
		}
		List<Path> imagePaths = FileInfoUtils.getFilePaths(directory, false).stream()
				.filter(path -> Strings.CI.equalsAny(PathUtils.getExtension(path.getFileName().toString()),
						"png", "jpg", "jpeg", "tif", "tiff", "bmp"))
				.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
		if (CollectionUtils.isEmpty(imagePaths)) {
			throw new IllegalArgumentException("フォルダ直下にPNG / JPEG / TIFF / BMP画像がありません。");
		}
		FileOperationUtils.createDirectories(destination.getParent());
		Path temporaryPdf = Files.createTempFile(destination.getParent(), ".ghost-images-", ".pdf");
		try {
			pdfLogic.createPdfFromImagePaths(imagePaths, temporaryPdf, pageSize, rotation);
			// REPLACE_EXISTINGを付けず、変換中に作られた出力ファイルも上書きしない。
			Files.move(temporaryPdf, destination);
		} finally {
			FileOperationUtils.deleteFile(temporaryPdf);
		}
	}

	/**
	 * 使用する解像度を決める。
	 * <p>
	 * 未指定なら設定の既定値を使い、上限を超える指定は拒否する。上限で丸めずに拒否するのは、
	 * ZIPレスポンスには「丸めた」と伝える経路が無く、利用者が指定と違う画像を受け取ったことに気付けないため。
	 *
	 * @param requestedDpi リクエストで指定された解像度。未指定の場合はnull
	 * @return 画像化に使用する解像度
	 * @throws PdfRenderDpiException 指定された解像度が上限を超えた場合
	 */
	private int resolveRenderDpi(Integer requestedDpi) {
		if (Objects.isNull(requestedDpi)) {
			return properties.getDefaultDpi();
		}
		if (requestedDpi > properties.getMaxDpi()) {
			throw new PdfRenderDpiException(requestedDpi, properties.getMaxDpi());
		}
		return requestedDpi;
	}
}
