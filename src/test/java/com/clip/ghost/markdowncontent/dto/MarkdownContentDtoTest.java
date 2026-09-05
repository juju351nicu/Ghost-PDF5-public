package com.clip.ghost.markdowncontent.dto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Markdown機能で利用するリクエスト/レスポンスDTOの外部仕様を固定するテスト。
 */
class MarkdownContentDtoTest {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Test
	@DisplayName("Markdown保存リクエストはfileNameとcontentを必須にする")
	void markdownSaveRequestRequiresFileNameAndContent() {
		MarkdownSaveRequest request = new MarkdownSaveRequest();

		Set<ConstraintViolation<MarkdownSaveRequest>> violations = validate(request);

		assertEquals(2, violations.size());
		assertTrue(hasViolation(violations, "fileName"));
		assertTrue(hasViolation(violations, "content"));
	}

	@Test
	@DisplayName("Markdown保存リクエストは空白だけのfileNameを拒否する")
	void markdownSaveRequestRejectsBlankFileName() {
		MarkdownSaveRequest request = new MarkdownSaveRequest();
		request.setFileName("   ");
		request.setContent("# Title");

		Set<ConstraintViolation<MarkdownSaveRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "fileName"));
	}

	@Test
	@DisplayName("Markdown保存リクエストはfileNameを255文字までにする")
	void markdownSaveRequestLimitsFileNameLength() {
		MarkdownSaveRequest request = new MarkdownSaveRequest();
		request.setFileName("a".repeat(256));
		request.setContent("# Title");

		Set<ConstraintViolation<MarkdownSaveRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "fileName"));
	}

	@Test
	@DisplayName("Markdown更新リクエストはcontentを必須にする")
	void markdownUpdateRequestRequiresContent() {
		MarkdownUpdateRequest request = new MarkdownUpdateRequest();

		Set<ConstraintViolation<MarkdownUpdateRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "content"));
	}

	@Test
	@DisplayName("Markdownプレビューリクエストはcontentを必須にする")
	void markdownPreviewRequestRequiresContent() {
		MarkdownPreviewRequest request = new MarkdownPreviewRequest();

		Set<ConstraintViolation<MarkdownPreviewRequest>> violations = validate(request);

		assertEquals(1, violations.size());
		assertTrue(hasViolation(violations, "content"));
	}

	@Test
	@DisplayName("Markdown保存レスポンスはAPI仕様のJSON項目名で変換する")
	void markdownFileResponseUsesApiJsonPropertyNames() throws Exception {
		MarkdownFileResponse response = new MarkdownFileResponse();
		response.setFileName("design-note.md");
		response.setByteSize(123L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:45:00");

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals("design-note.md", json.get("fileName").asString()),
				() -> assertEquals(123L, json.get("byteSize").asLong()),
				() -> assertEquals(2L, json.get("lineCount").asLong()),
				() -> assertEquals("2026-07-25T16:45:00", json.get("lastModifiedTime").asString()));
	}

	@Test
	@DisplayName("Markdown本文レスポンスはAPI仕様のJSON項目名で変換する")
	void markdownDocumentResponseUsesApiJsonPropertyNames() throws Exception {
		MarkdownDocumentResponse response = new MarkdownDocumentResponse();
		response.setFileName("design-note.md");
		response.setByteSize(123L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:45:00");
		response.setContent("# Title");

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals("design-note.md", json.get("fileName").asString()),
				() -> assertEquals(123L, json.get("byteSize").asLong()),
				() -> assertEquals(2L, json.get("lineCount").asLong()),
				() -> assertEquals("2026-07-25T16:45:00", json.get("lastModifiedTime").asString()),
				() -> assertEquals("# Title", json.get("content").asString()));
	}

	@Test
	@DisplayName("MarkdownプレビューレスポンスはAPI仕様のJSON項目名で変換する")
	void markdownPreviewResponseUsesApiJsonPropertyNames() throws Exception {
		MarkdownPreviewResponse response = new MarkdownPreviewResponse();
		response.setFileName("design-note.md");
		response.setByteSize(123L);
		response.setLineCount(2L);
		response.setLastModifiedTime("2026-07-25T16:45:00");
		response.setHtml("<h1>Title</h1>");

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals("design-note.md", json.get("fileName").asString()),
				() -> assertEquals(123L, json.get("byteSize").asLong()),
				() -> assertEquals(2L, json.get("lineCount").asLong()),
				() -> assertEquals("2026-07-25T16:45:00", json.get("lastModifiedTime").asString()),
				() -> assertEquals("<h1>Title</h1>", json.get("html").asString()));
	}

	@Test
	@DisplayName("Markdown本文プレビューレスポンスはAPI仕様のJSON項目名で変換する")
	void markdownPreviewContentResponseUsesApiJsonPropertyNames() throws Exception {
		MarkdownPreviewContentResponse response = new MarkdownPreviewContentResponse();
		response.setHtml("<h1>Title</h1>");

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertEquals("<h1>Title</h1>", json.get("html").asString());
	}

	@Test
	@DisplayName("Markdown削除レスポンスはAPI仕様のJSON項目名で変換する")
	void markdownDeleteResponseUsesApiJsonPropertyNames() throws Exception {
		MarkdownDeleteResponse response = new MarkdownDeleteResponse();
		response.setFileName("design-note.md");
		response.setDeleted(Boolean.TRUE);

		JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(response));

		assertAll(() -> assertEquals("design-note.md", json.get("fileName").asString()),
				() -> assertTrue(json.get("deleted").asBoolean()));
	}

	private <T> Set<ConstraintViolation<T>> validate(T target) {
		try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
			Validator validator = validatorFactory.getValidator();
			return validator.validate(target);
		}
	}

	private <T> boolean hasViolation(Set<ConstraintViolation<T>> violations, String fieldName) {
		return violations.stream().anyMatch(violation -> fieldName.equals(violation.getPropertyPath().toString()));
	}
}
