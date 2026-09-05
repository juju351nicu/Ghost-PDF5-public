package com.clip.ghost.common.utils;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
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
 */
public final class ResponseUtils {
	private static final MediaType APPLICATION_ZIP = MediaType.valueOf("application/zip");

	private ResponseUtils() {
	}

	/**
	 * PDFをブラウザ内で表示するためのレスポンスを作成する。
	 *
	 * @param contents byte型のPDF情報
	 * @return inline表示用PDFレスポンス
	 * @throws IllegalArgumentException contentsがnullの場合
	 */
	public static ResponseEntity<byte[]> getResponseBytes(byte[] contents) {
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(ContentDisposition.inline().build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * PDFをダウンロードするためのレスポンスを作成する。
	 *
	 * @param filename ファイル名
	 * @param contents byte型のPDF情報
	 * @return attachmentダウンロード用PDFレスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<byte[]> downloadPdf(String filename, byte[] contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentLength(contents.length);
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * ZIPファイルをダウンロードするためのレスポンスを作成する。
	 *
	 * @param filename ZIPファイル名
	 * @param contents byte型のZIP情報
	 * @return attachmentダウンロード用ZIPレスポンス
	 * @throws IllegalArgumentException filenameが未指定、またはcontentsがnullの場合
	 */
	public static ResponseEntity<byte[]> downloadZip(String filename, byte[] contents) {
		requireDownloadFileName(filename);
		requireContents(contents);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(APPLICATION_ZIP);
		headers.setContentLength(contents.length);
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		return new ResponseEntity<>(contents, headers, HttpStatus.OK);
	}

	/**
	 * PDFレスポンス本文が指定されていることを検証する。
	 *
	 * @param contents PDFバイト配列
	 */
	private static void requireContents(byte[] contents) {
		if (contents == null) {
			throw new IllegalArgumentException("contents must not be null.");
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
