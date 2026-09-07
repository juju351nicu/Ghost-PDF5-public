package com.clip.ghost.common.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;

/**
 * {@link CheckPageRangeListValidator} の単体テスト。
 * <p>
 * 分割範囲は利用者が直せる入力のため、不正の種類ごとにメッセージが変わることまで固定する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckPageRangeListValidatorTest {

	@Mock
	private ConstraintValidatorContext context;

	@Mock
	private ConstraintViolationBuilder violationBuilder;

	private final CheckPageRangeListValidator validator = new CheckPageRangeListValidator();

	@BeforeEach
	void setUp() {
		validator.initialize(createAnnotation(50));
		when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
	}

	@Test
	@DisplayName("分割範囲未指定_nullと空リストは1ページずつ分割として正常扱い")
	void isValidAcceptsNullAndEmptyList() {
		assertTrue(validator.isValid(null, context));
		assertTrue(validator.isValid(List.of(), context));
		assertTrue(validator.isValid(Arrays.asList("", "   "), context));
	}

	@Test
	@DisplayName("分割範囲_単一ページと範囲の混在は正常扱い")
	void isValidAcceptsSinglePageAndRange() {
		assertTrue(validator.isValid(List.of("1-5", "6", "7-12"), context));
	}

	@Test
	@DisplayName("分割範囲_開始と終了が同じ範囲は1ページ分の指定として正常扱い")
	void isValidAcceptsRangeWithSameStartAndEnd() {
		assertTrue(validator.isValid(List.of("3-3"), context));
	}

	@Test
	@DisplayName("分割範囲_形式不正は不正扱いにし、形式のメッセージを返す")
	void isValidRejectsInvalidFormat() {
		assertFalse(validator.isValid(List.of("a-b"), context));
		assertFalse(validator.isValid(List.of("1-"), context));
		assertFalse(validator.isValid(List.of("-5"), context));
		assertFalse(validator.isValid(List.of("0-3"), context));
		assertFalse(validator.isValid(List.of("1-2-3"), context));
		assertEquals("分割範囲は「1-5」「7」の形式で入力してください。", captureLastMessage());
	}

	@Test
	@DisplayName("分割範囲_開始が終了より大きい場合は不正扱い")
	void isValidRejectsReversedRange() {
		assertFalse(validator.isValid(List.of("5-1"), context));
	}

	@Test
	@DisplayName("分割範囲_重複は不正扱いにし、重複のメッセージを返す")
	void isValidRejectsOverlappingRanges() {
		// 同じページが複数ファイルへ入ると、どちらを使うべきか利用者が判断できないため禁止する。
		assertFalse(validator.isValid(List.of("1-5", "3-8"), context));
		assertEquals("分割範囲が重複しています。同じページを複数の範囲へ含めないでください。", captureLastMessage());
	}

	@Test
	@DisplayName("分割範囲_隣接するだけで重ならない範囲は正常扱い")
	void isValidAcceptsAdjacentRanges() {
		assertTrue(validator.isValid(List.of("1-5", "6-10"), context));
	}

	@Test
	@DisplayName("分割範囲_件数上限を超えた場合は不正扱いにし、件数のメッセージを返す")
	void isValidRejectsTooManyRanges() {
		validator.initialize(createAnnotation(3));

		assertFalse(validator.isValid(List.of("1", "2", "3", "4"), context));
		assertEquals("分割範囲は3件までにしてください。", captureLastMessage());
	}

	@Test
	@DisplayName("分割範囲_件数上限と同数は正常扱い")
	void isValidAcceptsRangeCountAtLimit() {
		validator.initialize(createAnnotation(3));

		assertTrue(validator.isValid(List.of("1", "2", "3"), context));
	}

	@Test
	@DisplayName("分割範囲_件数超過は形式検証より先に判定する")
	void isValidChecksRangeCountBeforeFormat() {
		validator.initialize(createAnnotation(2));

		// 大量入力では1件ずつの形式検証よりも「多すぎる」ことを伝えた方が直しやすい。
		assertFalse(validator.isValid(List.of("a", "b", "c"), context));
		assertEquals("分割範囲は2件までにしてください。", captureLastMessage());
	}

	@Test
	@DisplayName("分割範囲_既定の上限は50件")
	void defaultMaxIsFiftyRanges() {
		List<String> fiftyRanges = IntStream.rangeClosed(1, 50).mapToObj(String::valueOf).toList();
		List<String> fiftyOneRanges = IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).toList();

		assertTrue(validator.isValid(fiftyRanges, context));
		assertFalse(validator.isValid(fiftyOneRanges, context));
	}

	/**
	 * 最後に差し替えられたエラーメッセージを取得する。
	 *
	 * @return エラーメッセージ
	 */
	private String captureLastMessage() {
		ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
		verify(context, org.mockito.Mockito.atLeastOnce())
				.buildConstraintViolationWithTemplate(messageCaptor.capture());
		return messageCaptor.getValue();
	}

	/**
	 * 指定した件数上限を持つannotationを生成する。
	 *
	 * @param max 受け付ける最大件数
	 * @return テスト用annotation
	 */
	private CheckPageRangeList createAnnotation(int max) {
		return new CheckPageRangeList() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return CheckPageRangeList.class;
			}

			@Override
			public String message() {
				return "";
			}

			@Override
			public int max() {
				return max;
			}

			@Override
			public Class<?>[] groups() {
				return new Class<?>[0];
			}

			@Override
			public Class<? extends jakarta.validation.Payload>[] payload() {
				return castPayload();
			}

			@SuppressWarnings("unchecked")
			private Class<? extends jakarta.validation.Payload>[] castPayload() {
				return new Class[0];
			}
		};
	}
}
