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
