package com.clip.ghost.pdfcontent.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * サンプル画像・サンプルPDFの表示確認用ページを提供するコントローラー。
 * <p>
 * 本番のPDF編集機能とは独立した確認用画面であり、URLとview名は既存互換のため維持する。
 */
@Hidden
@Controller
public class SampleController {
	private static final Logger LOGGER = LoggerFactory.getLogger(SampleController.class);
	private static final String SAMPLE_VIEW_NAME = "sample";
	private static final String SAMPLE_COOKIE_NAME = "sampleKey";
	private static final String SAMPLE_COOKIE_VALUE = "sampleValue";
	private static final int SAMPLE_COOKIE_MAX_AGE_SECONDS = 265 * 24 * 60 * 60;
	private static final String SAMPLE_PNG_RESOURCE_PATH = "static/img/sample.png";
	private static final String SAMPLE_PDF_RESOURCE_PATH = "static/img/sample.pdf";
	private static final String BASE64_PNG_PREFIX = "data:image/png;base64,";
	private static final String BASE64_PDF_PREFIX = "data:application/pdf;base64,";

	/**
	 * サンプルのPNGとPDFをbase64に変換して画面に表示する。
	 *
	 * @param model    画面表示用のmodel
	 * @param response サンプルCookieを設定するレスポンス
	 * @return sample.htmlのview名
	 */
	@GetMapping("/getSample")
	public String showSamplePage(Model model, HttpServletResponse response) {
		addSampleCookie(response);
		model.addAttribute("base64Png", readBase64Resource(SAMPLE_PNG_RESOURCE_PATH, BASE64_PNG_PREFIX));
		model.addAttribute("base64Pdf", readBase64Resource(SAMPLE_PDF_RESOURCE_PATH, BASE64_PDF_PREFIX));
		return SAMPLE_VIEW_NAME;
	}

	/**
	 * サンプルページ用Cookieを設定する。
	 *
	 * @param response Cookieを追加するレスポンス
	 */
	private void addSampleCookie(HttpServletResponse response) {
		Cookie cookie = new Cookie(SAMPLE_COOKIE_NAME, SAMPLE_COOKIE_VALUE);
		cookie.setMaxAge(SAMPLE_COOKIE_MAX_AGE_SECONDS);
		cookie.setPath("/");
		cookie.setSecure(false);
		response.addCookie(cookie);
	}

	/**
	 * classpath上のサンプルファイルをbase64 data URLに変換する。
	 *
	 * @param resourcePath classpath上のリソースパス
	 * @param base64Prefix data URL prefix
	 * @return base64 data URL
	 */
	private String readBase64Resource(String resourcePath, String base64Prefix) {
		ClassPathResource resource = new ClassPathResource(resourcePath);
		try (InputStream inputStream = resource.getInputStream()) {
			return base64Prefix + Base64.getEncoder().encodeToString(inputStream.readAllBytes());
		} catch (IOException e) {
			LOGGER.error("サンプルファイルの読み込みに失敗しました。resourcePath={}", resourcePath);
			LOGGER.debug("サンプルファイル読み込み失敗の詳細です。", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "サンプルファイルの読み込みに失敗しました。", e);
		}
	}
}
