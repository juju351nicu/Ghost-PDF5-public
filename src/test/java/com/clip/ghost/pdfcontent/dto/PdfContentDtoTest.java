package com.clip.ghost.pdfcontent.dto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockMultipartFile;

import com.clip.ghost.pdfcontent.constant.PdfConstants;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PDF機能で利用するリクエスト/レスポンスDTOの外部仕様を固定するテスト。
 * <p>
 * multipartフォーム名、JSON名、入力validationはフロントエンドや将来のRest/OpenAPI化に影響するため、
 * 本番ロジックではなくDTO単位で軽く検証する。
 */
class PdfContentDtoTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	@DisplayName("編集元PDFリクエストはoriginalFile未指定をvalidationエラーにする")
	void originalPdfRequestRequiresOriginalFile() {
		OriginalPdfRequest request = new OriginalPdfRequest();

		Set<ConstraintViolation<OriginalPdfRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		ConstraintViolation<OriginalPdfRequest> violation = violations.iterator().next();
		assertAll(() -> assertEquals("originalFile", violation.getPropertyPath().toString()),
				() -> assertEquals("ファイルを入れてください。", violation.getMessage()));
	}

	@Test
	@DisplayName("差し込みPDFリクエストはページ番号と差し込み方法の範囲をvalidationする")
	void insertPdfRequestValidatesPageAndOptionRange() {
		InsertPdfRequest request = new InsertPdfRequest();
		request.setInsertPage(PdfConstants.START_PAGE - 1);
		request.setInsertOption(PdfConstants.OPTION_LAST_INSERT + 1);

		Set<ConstraintViolation<InsertPdfRequest>> violations = validate(request);

		assertEquals(2, violations.size());
		assertTrue(hasViolation(violations, "insertPage", "1以上の半角数字で入力してください"));
		assertTrue(hasViolation(violations, "insertOption", "1から3までの値を入れてください"));
	}

	@Test
	@DisplayName("PDF抽出リクエストは編集元PDFと抽出ページを必須にする")
	void extractPdfRequestRequiresOriginalFileAndExtractPages() {
		ExtractPdfRequest request = new ExtractPdfRequest();

		Set<ConstraintViolation<ExtractPdfRequest>> violations = validate(request);

		assertEquals(2, violations.size());
		assertTrue(hasViolation(violations, "originalFile", "ファイルを入れてください。"));
		assertTrue(hasViolation(violations, "extractPages", "抽出ページを入れてください。"));
	}

	@Test
	@DisplayName("PDF結合リクエストは結合対象PDFを必須にする")
	void mergePdfRequestRequiresMergeFiles() {
		MergePdfRequest request = new MergePdfRequest();

		Set<ConstraintViolation<MergePdfRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "mergeFiles", "結合するファイルを入れてください。"));
	}

	@Test
	@DisplayName("PDF分割リクエストは分割対象PDFを必須にする")
	void splitPdfRequestRequiresOriginalFile() {
		SplitPdfRequest request = new SplitPdfRequest();

		Set<ConstraintViolation<SplitPdfRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "originalFile", "ファイルを入れてください。"));
	}

	@Test
	@DisplayName("PDF分割リクエストは分割範囲未指定を1ページずつ分割として許可する")
	void splitPdfRequestAllowsMissingSplitRanges() {
		SplitPdfRequest request = new SplitPdfRequest();
		request.setOriginalFile(new MockMultipartFile("originalFile", new byte[] { 1 }));

		assertTrue(validate(request).isEmpty());
	}

	@Test
	@DisplayName("PDF分割リクエストは分割範囲の形式不正をvalidationエラーにする")
	void splitPdfRequestValidatesSplitRangeFormat() {
		SplitPdfRequest request = new SplitPdfRequest();
		request.setOriginalFile(new MockMultipartFile("originalFile", new byte[] { 1 }));
		request.setSplitRanges(List.of("1-", "abc"));

		Set<ConstraintViolation<SplitPdfRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "splitRanges", "分割範囲は「1-5」「7」の形式で入力してください。"));
	}

	@Test
	@DisplayName("PDF分割リクエストは分割範囲の重複をvalidationエラーにする")
	void splitPdfRequestRejectsOverlappingSplitRanges() {
		SplitPdfRequest request = new SplitPdfRequest();
		request.setOriginalFile(new MockMultipartFile("originalFile", new byte[] { 1 }));
		request.setSplitRanges(List.of("1-5", "3-8"));

		Set<ConstraintViolation<SplitPdfRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "splitRanges", "分割範囲が重複しています。同じページを複数の範囲へ含めないでください。"));
	}

	@Test
	@DisplayName("Markdown下書きリクエストは生成元PDFを必須にする")
	void pdfMarkdownDraftRequestRequiresOriginalFile() {
		PdfMarkdownDraftRequest request = new PdfMarkdownDraftRequest();

		Set<ConstraintViolation<PdfMarkdownDraftRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "originalFile", "ファイルを入れてください。"));
	}

	@Test
	@DisplayName("PDFレスポンスは既存互換のmessageList名でJSON変換する")
	void ghostPdfResponseUsesLegacyMessageListJsonProperty() throws Exception {
		GhostPdfResponse response = new GhostPdfResponse();
		response.setTotalPages(3);
		response.setContents("base64-content");
		response.setMessagesList(List.of("PDFを読み込みました。"));

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals(3, json.get("totalPages").asInt()),
				() -> assertEquals("base64-content", json.get("contents").asString()),
				() -> assertEquals("PDFを読み込みました。", json.get("messageList").get(0).asString()),
				() -> assertFalse(json.has("messagesList")));
	}

	@Test
	@DisplayName("PDFメタデータレスポンスはAPI仕様のJSON項目名で変換する")
	void pdfMetadataResponseUsesApiJsonPropertyNames() throws Exception {
		PdfMetadataResponse response = new PdfMetadataResponse();
		response.setFileName("sample.pdf");
		response.setFileSize(123L);
		response.setPageCount(2);
		response.setEncrypted(false);

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals("sample.pdf", json.get("fileName").asString()),
				() -> assertEquals(123L, json.get("fileSize").asLong()),
				() -> assertEquals(2, json.get("pageCount").asInt()),
				() -> assertFalse(json.get("encrypted").asBoolean()));
	}

	@Test
	@DisplayName("PDFテキストレスポンスはAPI仕様のJSON項目名で変換する")
	void pdfTextResponseUsesApiJsonPropertyNames() throws Exception {
		PdfTextResponse response = new PdfTextResponse();
		response.setFileName("sample.pdf");
		response.setFileSize(123L);
		response.setPageCount(2);
		response.setText("PDF text");

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals("sample.pdf", json.get("fileName").asString()),
				() -> assertEquals(123L, json.get("fileSize").asLong()),
				() -> assertEquals(2, json.get("pageCount").asInt()),
				() -> assertEquals("PDF text", json.get("text").asString()));
	}

	@Test
	@DisplayName("Markdown下書きレスポンスはページ順とAPI仕様のJSON項目名を維持する")
	void pdfMarkdownDraftResponseUsesApiJsonPropertyNamesAndKeepsPageOrder() throws Exception {
		PdfMarkdownDraftPageResponse firstPage = new PdfMarkdownDraftPageResponse();
		firstPage.setPageNumber(1);
		firstPage.setText("first page");
		PdfMarkdownDraftPageResponse secondPage = new PdfMarkdownDraftPageResponse();
		secondPage.setPageNumber(2);
		secondPage.setText("");

		PdfMarkdownDraftResponse response = new PdfMarkdownDraftResponse();
		response.setFileName("sample.pdf");
		response.setFileSize(123L);
		response.setPageCount(2);
		response.setMarkdown("## Page 1\n\nfirst page\n\n## Page 2");
		response.setPages(List.of(firstPage, secondPage));

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals("sample.pdf", json.get("fileName").asString()),
				() -> assertEquals(123L, json.get("fileSize").asLong()),
				() -> assertEquals(2, json.get("pageCount").asInt()),
				() -> assertEquals("## Page 1\n\nfirst page\n\n## Page 2", json.get("markdown").asString()),
				() -> assertEquals(2, json.get("pages").size()),
				() -> assertEquals(1, json.get("pages").get(0).get("pageNumber").asInt()),
				() -> assertEquals("first page", json.get("pages").get(0).get("text").asString()),
				() -> assertEquals(2, json.get("pages").get(1).get("pageNumber").asInt()),
				() -> assertEquals("", json.get("pages").get(1).get("text").asString()));
	}

	@Test
	@DisplayName("PDFリクエストDTOは既存multipartフォーム名を維持する")
	void pdfRequestDtosKeepLegacyJsonPropertyNames() throws Exception {
		assertAll(() -> assertJsonProperty(OriginalPdfRequest.class, "token", "token"),
				() -> assertJsonProperty(OriginalPdfRequest.class, "originalFile", "originalFile"),
				() -> assertJsonProperty(OriginalPdfRequest.class, "originalDeletePages", "originalDeletePages"),
				() -> assertJsonProperty(OriginalPdfRequest.class, "insertPdfForm", "insertPdfForm"),
				() -> assertJsonProperty(ExtractPdfRequest.class, "originalFile", "originalFile"),
				() -> assertJsonProperty(ExtractPdfRequest.class, "extractPages", "extractPages"),
				() -> assertJsonProperty(MergePdfRequest.class, "mergeFiles", "mergeFiles"),
				() -> assertJsonProperty(SplitPdfRequest.class, "originalFile", "originalFile"),
				() -> assertJsonProperty(PdfMarkdownDraftRequest.class, "originalFile", "originalFile"),
				() -> assertJsonProperty(InsertPdfRequest.class, "insertFile", "insertFile"),
				() -> assertJsonProperty(InsertPdfRequest.class, "insertPage", "insertPage"),
				() -> assertJsonProperty(InsertPdfRequest.class, "insertOption", "insertOption"));
	}

	/**
	 * Jakarta Validationを使ってDTO単位の制約違反を取得する。
	 *
	 * @param target validation対象DTO
	 * @return 制約違反一覧
	 */
	private <T> Set<ConstraintViolation<T>> validate(T target) {
		try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = validatorFactory.getValidator();
			return validator.validate(target);
		}
	}

	/**
	 * 指定したフィールドとメッセージのvalidationエラーが含まれるか判定する。
	 *
	 * @param violations validationエラー一覧
	 * @param fieldName  フィールド名
	 * @param message    validationメッセージ
	 * @return 指定したvalidationエラーが存在する場合はtrue
	 */
	private <T> boolean hasViolation(Set<ConstraintViolation<T>> violations, String fieldName, String message) {
		return violations.stream().anyMatch(violation -> fieldName.equals(violation.getPropertyPath().toString())
				&& message.equals(violation.getMessage()));
	}

	/**
	 * フィールドの {@link JsonProperty} 名が既存フォーム項目名と一致することを確認する。
	 *
	 * @param dtoClass             DTOクラス
	 * @param fieldName            フィールド名
	 * @param expectedPropertyName 期待するJSON/フォーム項目名
	 * @throws NoSuchFieldException フィールドが存在しない場合
	 */
	private void assertJsonProperty(Class<?> dtoClass, String fieldName, String expectedPropertyName)
			throws NoSuchFieldException {
		Field field = dtoClass.getDeclaredField(fieldName);
		JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);

		assertEquals(expectedPropertyName, jsonProperty.value());
	}
}
