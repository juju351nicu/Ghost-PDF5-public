package com.clip.ghost.officecontent.service;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.common.response.ApiResult;
import com.clip.ghost.officecontent.dto.OfficeMarkdownRequest;
import com.clip.ghost.officecontent.dto.OfficeMarkdownResponse;
import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.logic.OfficeMarkdownLogic;

import lombok.RequiredArgsConstructor;

/**
 * Office文書からMarkdownを起こすサービス。
 * <p>
 * 読み取りは {@link OfficeMarkdownLogic} へ委譲し、このクラスは形式の判定とレスポンスDTOの組み立てを担当する。
 */
@Service
@RequiredArgsConstructor
public class OfficeMarkdownService {
	private static final String UNKNOWN_FILE_NAME = "（ファイル名不明）";

	private final OfficeMarkdownLogic officeMarkdownLogic;

	/**
	 * アップロードされたOffice文書からMarkdownを起こす。
	 *
	 * @param form Markdownへ変換するOffice文書を含むフォーム
	 * @return 変換元情報とMarkdown本文を含むレスポンス
	 * @throws OfficeInputException 対応していない形式、またはOffice文書として読み込めない場合
	 */
	public ResponseEntity<ApiResult<OfficeMarkdownResponse>> generateMarkdown(OfficeMarkdownRequest form) {
		MultipartFile officeFile = form.getOfficeFile();
		OfficeDocumentType documentType = resolveDocumentType(officeFile);
		String markdown = officeMarkdownLogic.readMarkdown(officeFile, documentType);
		return ResponseEntity.ok(ApiResult.of(buildResponse(officeFile, documentType, markdown)));
	}

	/**
	 * アップロードファイルの拡張子からOffice形式を判定する。
	 *
	 * @param officeFile アップロードされたOffice文書
	 * @return 判定したOffice形式
	 * @throws OfficeInputException 対応する形式が無い場合
	 */
	private OfficeDocumentType resolveDocumentType(MultipartFile officeFile) {
		String fileName = StringUtils.defaultIfBlank(officeFile.getOriginalFilename(), UNKNOWN_FILE_NAME);
		OfficeDocumentType documentType = OfficeDocumentType.fromFileName(fileName);
		if (Objects.isNull(documentType)) {
			// 旧形式(.doc/.xls/.ppt)もここで弾かれる。読めないものを読もうとして壊れた結果を返すより、
			// 対応形式を名指しで伝えるほうが利用者は次の一手を決められる。
			throw new OfficeInputException(fileName);
		}
		return documentType;
	}

	/**
	 * 変換結果からレスポンスDTOを組み立てる。
	 *
	 * @param officeFile   アップロードされたOffice文書
	 * @param documentType 判定したOffice形式
	 * @param markdown     起こしたMarkdown本文
	 * @return Markdownレスポンス
	 */
	private OfficeMarkdownResponse buildResponse(MultipartFile officeFile, OfficeDocumentType documentType,
			String markdown) {
		OfficeMarkdownResponse response = new OfficeMarkdownResponse();
		response.setFileName(officeFile.getOriginalFilename());
		response.setFileSize(officeFile.getSize());
		response.setDocumentType(documentType.getKey());
		response.setMarkdown(markdown);
		return response;
	}
}
