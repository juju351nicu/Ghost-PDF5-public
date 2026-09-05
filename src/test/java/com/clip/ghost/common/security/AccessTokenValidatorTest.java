package com.clip.ghost.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link AccessTokenValidator} の単体テスト。
 */
class AccessTokenValidatorTest {
	private static final String ACCESS_TOKEN = "test-token";
	private static final String INVALID_ACCESS_TOKEN = "invalid-token";

	private final AccessTokenValidator validator = new AccessTokenValidator();

	@Test
	@DisplayName("session tokenとaccess-tokenが一致する場合は例外にしない")
	void validateAcceptsMatchingToken() {
		MockHttpSession session = createSession(ACCESS_TOKEN);

		assertDoesNotThrow(() -> validator.validate(ACCESS_TOKEN, session));
	}

	@Test
	@DisplayName("session tokenとaccess-tokenが一致しない場合は403にする")
	void validateThrowsForbiddenWhenTokenDoesNotMatch() {
		MockHttpSession session = createSession(ACCESS_TOKEN);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> validator.validate(INVALID_ACCESS_TOKEN, session));
		ResponseStatusException caseMismatchException = assertThrows(ResponseStatusException.class,
				() -> validator.validate(ACCESS_TOKEN.toUpperCase(), session));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, caseMismatchException.getStatusCode());
	}

	@Test
	@DisplayName("session token未設定の場合は403にする")
	void validateThrowsForbiddenWhenSessionTokenIsMissing() {
		MockHttpSession session = new MockHttpSession();

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> validator.validate(ACCESS_TOKEN, session));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}

	private MockHttpSession createSession(String token) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AccessTokenValidator.SESSION_TOKEN_ATTRIBUTE, token);
		return session;
	}
}
