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
 * 成功レスポンスの通知メッセージ（{@code ApiResult} の {@code messageList}）の画面表示導線を固定するテスト。
 * <p>
 * 各api clientは以前から {@code messages} を返していたが、画面へ出す先が無く死蔵していた。
 * JavaScriptのテストランナーを持たない構成のため、api client → app state → 表示componentの
 * 接続が切れていないことをソーススキャンで検知する。
 */
class FrontendApiMessageContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * api clientが返す {@code messages} が、app stateを経て表示componentへ渡ることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void apiMessagesReachInlineDisplayComponent() throws IOException {
		String mainTemplate = read("templates/main.html");
		String apiMessageList = read("static/js/components/api-message-list.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertTrue(pdfApp.contains("apiMessages: [],"), "通知メッセージのapp stateを用意してください。"),
				() -> assertTrue(pdfApp.contains("applyApiMessages(result.messages)"),
						"api clientのmessagesをapp stateへ反映してください。"),
				() -> assertTrue(pdfApp.contains("\"api-message-list\": ApiMessageList,"),
						"通知メッセージの表示componentを登録してください。"),
				() -> assertTrue(mainTemplate.contains("<api-message-list :messages=\"apiMessages\">"),
						"通知メッセージを操作の近くへ表示してください。"),
				() -> assertTrue(apiMessageList.contains("messages: { type: Array, required: true }"),
						"表示componentは通知メッセージをpropsで受け取ってください。"));
	}

	/**
	 * 通知メッセージが次の操作へ持ち越されず、エラーと同じ導線に混ざらないことを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void apiMessagesAreClearedPerRequestAndDoNotUseErrorModal() throws IOException {
		String apiMessageList = read("static/js/components/api-message-list.js");
		String pdfApp = read("static/js/pdf/pdf-app.js");

		assertAll(
				() -> assertTrue(pdfApp.contains("this.errorMessages = [];\n      this.clearApiMessages();"),
						"通知メッセージはエラーメッセージの初期化と同じ位置で消してください。"),
				// 成功時の通知でモーダルを出すと、終わった処理のために操作が止まる。
				() -> assertFalse(apiMessageList.contains("dialog"), "成功時の通知はモーダルで表示しないでください。"),
				() -> assertFalse(apiMessageList.contains("style="), "通知メッセージの見た目はmain.cssのクラスで指定してください。"));
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
