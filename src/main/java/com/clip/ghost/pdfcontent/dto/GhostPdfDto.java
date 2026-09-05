package com.clip.ghost.pdfcontent.dto;

import java.nio.file.Path;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PDFロジック層へ差し込み・差し替え条件を渡す内部DTO。
 * <p>
 * ControllerのリクエストDTOとは分離し、service層のbuild系メソッドで生成する。これにより、画面入力のnull補完やデフォルト値設定を
 * service層に閉じ込める。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GhostPdfDto {
	/** 差し込み・差し替え対象のページ番号。 */
	private Integer insertPage;

	/** 差し込み・差し替え対象PDFの一時保存パス。 */
	private Path insertPath;

	/** 差し込み・差し替え方法の選択オプション。 */
	private Integer insertOption;
}
