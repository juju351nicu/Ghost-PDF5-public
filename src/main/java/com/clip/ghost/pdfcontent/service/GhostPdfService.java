package com.clip.ghost.pdfcontent.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.pdfcontent.enums.PdfInsertOption;
import com.clip.ghost.pdfcontent.logic.GhostPdfLogic;
import com.clip.ghost.pdfcontent.dto.ExtractPdfRequest;
import com.clip.ghost.pdfcontent.dto.GhostPdfDto;
import com.clip.ghost.pdfcontent.dto.InsertPdfRequest;
import com.clip.ghost.pdfcontent.dto.MergePdfRequest;
import com.clip.ghost.pdfcontent.dto.OriginalPdfRequest;
import com.clip.ghost.pdfcontent.dto.PdfMetadataResponse;
import com.clip.ghost.pdfcontent.dto.PdfTextResponse;
import com.clip.ghost.pdfcontent.dto.SplitPdfRequest;
import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.common.utils.ResponseUtils;

import lombok.RequiredArgsConstructor;

/**
 * PDF操作APIのリクエストをPDF処理ロジックへ橋渡しするサービス。
 * <p>
 * Controllerが受け取ったMultipartFileを一時ファイルへ保存し、{@link GhostPdfLogic}
 * でPDF加工した結果をHTTPレスポンスとして返却する。
 * リクエストDTOから内部DTOへの変換、null補完、デフォルト値設定はこのクラスに集約し、PDF加工仕様自体は
 * {@link GhostPdfLogic} に閉じ込める。
 */
@Service
@RequiredArgsConstructor
public class GhostPdfService {
	private static final int DEFAULT_INSERT_PAGE = -1;
	private static final String SPLIT_ZIP_FILE_NAME = "split.zip";

	private final GhostPdfLogic pdfLogic;

	/**
	 * アップロードされたPDFをプレビュー用のレスポンスとして返却する。
	 *
	 * @param form 編集元PDFを含むフォーム
	 * @return PDFのbyte配列レスポンス
	 */
	public ResponseEntity<byte[]> showPdf(OriginalPdfRequest form) {
		Path originalFilePath = pdfLogic.loadPdf(form.getOriginalFile());
		return buildPdfResponse(originalFilePath);
	}

	/**
	 * アップロードされたPDFの基本メタデータを返却する。
	 *
	 * @param form 編集元PDFを含むフォーム
	 * @return PDFの基本情報レスポンス
	 */
	public ResponseEntity<ApiResult<PdfMetadataResponse>> getPdfMetadata(OriginalPdfRequest form) {
		MultipartFile originalFile = form.getOriginalFile();
		Path originalFilePath = pdfLogic.loadPdf(originalFile);
		PdfMetadataResponse metadata = pdfLogic.getPdfMetadata(originalFilePath, originalFile.getOriginalFilename(),
				originalFile.getSize());
		return ResponseEntity.ok(ApiResult.of(metadata));
	}

	/**
	 * アップロードされたPDFからテキストを抽出して返却する。
	 *
	 * @param form 編集元PDFを含むフォーム
	 * @return PDFテキスト抽出レスポンス
	 */
	public ResponseEntity<ApiResult<PdfTextResponse>> extractPdfText(OriginalPdfRequest form) {
		MultipartFile originalFile = form.getOriginalFile();
		Path originalFilePath = pdfLogic.loadPdf(originalFile);
		PdfTextResponse textResponse = pdfLogic.extractPdfText(originalFilePath, originalFile.getOriginalFilename(),
				originalFile.getSize());
		return ResponseEntity.ok(ApiResult.of(textResponse));
	}

