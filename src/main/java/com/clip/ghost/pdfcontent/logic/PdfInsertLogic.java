package com.clip.ghost.pdfcontent.logic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.clip.ghost.pdfcontent.dto.GhostPdfDto;
import com.clip.ghost.pdfcontent.enums.PdfInsertOption;
import com.clip.ghost.pdfcontent.exception.PdfProcessingException;

import lombok.NoArgsConstructor;

/**
 * PDFの差し込み、置換、末尾挿入を担当する内部クラス。
 * <p>
 * 編集元PDFの1始まりページ番号をキーに出力順を組み立て、{@link PdfPageCopySupport} を使って出力PDFへ複製する。
 * 入出力の一時ファイル削除はFacade側へ委ねる。
 */
@NoArgsConstructor
final class PdfInsertLogic {

	/**
	 * 編集元PDFに指定されたPDFを差し込み、置換、または末尾挿入する。
	 *
	 * @param originalFilePath 編集元PDFのパス
	 * @param insertPdfDtos    差し込みPDFのパス、対象ページ、挿入オプション
	 * @param outputPath       編集後PDFの出力先パス
	 * @throws PdfProcessingException PDFの読み込み、差し込み、保存に失敗した場合
	 */
	void insertPdf(Path originalFilePath, List<GhostPdfDto> insertPdfDtos, Path outputPath) {
		try (PDDocument outputDocument = new PDDocument();
				PDDocument originalDocument = Loader.loadPDF(originalFilePath.toFile())) {
			int totalPages = originalDocument.getNumberOfPages();
			Map<Integer, List<PdfSegment>> mergePlanByPageNumber = buildInitialMergePlan(totalPages);
			// リクエスト順に計画を書き換えることで、同一ページへの複数差し込み順も維持する。
			applyInsertRequests(mergePlanByPageNumber, insertPdfDtos, totalPages);
			appendMergePlan(outputDocument, originalDocument, mergePlanByPageNumber, totalPages);
			outputDocument.save(outputPath.toFile());
		} catch (IllegalArgumentException | IllegalStateException | IOException e) {
			throw new PdfProcessingException("PDFの差し込み処理に失敗しました。path=" + originalFilePath, e);
		}
	}

	/**
	 * 編集元PDFの全ページを、ページ番号順の結合計画として初期化する。
	 *
	 * @param totalPages 編集元PDFの総ページ数
	 * @return ページ番号をキーにした結合計画
	 */
	private Map<Integer, List<PdfSegment>> buildInitialMergePlan(int totalPages) {
		Map<Integer, List<PdfSegment>> mergePlanByPageNumber = new LinkedHashMap<>();
		for (int pageNumber = PdfConstants.START_PAGE; pageNumber <= totalPages; pageNumber++) {
			List<PdfSegment> originalPageSegment = new ArrayList<>();
			originalPageSegment.add(PdfSegment.originalPage(pageNumber - PdfConstants.START_PAGE));
			mergePlanByPageNumber.put(pageNumber, originalPageSegment);
		}
		return mergePlanByPageNumber;
	}

	/**
	 * 差し込みリクエストを結合計画へ反映する。
	 *
	 * @param mergePlanByPageNumber ページ番号をキーにした結合計画
	 * @param insertPdfDtos         差し込みPDFのパス、対象ページ、挿入オプション
	 * @param totalPages            編集元PDFの総ページ数
	 */
	private void applyInsertRequests(Map<Integer, List<PdfSegment>> mergePlanByPageNumber,
			List<GhostPdfDto> insertPdfDtos, int totalPages) {
		CollectionUtils.emptyIfNull(insertPdfDtos).stream().filter(Objects::nonNull)
				.filter(insertPdfDto -> shouldApplyInsertRequest(insertPdfDto, totalPages)).forEach(insertPdfDto -> {
					PdfInsertOption option = PdfInsertOption.fromKey(insertPdfDto.getInsertOption());
					PdfSegment insertDocument = PdfSegment.insertDocument(insertPdfDto.getInsertPath());
					applyInsertRequest(mergePlanByPageNumber, insertPdfDto.getInsertPage(), totalPages, option,
							insertDocument);
				});
	}

	/**
	 * 差し込みリクエストを結合計画へ反映するか判定する。
	 * <p>
	 * 既存仕様に合わせ、差し込みPDFパスまたはページ番号が未指定の場合は無視し、 最終ページより後ろの場合も無視する。末尾挿入は {@code -1}
	 * を使うため、下限チェックはここでは行わない。
	 *
	 * @param insertPdfDto 差し込みPDFのパス、対象ページ、挿入オプション
	 * @param totalPages   編集元PDFの総ページ数
	 * @return 結合計画へ反映する場合はtrue
	 */
	private boolean shouldApplyInsertRequest(GhostPdfDto insertPdfDto, int totalPages) {
		return Objects.nonNull(insertPdfDto.getInsertPath()) && Objects.nonNull(insertPdfDto.getInsertPage())
				&& insertPdfDto.getInsertPage() <= totalPages;
	}

