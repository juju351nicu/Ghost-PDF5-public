package com.clip.ghost.officecontent.logic;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ooxml.POIXMLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.officecontent.enums.OfficeDocumentType;
import com.clip.ghost.officecontent.exception.OfficeInputException;
import com.clip.ghost.officecontent.exception.OfficeProcessingException;

import lombok.NoArgsConstructor;

/**
 * Office文書（.docx / .xlsx / .pptx）をMarkdownへ起こすFacade。
 * <p>
 * Apache POIへの依存はこのpackage（{@code officecontent.logic}）に閉じ込め、Service層は
 * 形式enumとMarkdown文字列だけを扱う。この境界は {@code CodingConventionTest} が機械的に守る。
 * <p>
 * 出力するMarkdownは、既存のMarkdown編集欄・プレビュー・PDF出力がそのまま扱える普通のMarkdownにする。
 * Office専用の記法を足すと、下流のレンダラーを1つずつ対応させることになる。
 */
@Component
@NoArgsConstructor
public class OfficeMarkdownLogic {
	private static final Logger LOGGER = LoggerFactory.getLogger(OfficeMarkdownLogic.class);
	private static final String UNKNOWN_FILE_NAME = "（ファイル名不明）";

	/** Word文書の読み取り。 */
	private final WordMarkdownReader wordReader = new WordMarkdownReader();

	/** Excelブックの読み取り。 */
	private final ExcelMarkdownReader excelReader = new ExcelMarkdownReader();

	/** PowerPointプレゼンテーションの読み取り。 */
	private final PowerPointMarkdownReader powerPointReader = new PowerPointMarkdownReader();

	/**
	 * アップロードされたOffice文書をMarkdownへ変換する。
	 *
	 * @param officeFile   アップロードされたOffice文書
	 * @param documentType 読み取るOffice形式
	 * @return Markdown本文
	 * @throws OfficeInputException      Office文書として読み込めない場合
	 * @throws OfficeProcessingException 読み取り中に想定外の失敗が起きた場合
	 */
	public String readMarkdown(MultipartFile officeFile, OfficeDocumentType documentType) {
		String fileName = StringUtils.defaultIfBlank(officeFile.getOriginalFilename(), UNKNOWN_FILE_NAME);
		try (InputStream inputStream = officeFile.getInputStream()) {
			String markdown = readByType(inputStream, documentType);
			LOGGER.info("Office文書をMarkdownへ変換しました。type={}, markdownLength={}", documentType.getKey(),
					markdown.length());
			return markdown;
		} catch (IOException | IllegalArgumentException | POIXMLException e) {
			// POIは「OOXMLではない」「壊れている」「空ファイル」をこの3系統で知らせる。
			// 旧形式(.doc/.xls/.ppt)を渡した場合もここへ来る（NotOfficeXmlFileExceptionはIllegalArgumentExceptionの派生）。
			// いずれも利用者が別のファイルを用意すれば通るため400扱いにし、500と混ぜない。
			throw new OfficeInputException(fileName, e);
		} catch (RuntimeException e) {
			throw new OfficeProcessingException("Office文書の読み取りに失敗しました。fileName=" + fileName, e);
		}
	}

	/**
	 * Office形式ごとの読み取りへ振り分ける。
	 *
	 * @param inputStream  Office文書の入力ストリーム
	 * @param documentType 読み取るOffice形式
	 * @return Markdown本文
	 * @throws IOException Office文書を読み込めない場合
	 */
	private String readByType(InputStream inputStream, OfficeDocumentType documentType) throws IOException {
		return switch (documentType) {
			case DOCX -> wordReader.read(inputStream);
			case XLSX -> excelReader.read(inputStream);
			case PPTX -> powerPointReader.read(inputStream);
		};
	}
}
