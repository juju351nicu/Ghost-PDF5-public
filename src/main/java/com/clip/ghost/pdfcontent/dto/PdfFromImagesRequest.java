package com.clip.ghost.pdfcontent.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.enums.PdfImagePageSize;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * 画像からPDFを作る条件を受け取るリクエストフォーム。
 * <p>
 * multipartで受け取った画像を送信順にページへ並べて1つのPDFにする。
 * 複数ページTIFFはファイル内のページ順に展開する。
 */
@Schema(description = "画像からPDFを作る条件を受け取るmultipartフォーム。")
@Getter
@Setter
public class PdfFromImagesRequest {
	/** PDFへ変換する画像ファイル。送信順にページへ並べる。 */
	@Schema(description = "PDFへ変換する画像ファイル。送信順にページへ並べます。PNG / JPEG / TIFF / BMPを指定できます。", type = "array", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("imageFiles")
	@NotEmpty(message = "ファイルを入れてください。")
	private List<MultipartFile> imageFiles;

	/** ページサイズの決め方。省略時はA4に収める。 */
	@Schema(description = "ページサイズの決め方。省略時はA4に収めます。", example = "A4", allowableValues = { "A4", "FIT" })
	@JsonProperty("pageSize")
	private PdfImagePageSize pageSize;
}
