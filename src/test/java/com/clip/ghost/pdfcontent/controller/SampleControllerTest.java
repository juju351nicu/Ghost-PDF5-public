package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link SampleController} のサンプル画面表示仕様を検証するテスト。
 */
class SampleControllerTest {

	private static final String REQUEST_PATH_GET_SAMPLE = "/getSample";
	private static final String SAMPLE_COOKIE_NAME = "sampleKey";
	private static final String SAMPLE_COOKIE_VALUE = "sampleValue";
	private static final String SAMPLE_COOKIE_PATH = "/";
	private static final String BASE64_PNG_PREFIX = "data:image/png;base64,";
	private static final String BASE64_PDF_PREFIX = "data:application/pdf;base64,";

	private MockMvc mockMvc;

	/**
	 * MockMvcにテスト対象のコントローラーをセットする。
	 */
	@BeforeEach
	void setup() {
		mockMvc = MockMvcBuilders.standaloneSetup(new SampleController()).build();
	}

	@Test
	@DisplayName("サンプル画面ではbase64 data URLとサンプルCookieを設定する")
	void showSamplePageSetsBase64DataUrlsAndCookie() throws Exception {
		MvcResult result = sendGetRequest(REQUEST_PATH_GET_SAMPLE);

		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
		assertEquals("sample", result.getResponse().getForwardedUrl());
		assertTrue(result.getModelAndView().getModel().get("base64Png").toString().startsWith(BASE64_PNG_PREFIX));
		assertTrue(result.getModelAndView().getModel().get("base64Pdf").toString().startsWith(BASE64_PDF_PREFIX));
		Cookie sampleCookie = result.getResponse().getCookie(SAMPLE_COOKIE_NAME);
		assertNotNull(sampleCookie);
		assertEquals(SAMPLE_COOKIE_VALUE, sampleCookie.getValue());
		assertEquals(SAMPLE_COOKIE_PATH, sampleCookie.getPath());
	}

	/**
	 * MockMVCでGETリクエストを行う。
	 *
	 * @param url URL
	 * @return リクエスト結果
	 * @throws Exception 例外
	 */
	private MvcResult sendGetRequest(String url) throws Exception {
		return mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON_VALUE)).andReturn();
	}
}
