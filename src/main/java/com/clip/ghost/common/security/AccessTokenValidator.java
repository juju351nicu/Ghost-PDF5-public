package com.clip.ghost.common.security;

import org.apache.commons.lang3.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

/**
 * 画面表示時に発行した一時アクセストークンを検証するコンポーネント。
 * <p>
 * PDF APIとMarkdown APIで同じsession tokenを使うため、Controllerごとに検証条件がずれないようにする。
 */
@Component
public class AccessTokenValidator {
	public static final String SESSION_TOKEN_ATTRIBUTE = "token";

	/**
	 * リクエストヘッダーとセッションのアクセストークンが一致することを検証する。
	 *
	 * @param accessToken リクエストヘッダーのアクセストークン
	 * @param session     トークンを保持しているHTTPセッション
	 */
	public void validate(String accessToken, HttpSession session) {
		String sessionToken = (String) session.getAttribute(SESSION_TOKEN_ATTRIBUTE);
		if (!Strings.CS.equals(accessToken, sessionToken)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid access token.");
		}
	}
}
