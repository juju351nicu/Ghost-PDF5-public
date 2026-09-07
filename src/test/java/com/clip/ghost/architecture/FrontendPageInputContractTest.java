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
 * ページ指定入力の表記ゆれ吸収を固定するテスト。
 * <p>
 * 空白を除去する正規表現のエスケープが誤っていたため {@code "2, 3-5"} が扱えず、
 * さらに {@code toHalfWidth} の変換範囲が全角英数字だけだったため、当時のplaceholderが案内していた
 * 全角カンマ {@code "2，3-5"} も「整数の値を入力してください。」になっていた。
 * JavaScriptのテストランナーを持たない構成のため、その再発をソーススキャンで検知する。
 */
class FrontendPageInputContractTest {
	private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");

	/**
	 * 空白除去が実際に空白へマッチし、全体を置換することを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void whitespaceRemovalMatchesWhitespaceGlobally() throws IOException {
		String util = read("static/js/util.js");

		assertAll(
				() -> assertTrue(util.contains("target.replace(/\\s+/g, \"\")"),
						"空白除去は空白文字にマッチする正規表現とgフラグで行ってください。"),
				// 旧実装の `/\\s*|\t|\r|\n/` は、正規表現リテラル中の `\\` がリテラルのバックスラッシュを
				// 意味するため「バックスラッシュ + 0個以上の s」を探していた。空白とは無関係で、
				// gフラグを足しても直らない。エスケープの形そのものを検知する。
				() -> assertFalse(util.contains("replace(/\\\\s*"), "空白除去でバックスラッシュ自体を探さないでください。"));
	}

	/**
	 * ページ指定入力の判定と変換が、同じ正規化を通ることを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void pageInputValidationAndParsingShareNormalization() throws IOException {
		String validator = read("static/js/validation/page-number-validator.js");

		assertAll(
				() -> assertTrue(validator.contains("const normalizePagesText"),
						"ページ指定入力の正規化を1つの関数へ集約してください。"),
				// 判定と変換で処理が分かれると「入力欄では正しいのに送信でエラー」というズレが起きる。
				() -> assertTrue(validator.contains("const pageItems = normalizePagesText(pagesText).split("),
						"削除ページ入力の判定は正規化を通してください。"),
				() -> assertTrue(
						validator.contains("for (const pageItem of normalizePagesText(pagesText).split("),
						"削除ページ入力の変換は正規化を通してください。"),
				() -> assertTrue(validator.contains("const rangeItems = normalizePagesText(rangesText).split("),
						"分割範囲入力も同じ正規化を通してください。"));
	}

	/**
	 * 日本語入力で出やすい全角記号を、半角の区切りとして扱うことを確認する。
	 *
	 * @throws IOException フロントエンドresourceを読み込めない場合
	 */
	@Test
	void fullWidthDelimitersAreAcceptedAsHalfWidth() throws IOException {
		String validator = read("static/js/validation/page-number-validator.js");
		String originalPdfForm = read("static/js/components/original-pdf-form.js");

		assertAll(
				// placeholderは半角カンマの流儀にそろえる。2つの入力欄で流儀が違うと、
				// 利用者はどちらが正しい書き方か判断できない。
				() -> assertTrue(originalPdfForm.contains("ページ指定  (入力例：2, 3-5)"),
						"ページ指定のplaceholderは半角カンマで案内してください。"),
				() -> assertTrue(originalPdfForm.contains("分割範囲  (入力例：1-5, 6-12"),
						"分割範囲のplaceholderは半角カンマで案内してください。"),
				// 案内は半角にそろえるが、日本語入力で出やすい全角も受け付ける。
				() -> assertTrue(validator.contains("Util.toHalfWidth(pagesText)"),
						"全角数字は既存のtoHalfWidthで半角へそろえてください。"),
				() -> assertTrue(validator.contains("const COMMA_LIKE_PATTERN = /[，、]/g;"),
						"全角カンマと読点を半角カンマとして扱ってください。"),
				() -> assertTrue(validator.contains("const HYPHEN_LIKE_PATTERN = /[－‐‑‒–—―ー]/g;"),
						"全角ハイフンやダッシュ、長音を半角ハイフンとして扱ってください。"));
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
