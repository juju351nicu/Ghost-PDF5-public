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
import com.clip.ghost.imagecontent.dto.OcrWordBox;
import com.clip.ghost.imagecontent.exception.ImageProcessingException;

/**
 * {@link TesseractWordBoxExtractorImpl} の引数組み立てとTSV解析を、実プロセスを起動せずに検証するテスト。
 * <p>
 * {@link CommandRunner} をmockし、TSV出力の解析、コマンド引数の並び（{@code tsv} が最後）、異常終了の扱いを確認する。
 * 実Tesseractを使う確認は {@code @Tag("ocr")} の統合テストで行う。
 */
@ExtendWith(MockitoExtension.class)
class TesseractWordBoxExtractorImplTest {
	private static final String SAMPLE_TSV = """
			level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
			1\t1\t0\t0\t0\t0\t0\t0\t600\t160\t-1\t
			2\t1\t1\t0\t0\t0\t33\t60\t337\t40\t-1\t
			3\t1\t1\t1\t0\t0\t33\t60\t337\t40\t-1\t
			4\t1\t1\t1\t1\t0\t33\t60\t337\t40\t-1\t
			5\t1\t1\t1\t1\t1\t33\t60\t172\t40\t95.598984\tHELLO
			5\t1\t1\t1\t1\t2\t226\t61\t144\t38\t95.598984\t12345
			""";

	@Mock
	private CommandRunner commandRunner;

	@Test
	@DisplayName("機能無効時はisEnabledがfalseになる")
	void isEnabledIsFalseWhenDisabled() {
		TesseractProperties properties = createProperties();
		properties.setEnabled(false);
		TesseractWordBoxExtractorImpl extractor = new TesseractWordBoxExtractorImpl(properties, commandRunner);

		assertFalse(extractor.isEnabled());
	}

	@Test
	@DisplayName("TSVの単語行（level=5）だけを単語ボックスへ変換する")
	void extractsWordLevelRowsOnly() {
		TesseractWordBoxExtractorImpl extractor = new TesseractWordBoxExtractorImpl(createProperties(), commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(0, SAMPLE_TSV));

		List<OcrWordBox> wordBoxes = extractor.extractWordBoxes(new byte[] { 1, 2, 3 });

		assertEquals(2, wordBoxes.size());
		assertEquals(new OcrWordBox("HELLO", 33, 60, 172, 40), wordBoxes.get(0));
		assertEquals(new OcrWordBox("12345", 226, 61, 144, 38), wordBoxes.get(1));
	}

	@Test
	@DisplayName("単語が1つも認識されない場合は空リストを返す")
	void returnsEmptyListWhenNoWordsRecognized() {
		TesseractWordBoxExtractorImpl extractor = new TesseractWordBoxExtractorImpl(createProperties(), commandRunner);
		String tsvWithoutWords = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
				+ "1\t1\t0\t0\t0\t0\t0\t0\t600\t160\t-1\t\n";
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(0, tsvWithoutWords));

		List<OcrWordBox> wordBoxes = extractor.extractWordBoxes(new byte[] { 1 });

		assertTrue(wordBoxes.isEmpty());
	}

	@Test
	@DisplayName("設定値からコマンド引数を組み立て、tsvを最後の引数にする")
	void buildsCommandWithTsvAsLastArgument() {
		TesseractWordBoxExtractorImpl extractor = new TesseractWordBoxExtractorImpl(createProperties(), commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(0, SAMPLE_TSV));

		extractor.extractWordBoxes(new byte[] { 1 });

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
		// Tesseractの仕様上、出力設定ファイル（tsv）は他のオプションより後ろの最後の引数にする必要がある。
		assertEquals("tsv", command.get(command.size() - 1));
	}

	@Test
	@DisplayName("異常終了時は例外にする")
	void throwsWhenExitCodeIsNonZero() {
		TesseractWordBoxExtractorImpl extractor = new TesseractWordBoxExtractorImpl(createProperties(), commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(1, ""));

		assertThrows(ImageProcessingException.class, () -> extractor.extractWordBoxes(new byte[] { 1 }));
	}

	@Test
	@DisplayName("空応答時は例外にする")
	void throwsWhenOutputIsBlank() {
		TesseractWordBoxExtractorImpl extractor = new TesseractWordBoxExtractorImpl(createProperties(), commandRunner);
		when(commandRunner.run(anyList(), eq(60L))).thenReturn(new CommandResult(0, "   \n  "));

		assertThrows(ImageProcessingException.class, () -> extractor.extractWordBoxes(new byte[] { 1 }));
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
		properties.setTimeoutSeconds(60);
		return properties;
	}
}