	/**
	 * アップロードされたPDFから指定ページだけを抽出し、PDFレスポンスとして返却する。
	 *
	 * @param form 編集元PDFと抽出ページ番号を含むフォーム
	 * @return 抽出後PDFのbyte配列レスポンス
	 */
	public ResponseEntity<byte[]> extractPdfByPages(ExtractPdfRequest form) {
		Path inputPath = pdfLogic.loadPdf(form.getOriginalFile());
		Path extractPath = pdfLogic.extractPdf(form.getExtractPages(), inputPath);
		return buildPdfResponse(extractPath);
	}

	/**
	 * アップロードされた複数PDFを結合し、PDFレスポンスとして返却する。
	 *
	 * @param form 結合対象PDFを含むフォーム
	 * @return 結合後PDFのbyte配列レスポンス
	 */
	public ResponseEntity<byte[]> mergePdfs(MergePdfRequest form) {
		List<Path> inputPaths = CollectionUtils.emptyIfNull(form.getMergeFiles()).stream().map(pdfLogic::loadPdf)
				.toList();
		Path mergePath = pdfLogic.mergePdf(inputPaths);
		return buildPdfResponse(mergePath);
	}

	/**
	 * アップロードされたPDFを1ページずつ分割し、ZIPレスポンスとして返却する。
	 *
	 * @param form 分割対象PDFを含むフォーム
	 * @return 分割後PDFを格納したZIPのbyte配列レスポンス
	 */
	public ResponseEntity<byte[]> splitPdf(SplitPdfRequest form) {
		Path inputPath = pdfLogic.loadPdf(form.getOriginalFile());
		Path splitZipPath = pdfLogic.splitPdf(inputPath);
		byte[] contents = pdfLogic.convertTemporaryFile(splitZipPath);
		return ResponseUtils.downloadZip(SPLIT_ZIP_FILE_NAME, contents);
	}

	/**
	 * アップロードされたPDFから指定ページを削除し、PDFレスポンスとして返却する。
	 *
	 * @param form 編集元PDFと削除ページ番号を含むフォーム
	 * @return 削除後PDFのbyte配列レスポンス
	 */
	public ResponseEntity<byte[]> deletePdfByPages(OriginalPdfRequest form) {
		Path inputPath = pdfLogic.loadPdf(form.getOriginalFile());
		Path deletePath = pdfLogic.deletePdf(form.getOriginalDeletePages(), inputPath);
		return buildPdfResponse(deletePath);
	}

	/**
	 * アップロードされたPDFに別PDFを差し込み、PDFレスポンスとして返却する。
	 *
	 * @param form 編集元PDF、削除ページ番号、差し込みPDF情報を含むフォーム
	 * @return 差し込み後PDFのbyte配列レスポンス
	 */
	public ResponseEntity<byte[]> insertPdfs(OriginalPdfRequest form) {
		Path inputPath = pdfLogic.loadPdf(form.getOriginalFile());
		inputPath = deleteOriginalPagesIfRequested(form, inputPath);

		List<GhostPdfDto> insertPdfDtoList = buildInsertPdfDtos(form);
		Path mergePath = pdfLogic.insertPdf(inputPath, insertPdfDtoList);
		return buildPdfResponse(mergePath);
	}

	/**
	 * PDFファイルをbyte配列へ変換し、既存のPDFダウンロード用レスポンスを組み立てる。
	 *
	 * @param pdfPath レスポンス化するPDFの一時ファイルパス
	 * @return PDFのbyte配列レスポンス
	 */
	private ResponseEntity<byte[]> buildPdfResponse(Path pdfPath) {
		byte[] contents = pdfLogic.convertPdf(pdfPath);
		return ResponseUtils.getResponseBytes(contents);
	}

