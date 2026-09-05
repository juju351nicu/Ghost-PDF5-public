package com.clip.ghost.common.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CheckNumericListValidator} の単体テスト。
 */
class CheckNumericListValidatorTest {

	private final CheckNumericListValidator validator = new CheckNumericListValidator();

	@Test
	@DisplayName("削除ページ未指定_nullと空リストは正常扱い")
	void isValidAcceptsNullAndEmptyList() {
		assertTrue(validator.isValid(null, null));
		assertTrue(validator.isValid(List.of(), null));
	}

	@Test
	@DisplayName("削除ページ番号_1以上は正常扱い")
	void isValidAcceptsPositivePageNumbers() {
		assertTrue(validator.isValid(List.of(1, 2, 10), null));
	}

	@Test
	@DisplayName("削除ページ番号_0は1始まり仕様に合わないため不正")
	void isValidRejectsZero() {
		assertFalse(validator.isValid(List.of(0), null));
	}

	@Test
	@DisplayName("削除ページ番号_負数は不正")
	void isValidRejectsNegativeNumber() {
		assertFalse(validator.isValid(List.of(-1), null));
	}

	@Test
	@DisplayName("削除ページ番号_複数要素の一部に不正値がある場合は不正")
	void isValidRejectsListContainingInvalidNumber() {
		assertFalse(validator.isValid(List.of(1, 2, 0, 3), null));
	}

	@Test
	@DisplayName("削除ページ番号_要素nullは不正")
	void isValidRejectsNullElement() {
		assertFalse(validator.isValid(Arrays.asList(1, null), null));
	}
}
