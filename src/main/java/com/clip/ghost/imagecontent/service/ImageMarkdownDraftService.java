package com.clip.ghost.imagecontent.service;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftRequest;
import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftResponse;
import com.clip.ghost.imagecontent.exception.ImageInputException;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;
import com.clip.ghost.imagecontent.logic.ImageToMarkdownConverter;

import lombok.RequiredArgsConstructor;

/**
 * 画像からMarkdown下書きを生成するサービス。
 * <p>
 * 変換自体は {@link ImageToMarkdownConverter} へ委譲し、このクラスは有効性確認、画像形式の検証、
 * 抽出テキストの正規化、レスポンスDTOの組み立てを担当する。外部AIの実装詳細には依存しない。
 */
@Service
@RequiredArgsConstructor
public class ImageMarkdownDraftService {
	private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("image/png", "image/jpeg", "image/jpg", "image/gif",
			"image/webp");

	private final ImageToMarkdownConverter converter;

	/**
	 * アップロードされた画像からMarkdown下書きを生成する。
	 *
	 * @param form 生成元画像を含むフォーム
	 * @return ファイル情報とMarkdown下書きを含むレスポンス
	 * @throws OcrUnavailableException 機能が無効、またはAPIキー未設定の場合
	 * @throws ImageInputException     対応していない画像形式の場合
	 * @throws ImageProcessingException 画像読み込みまたは変換に失敗した場合
	 */
	public ResponseEntity<ImageMarkdownDraftResponse> generateMarkdownDraft(ImageMarkdownDraftRequest form) {
		if (!converter.isEnabled()) {
			throw new OcrUnavailableException("画像Markdown下書き機能は無効です。");
		}
		MultipartFile imageFile = form.getImageFile();
		validateImageContentType(imageFile);
		String markdown = normalizeMarkdown(converter.convert(readBytes(imageFile), imageFile.getContentType()));
		return ResponseEntity.ok(buildResponse(imageFile, markdown));
	}

	/**
	 * アップロード画像のMIMEタイプが対応形式か検証する。
	 *
	 * @param imageFile アップロード画像
	 * @throws ImageInputException 対応していない形式の場合
	 */
	private void validateImageContentType(MultipartFile imageFile) {
		String mediaType = StringUtils.lowerCase(StringUtils.defaultString(imageFile.getContentType()));
		if (!SUPPORTED_MEDIA_TYPES.contains(mediaType)) {
			throw new ImageInputException("対応していない画像形式です。PNG / JPEG / GIF / WEBPを指定してください。");
		}
	}

	/**
	 * アップロード画像のバイト列を読み込む。
	 *
	 * @param imageFile アップロード画像
	 * @return 画像バイト列
	 * @throws ImageProcessingException 読み込みに失敗した場合
	 */
	private byte[] readBytes(MultipartFile imageFile) {
		try {
			return imageFile.getBytes();
		} catch (IOException e) {
			throw new ImageProcessingException("アップロード画像の読み込みに失敗しました。", e);
		}
	}

	/**
	 * 変換結果の改行を正規化する。CRLFとCRをLFへ統一し、末尾の空白文字を除去する。
	 *
	 * @param markdown 変換結果のMarkdown
	 * @return 正規化済みMarkdown
	 */
	private String normalizeMarkdown(String markdown) {
		return markdown.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
	}

	/**
	 * アップロード情報とMarkdownからレスポンスDTOを生成する。
	 *
	 * @param imageFile アップロード画像
	 * @param markdown  正規化済みMarkdown
	 * @return レスポンスDTO
	 */
	private ImageMarkdownDraftResponse buildResponse(MultipartFile imageFile, String markdown) {
		ImageMarkdownDraftResponse response = new ImageMarkdownDraftResponse();
		response.setFileName(imageFile.getOriginalFilename());
		response.setFileSize(imageFile.getSize());
		response.setMarkdown(markdown);
		return response;
	}
}