	/**
	 * 差し込み前に元PDFから削除指定ページを取り除く。
	 * <p>
	 * 削除ページが未指定の場合はPDFBox処理を呼ばず、アップロード直後の一時ファイルをそのまま後続処理へ渡す。
	 *
	 * @param form      編集元PDFの削除ページ指定を含むリクエスト
	 * @param inputPath 編集元PDFの一時ファイルパス
	 * @return 削除後PDF、または削除指定なしの場合は入力されたPDFパス
	 */
	private Path deleteOriginalPagesIfRequested(OriginalPdfRequest form, Path inputPath) {
		if (CollectionUtils.isEmpty(form.getOriginalDeletePages())) {
			return inputPath;
		}
		return pdfLogic.deletePdf(form.getOriginalDeletePages(), inputPath);
	}

	/**
	 * 差し込みPDFフォームをPDF処理ロジック用のDTOリストへ変換する。
	 * <p>
	 * ファイル未選択の行はフロントエンド上の空行として扱い、PDF処理から除外する。 ページ番号未指定時は既存仕様に合わせて
	 * {@code -1}、差し込み種別未指定時は末尾挿入として扱う。
	 *
	 * @param form 差し込みPDFフォームを含むリクエスト
	 * @return PDF処理ロジックへ渡す差し込みPDF情報
	 */
	private List<GhostPdfDto> buildInsertPdfDtos(OriginalPdfRequest form) {
		return CollectionUtils.emptyIfNull(form.getInsertPdfForm()).stream().filter(Objects::nonNull)
				.filter(this::hasSelectedInsertFile)
				.map(insertPdfForm -> buildInsertPdfDto(insertPdfForm, insertPdfForm.getInsertFile())).toList();
	}

	/**
	 * 差し込みPDFフォーム行に処理対象ファイルが選択されているか判定する。
	 * <p>
	 * null行やファイル未選択行は、フロントエンド上の空行としてPDF処理から除外する。
	 *
	 * @param insertPdfForm 差し込みPDFフォーム1行分
	 * @return 処理対象ファイルが存在する場合はtrue
	 */
	private boolean hasSelectedInsertFile(InsertPdfRequest insertPdfForm) {
		MultipartFile insertFile = insertPdfForm.getInsertFile();
		return Objects.nonNull(insertFile) && !insertFile.isEmpty();
	}

	/**
	 * 差し込みPDFフォーム1行分をPDF処理ロジック用DTOへ変換する。
	 * <p>
	 * DTO生成時のデフォルト値をこのメソッドに集約し、呼び出し側で {@link GhostPdfDto} のsetterを直接並べないようにする。
	 *
	 * @param insertPdfForm 差し込みPDFフォーム1行分
	 * @param insertFile    差し込み対象PDFファイル
	 * @return PDF処理ロジック用DTO
	 */
	private GhostPdfDto buildInsertPdfDto(InsertPdfRequest insertPdfForm, MultipartFile insertFile) {
		GhostPdfDto dto = new GhostPdfDto();
		dto.setInsertPath(pdfLogic.loadPdf(insertFile));
		dto.setInsertPage(resolveInsertPage(insertPdfForm));
		dto.setInsertOption(resolveInsertOption(insertPdfForm));
		return dto;
	}

	/**
	 * 差し込みページ未指定時の既存デフォルト値を返す。
	 *
	 * @param insertPdfForm 差し込みPDFフォーム1行分
	 * @return 差し込みページ番号。未指定時は末尾挿入扱いの {@value #DEFAULT_INSERT_PAGE}
	 */
	private int resolveInsertPage(InsertPdfRequest insertPdfForm) {
		Integer insertPage = insertPdfForm.getInsertPage();
		return insertPage == null ? DEFAULT_INSERT_PAGE : insertPage;
	}

	/**
	 * 差し込み種別未指定時の既存デフォルト値を返す。
	 *
	 * @param insertPdfForm 差し込みPDFフォーム1行分
	 * @return 差し込み種別。未指定時は末尾挿入
	 */
	private int resolveInsertOption(InsertPdfRequest insertPdfForm) {
		Integer insertOption = insertPdfForm.getInsertOption();
		return insertOption == null ? PdfInsertOption.LAST_INSERT.getKey() : insertOption;
	}
}
