package com.clip.ghost.imagecontent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftRequest;
import com.clip.ghost.imagecontent.dto.ImageMarkdownDraftResponse;
import com.clip.ghost.imagecontent.exception.ImageInputException;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;
import com.clip.ghost.imagecontent.exception.OcrUnavailableException;
import com.clip.ghost.imagecontent.logic.ImageConverterResolver;
import com.clip.ghost.imagecontent.logic.ImageToMarkdownConverter;

/**
 * {@link ImageMarkdownDraftService} の有効性確認、画像形式検証、正規化、DTO組み立てを検証するテスト。
 * <p>
 * provider選択は {@link ImageConverterResolver} をmockし、選択済み変換器の挙動だけを扱う。
 */
@ExtendWith(MockitoExtension.class)
class ImageMarkdownDraftServiceTest {

	@InjectMocks
	private ImageMarkdownDraftService service;

	@Mock
	private ImageConverterResolver converterResolver;

	@Mock
	private ImageToMarkdownConverter converter;

	@Test
	@DisplayName("有効時は変換結果を正規化してレスポンスへ組み立てる")
	void generateMarkdownDraftNormalizesConverterResultWhenEnabled() {
		MockMultipartFile imageFile = createImageFile("shot.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		doReturn("first\r\nline  \r\n").when(converter).convert(any(byte[].class), eq(MediaType.IMAGE_PNG_VALUE));

		ResponseEntity<ImageMarkdownDraftResponse> result = service.generateMarkdownDraft(createRequest(imageFile));

		ImageMarkdownDraftResponse response = result.getBody();
		assertNotNull(response);
		assertEquals(HttpStatus.OK, result.getStatusCode());
		assertEquals("shot.png", response.getFileName());
		assertEquals(imageFile.getSize(), response.getFileSize());
		assertEquals("first\nline", response.getMarkdown());
	}

	@Test
	@DisplayName("選択された変換器が無効時は503相当の例外を投げ、変換を呼ばない")
	void generateMarkdownDraftThrowsWhenDisabled() {
		MockMultipartFile imageFile = createImageFile("shot.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1 });
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(false);

		assertThrows(OcrUnavailableException.class, () -> service.generateMarkdownDraft(createRequest(imageFile)));

		verify(converter, never()).convert(any(byte[].class), any());
	}

	@Test
	@DisplayName("対応していない画像形式は400相当の例外を投げ、変換を呼ばない")
	void generateMarkdownDraftRejectsUnsupportedMediaType() {
		MockMultipartFile imageFile = createImageFile("note.txt", MediaType.TEXT_PLAIN_VALUE, new byte[] { 1 });
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);

		assertThrows(ImageInputException.class, () -> service.generateMarkdownDraft(createRequest(imageFile)));

		verify(converter, never()).convert(any(byte[].class), any());
	}

	@Test
	@DisplayName("変換失敗の例外はそのまま伝播する")
	void generateMarkdownDraftPropagatesConversionFailure() {
		MockMultipartFile imageFile = createImageFile("shot.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1 });
		when(converterResolver.resolve()).thenReturn(converter);
		when(converter.isEnabled()).thenReturn(true);
		doThrow(new ImageProcessingException("失敗")).when(converter).convert(any(byte[].class), any());

		assertThrows(ImageProcessingException.class, () -> service.generateMarkdownDraft(createRequest(imageFile)));
	}

	/**
	 * テスト用の画像アップロードファイルを生成する。
	 *
	 * @param fileName    ファイル名
	 * @param contentType MIMEタイプ
	 * @param contents    ファイル内容
	 * @return 画像multipartファイル
	 */
	private MockMultipartFile createImageFile(String fileName, String contentType, byte[] contents) {
		return new MockMultipartFile("imageFile", fileName, contentType, contents);
	}

	/**
	 * テスト用の画像Markdown下書きリクエストを生成する。
	 *
	 * @param imageFile 生成元画像
	 * @return 画像Markdown下書きリクエスト
	 */
	private ImageMarkdownDraftRequest createRequest(MockMultipartFile imageFile) {
		ImageMarkdownDraftRequest request = new ImageMarkdownDraftRequest();
		request.setImageFile(imageFile);
		return request;
	}
}
