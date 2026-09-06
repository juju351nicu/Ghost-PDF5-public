package com.clip.ghost.imagecontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.clip.ghost.imagecontent.config.TesseractProperties;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;

/**
 * {@link TesseractImageToMarkdownConverter} の引数組み立てと結果処理を、実プロセスを起動せずに検証するテスト。
 * <p>
 * {@link CommandRunner} をmockし、コマンド引数、正常出力、異常終了、空応答の扱いを確認する。
 * 実Tesseractを使う確認は {@code @Tag("ocr")} の統合テストで行う。
 */
@ExtendWith(MockitoExtension.class)
class TesseractImageToMarkdownConverterTest {

	@Mock
	private CommandRunner commandRunner;

	@Test
	@DisplayName("機能無効時はisEnabledがfalseになり、providerはtesseract")
	void isEnabledIsFalseWhenDisabled() {
		TesseractProperties properties = createProperties();
		properties.setEnabled(false);
		TesseractImageToMarkdownConverter converter = new TesseractImageToMarkdownConverter(properties, commandRunner);

		assertFalse(converter.isEnabled());
		assertEquals("tesseract", converter.provider());
	}

	@Test
	@DisplayName("設定値からコマンド引数を組み立て、標準出力を返す")
	void buildsCommandFromPropertiesAndReturnsOutput() {
		TesseractProperties properties = createProperties();
		TesseractImageToMarkdownConverter converter = new TesseractImageToMarkdownConverter(properties, commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(0, "OCR結果"));

		String result = converter.convert(new byte[] { 1, 2, 3 }, "image/png");

		assertEquals("OCR結果", result);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
		verify(commandRunner).run(commandCaptor.capture(), eq(60L));
		List<String> command = commandCaptor.getValue();
		assertEquals("tesseract", command.get(0));
		assertTrue(command.contains("stdout"));
		assertTrue(command.contains("-l"));
		assertTrue(command.contains("jpn+eng"));
		assertTrue(command.contains("--psm"));
		assertTrue(command.contains("6"));
		assertTrue(command.contains("-c"));
		assertTrue(command.contains("preserve_interword_spaces=1"));
	}

	@Test
	@DisplayName("空白保持をオフにすると preserve_interword_spaces を付けない")
	void omitsPreserveSpacesWhenDisabled() {
		TesseractProperties properties = createProperties();
		properties.setPreserveInterwordSpaces(false);
		TesseractImageToMarkdownConverter converter = new TesseractImageToMarkdownConverter(properties, commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(0, "text"));

		converter.convert(new byte[] { 1 }, "image/png");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
		verify(commandRunner).run(commandCaptor.capture(), eq(60L));
		assertFalse(commandCaptor.getValue().contains("preserve_interword_spaces=1"));
	}

	@Test
	@DisplayName("異常終了時は例外にする")
	void throwsWhenExitCodeIsNonZero() {
		TesseractProperties properties = createProperties();
		TesseractImageToMarkdownConverter converter = new TesseractImageToMarkdownConverter(properties, commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(1, ""));

		assertThrows(ImageProcessingException.class, () -> converter.convert(new byte[] { 1 }, "image/png"));
	}

	@Test
	@DisplayName("空応答時は例外にする")
	void throwsWhenOutputIsBlank() {
		TesseractProperties properties = createProperties();
		TesseractImageToMarkdownConverter converter = new TesseractImageToMarkdownConverter(properties, commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(0, "   \n  "));

		assertThrows(ImageProcessingException.class, () -> converter.convert(new byte[] { 1 }, "image/png"));
	}

	/**
	 * 既定に近いテスト用のTesseract設定を生成する。
	 *
	 * @return Tesseract設定
	 */
	private TesseractProperties createProperties() {
		TesseractProperties properties = new TesseractProperties();
		properties.setEnabled(true);
		properties.setCommand("tesseract");
		properties.setLanguages("jpn+eng");
		properties.setPsm(6);
		properties.setPreserveInterwordSpaces(true);
		properties.setTimeoutSeconds(60);
		return properties;
	}
}
