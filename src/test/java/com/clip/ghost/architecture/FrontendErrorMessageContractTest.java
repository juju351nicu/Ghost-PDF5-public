package com.clip.ghost.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * 想定外エラーの画面表示メッセージを固定するテスト。
 * <p>
 * 以前は {@code error.message} を優先していたため、{@code TypeError: Failed to fetch} のような
 * 英語の内部表現が利用者向けモーダルへそのまま出ていた。日本語のfallbackは事実上到達しなかった。
 * JavaScriptのテストランナーを持たない構成のため、その再発をソーススキャンで検知する。
 */
class FrontendErrorMessageContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * 想定外エラーのメッセージが例外の内容をそのまま画面へ出さないことを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void unexpectedErrorMessageDoesNotExposeExceptionMessage() throws IOException {
		String apiErrorUtils = read("static/js/api/api-error-utils.js");

		assertAll(
				() -> assertFalse(apiErrorUtils.contains("return error?.message"),
						"想定外エラーで例外のmessageを画面へ返さないでください。"),
				() -> assertFalse(apiErrorUtils.contains("error.message || fallbackMessage"),
						"想定外エラーで例外のmessageを画面へ返さないでください。"),
				() -> assertTrue(apiErrorUtils.contains("return fallbackMessage;"),
						"想定外エラーは日本語のfallbackメッセージを返してください。"));
	}

	/**
	 * サーバーへ到達できなかった場合に、利用者が対処できる専用メッセージを返すことを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void networkFailureHasDedicatedMessage() throws IOException {
		String apiErrorUtils = read("static/js/api/api-error-utils.js");

		assertAll(
				() -> assertTrue(apiErrorUtils.contains("サーバーに接続できません。"),
						"サーバー到達失敗の専用メッセージを用意してください。"),
				// メッセージ文言はブラウザごとに異なるため、型とパターンの両方で判定する実装を固定する。
				() -> assertTrue(apiErrorUtils.contains("error instanceof TypeError"),
						"サーバー到達失敗はTypeErrorで判定してください。"),
				() -> assertTrue(apiErrorUtils.contains("NETWORK_ERROR_MESSAGE_PATTERN"),
						"サーバー到達失敗はメッセージパターンでも絞ってください。"));
	}

	/**
	 * プロジェクト相対パスのフロントエンドresourceをUTF-8で読み込む。
	 *
	 * @param relativePath {@link #RESOURCE_ROOT} からの相対パス
	 * @return resource内容
	 * @throws IOException resourceを読み込めない場合
	 */
	private String read(String relativePath) throws IOException {
		return Files.readString(RESOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
	}
}