	/**
	 * 差し込み種別ごとの結合計画を作成する。
	 *
	 * @param mergePlanByPageNumber ページ番号をキーにした結合計画
	 * @param insertPage            差し込み対象ページ番号
	 * @param totalPages            編集元PDFの総ページ数
	 * @param option                差し込み種別
	 * @param insertDocument        差し込みPDFを表す結合要素
	 */
	private void applyInsertRequest(Map<Integer, List<PdfSegment>> mergePlanByPageNumber, int insertPage,
			int totalPages, PdfInsertOption option, PdfSegment insertDocument) {
		switch (option) {
		case INSERT:
			appendAfterTargetPage(mergePlanByPageNumber, insertPage, insertDocument);
			break;
		case REPLACE:
			replaceTargetPage(mergePlanByPageNumber, insertPage, insertDocument);
			break;
		case LAST_INSERT:
			appendAfterLastPage(mergePlanByPageNumber, totalPages, insertDocument);
			break;
		default:
			throw new IllegalStateException("Unexpected value: " + option);
		}
	}

	/**
	 * 指定ページの直後へ差し込みPDFを追加する。
	 *
	 * @param mergePlanByPageNumber ページ番号をキーにした結合計画
	 * @param insertPage            差し込み対象ページ番号
	 * @param insertDocument        差し込みPDFを表す結合要素
	 */
	private void appendAfterTargetPage(Map<Integer, List<PdfSegment>> mergePlanByPageNumber, int insertPage,
			PdfSegment insertDocument) {
		List<PdfSegment> mergeSegments = new ArrayList<>(mergePlanByPageNumber.get(insertPage));
		mergeSegments.add(insertDocument);
		mergePlanByPageNumber.put(insertPage, mergeSegments);
	}

	/**
	 * 指定ページを差し込みPDFで置き換える。
	 *
	 * @param mergePlanByPageNumber ページ番号をキーにした結合計画
	 * @param insertPage            差し込み対象ページ番号
	 * @param insertDocument        差し込みPDFを表す結合要素
	 */
	private void replaceTargetPage(Map<Integer, List<PdfSegment>> mergePlanByPageNumber, int insertPage,
			PdfSegment insertDocument) {
		List<PdfSegment> replaceSegments = new ArrayList<>();
		replaceSegments.add(insertDocument);
		mergePlanByPageNumber.put(insertPage, replaceSegments);
	}

	/**
	 * 編集元PDFの末尾へ差し込みPDFを追加する。
	 *
	 * @param mergePlanByPageNumber ページ番号をキーにした結合計画
	 * @param totalPages            編集元PDFの総ページ数
	 * @param insertDocument        差し込みPDFを表す結合要素
	 */
	private void appendAfterLastPage(Map<Integer, List<PdfSegment>> mergePlanByPageNumber, int totalPages,
			PdfSegment insertDocument) {
		List<PdfSegment> mergeSegments = new ArrayList<>(mergePlanByPageNumber.get(totalPages));
		mergeSegments.add(insertDocument);
		mergePlanByPageNumber.put(totalPages, mergeSegments);
	}

	/**
	 * 結合計画の順に編集元PDFページまたは差し込みPDFを出力PDFへ追加する。
	 *
	 * @param outputDocument        追加先のPDFドキュメント
	 * @param originalDocument      編集元PDFドキュメント
	 * @param mergePlanByPageNumber ページ番号をキーにした結合計画
	 * @param totalPages            編集元PDFの総ページ数
	 * @throws IOException PDFの読み込みまたはページ追加に失敗した場合
	 */
	private void appendMergePlan(PDDocument outputDocument, PDDocument originalDocument,
			Map<Integer, List<PdfSegment>> mergePlanByPageNumber, int totalPages) throws IOException {
		// 元PDFの複数ページでは同じクローンキャッシュを共有し、各差し込みPDFは独立させる。
		PdfPageCopySupport originalPageCopySupport = new PdfPageCopySupport(outputDocument);
		for (int pageNumber = PdfConstants.START_PAGE; pageNumber <= totalPages; pageNumber++) {
			List<PdfSegment> mergeSegments = mergePlanByPageNumber.get(pageNumber);
			for (PdfSegment pdfSegment : mergeSegments) {
				if (pdfSegment.isOriginalPage()) {
					originalPageCopySupport.appendPage(originalDocument.getPage(pdfSegment.pageIndex()));
				} else {
					new PdfPageCopySupport(outputDocument).appendDocument(pdfSegment.path());
				}
			}
		}
	}

	/**
	 * 結合後のページ順を表す内部情報。
	 * <p>
	 * 編集元PDFの単一ページ、または差し込みPDF全体のどちらかを1要素として扱い、 既存仕様の差し込み・差し替え・末尾挿入のページ順を組み立てる。
	 *
	 * @param path      差し込みPDFのパス。編集元PDFページの場合はnull
	 * @param pageIndex 編集元PDFの0始まりページ番号。差し込みPDFの場合はnull
	 */
	private record PdfSegment(Path path, Integer pageIndex) {
		/**
		 * 編集元PDFの単一ページを表す要素を作成する。
		 *
		 * @param pageIndex 編集元PDFの0始まりページ番号
		 * @return 編集元PDFページを表す要素
		 */
		private static PdfSegment originalPage(int pageIndex) {
			return new PdfSegment(null, pageIndex);
		}

		/**
		 * 差し込みPDF全体を表す要素を作成する。
		 *
		 * @param path 差し込みPDFのパス
		 * @return 差し込みPDFを表す要素
		 */
		private static PdfSegment insertDocument(Path path) {
			return new PdfSegment(path, null);
		}

		/**
		 * 編集元PDFのページを表す要素か判定する。
		 *
		 * @return 編集元PDFページの場合はtrue
		 */
		private boolean isOriginalPage() {
			return pageIndex != null;
		}
	}
}
