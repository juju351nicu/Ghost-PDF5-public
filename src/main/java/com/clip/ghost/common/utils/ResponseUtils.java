package com.clip.ghost.common.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * PDFレスポンス用のHTTPヘッダーと {@link ResponseEntity} を作成するユーティリティクラス。
 * <p>
 * inline表示とattachmentダウンロードで必要なContent-Type / Content-Dispositionをここに集約し、
 * ControllerやServiceでHTTPヘッダー生成が散らばらないようにする。
 * <p>
 * ファイル本文は {@link Resource} として受け取り、byte配列へ読み込まない。出力サイズに比例した
 * ヒープ消費を避けるため。一時ファイルの削除タイミングはリソース側の責務とし、このクラスは知らない。
 */
public final class ResponseUtils {
	private static final MediaType APPLICATION_ZIP = MediaType.valueOf("application/zip");
	private static final MediaType TEXT_HTML_UTF8 = MediaType.valueOf("text/html;charset=UTF-8");
	private static final MediaType APPLICATION_EPUB = MediaType.valueOf("application/epub+zip");
	private static final MediaType TEXT_CSV_UTF8 = MediaType.valueOf("text/csv;charset=UTF-8");

	private ResponseUtils() {
	}

	/**
	 * PDFをブラウザ内で表示するためのレスポンスを作成する。
	 * <p>
	 * Content-Lengthはリソースのサイズから設定する。付けないとブラウザの進捗表示が消えるため、
	 * byte配列を返していた従来と同じヘッダーを維持する。
	 *
	 * @param contents PDF本文のリソース
	 * @return inline表示用PDFレスポンス
	 * @throws IllegalArgumentException contentsがnullの場合
	 */
	public static ResponseEntity<Resource> inlinePdf(Resource contents) {
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentLength(resolveContentLength(contents));
		headers.setContentDisposition(ContentDisposition.inline().build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * ZIPファイルをダウンロードするためのレスポンスを作成する。
	 *
	 * @param filename ZIPファイル名
	 * @param contents ZIP本文のリソース
	 * @return attachmentダウンロード用ZIPレスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<Resource> downloadZip(String filename, Resource contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(APPLICATION_ZIP);
		headers.setContentLength(resolveContentLength(contents));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * PDFをダウンロードするためのレスポンスを作成する。
	 * <p>
	 * 画面内で開く {@link #inlinePdf(Resource)} と違い、ファイル名を付けて保存させる用途で使う。
	 *
	 * @param filename PDFファイル名
	 * @param contents PDF本文のリソース
	 * @return attachmentダウンロード用PDFレスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<Resource> downloadPdf(String filename, Resource contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentLength(resolveContentLength(contents));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * HTMLをダウンロードするためのレスポンスを作成する。
	 * <p>
	 * inlineではなくattachmentで返す。生成したHTMLをブラウザが同一オリジンで表示すると、
	 * 変換元PDF由来の内容がこのアプリのページとして動くことになるため、常に保存させる。
	 *
	 * @param filename HTMLファイル名
	 * @param contents HTML本文のリソース
	 * @return attachmentダウンロード用HTMLレスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<Resource> downloadHtml(String filename, Resource contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(TEXT_HTML_UTF8);
		headers.setContentLength(resolveContentLength(contents));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * Office文書をダウンロードするためのレスポンスを作成する。
	 * <p>
	 * content typeは形式ごとに分けず {@code application/octet-stream} にする。ブラウザは拡張子で
	 * アプリケーションを選ぶため、OOXMLの長いMIME typeを3種類持ち回っても保存結果は変わらない。
	 *
	 * @param filename Office文書のファイル名
	 * @param contents Office文書のリソース
	 * @return attachmentダウンロード用レスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<Resource> downloadOffice(String filename, Resource contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentLength(resolveContentLength(contents));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * EPUBをダウンロードするためのレスポンスを作成する。
	 *
	 * @param filename EPUBファイル名
	 * @param contents EPUB本文のリソース
	 * @return attachmentダウンロード用EPUBレスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<Resource> downloadEpub(String filename, Resource contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(APPLICATION_EPUB);
		headers.setContentLength(resolveContentLength(contents));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * CSVをダウンロードするためのレスポンスを作成する。
	 *
	 * @param filename CSVファイル名
	 * @param contents CSV本文のリソース
	 * @return attachmentダウンロード用CSVレスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<Resource> downloadCsv(String filename, Resource contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(TEXT_CSV_UTF8);
		headers.setContentLength(resolveContentLength(contents));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * レスポンス本文が指定されていることを検証する。
	 *
	 * @param contents レスポンス本文のリソース
	 */
	private static void requireContents(Resource contents) {
		if (contents == null) {
			throw new IllegalArgumentException("contents must not be null.");
		}
	}

	/**
	 * リソースのサイズを取得する。
	 * <p>
	 * ファイルベースのリソースはサイズ取得で内容を読み込まないため、ここでヒープを消費しない。
	 *
	 * @param contents レスポンス本文のリソース
	 * @return リソースのバイト数
	 * @throws IllegalArgumentException サイズを取得できない場合
	 */
	private static long resolveContentLength(Resource contents) {
		try {
			return contents.contentLength();
		} catch (IOException e) {
			throw new IllegalArgumentException("contents size must be resolvable.", e);
		}
	}

	/**
	 * ダウンロードファイル名が指定されていることを検証する。
	 *
	 * @param filename ダウンロードファイル名
	 */
	private static void requireDownloadFileName(String filename) {
		if (StringUtils.isBlank(filename)) {
			throw new IllegalArgumentException("filename must not be blank.");
		}
	}
}
