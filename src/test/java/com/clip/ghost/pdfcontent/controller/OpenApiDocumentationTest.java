package com.clip.ghost.pdfcontent.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import com.clip.ghost.common.security.AccessTokenValidator;
import com.clip.ghost.markdowncontent.controller.MarkdownController;
import com.clip.ghost.markdowncontent.service.MarkdownDocumentService;
import com.clip.ghost.pdfcontent.service.GhostPdfService;
import com.clip.ghost.imagecontent.controller.ImageMarkdownDraftController;
import com.clip.ghost.imagecontent.service.ImageMarkdownDraftService;
import com.clip.ghost.pdfcontent.service.PdfMarkdownDraftService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Springdocが生成するOpenAPI定義に、PDF操作APIだけが公開されることを検証するテスト。
 * <p>
 * Swagger UIの手動確認に寄せすぎるとCIで退行検知できないため、最低限の公開パスと非公開パスを {@code /v3/api-docs}
 * のJSONで確認する。
 */
@Tag("context")
@Tag("openapi")
@WebMvcTest({ GhostPdfController.class, PdfMarkdownDraftController.class, ImageMarkdownDraftController.class,
		MarkdownController.class, CsvController.class, SampleController.class })
@ImportAutoConfiguration({ SpringDocConfiguration.class, SpringDocConfigProperties.class,
		SpringDocWebMvcConfiguration.class })
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiDocumentationTest {
	private static final String OPEN_API_DOCS_PATH = "/v3/api-docs";
	private static final String PATH_SHOW_PDF = "/showPdf";
	private static final String PATH_METADATA_PDF = "/metadataPdf";
	private static final String PATH_TEXT_PDF = "/textPdf";
	private static final String PATH_MARKDOWN_DRAFT_PDF = "/markdownDraftPdf";
	private static final String PATH_MARKDOWN_DRAFT_IMAGE = "/markdownDraftImage";
	private static final String PATH_SAVE_MARKDOWN = "/saveMarkdown";
	private static final String PATH_MARKDOWN_FILES = "/markdownFiles";
	private static final String PATH_MARKDOWN_FILE = "/markdownFile";
	private static final String PATH_MARKDOWN_PREVIEW = "/markdownPreview";
	private static final String PATH_EXTRACT_PDF = "/extractPdf";
	private static final String PATH_MERGE_PDF = "/mergePdf";
	private static final String PATH_SPLIT_PDF = "/splitPdf";
	private static final String PATH_DELETE_PDF = "/deletePdf";
	private static final String PATH_INSERT_PDF = "/insertPdf";
	private static final String PATH_GET_SAMPLE = "/getSample";
	private static final String PATH_SHOW_CSV = "/showCSV";
	private static final String PATH_PRINT_CSV = "/printCSV";
	private static final String ACCESS_TOKEN_HEADER_NAME = "access-token";
	private static final String MEDIA_TYPE_APPLICATION_ZIP_VALUE = "application/zip";
	private static final String HTTP_METHOD_POST = "post";
	private static final String HTTP_METHOD_GET = "get";
	private static final String HTTP_METHOD_PUT = "put";
	private static final String HTTP_METHOD_DELETE = "delete";
	private static final String HTTP_STATUS_OK = "200";
	private static final String HTTP_STATUS_BAD_REQUEST = "400";
	private static final String HTTP_STATUS_FORBIDDEN = "403";
	private static final String HTTP_STATUS_NOT_FOUND = "404";
	private static final String HTTP_STATUS_PAYLOAD_TOO_LARGE = "413";
	private static final String HTTP_STATUS_INTERNAL_SERVER_ERROR = "500";
	private static final String HTTP_STATUS_SERVICE_UNAVAILABLE = "503";
	private static final String SCHEMA_ORIGINAL_PDF_REQUEST = "OriginalPdfRequest";
	private static final String SCHEMA_EXTRACT_PDF_REQUEST = "ExtractPdfRequest";
	private static final String SCHEMA_MERGE_PDF_REQUEST = "MergePdfRequest";
	private static final String SCHEMA_SPLIT_PDF_REQUEST = "SplitPdfRequest";
	private static final String SCHEMA_INSERT_PDF_REQUEST = "InsertPdfRequest";
	private static final String SCHEMA_PDF_METADATA_RESPONSE = "PdfMetadataResponse";
	private static final String SCHEMA_PDF_TEXT_RESPONSE = "PdfTextResponse";
	private static final String SCHEMA_PDF_MARKDOWN_DRAFT_REQUEST = "PdfMarkdownDraftRequest";
	private static final String SCHEMA_PDF_MARKDOWN_DRAFT_RESPONSE = "PdfMarkdownDraftResponse";
	private static final String SCHEMA_PDF_MARKDOWN_DRAFT_PAGE_RESPONSE = "PdfMarkdownDraftPageResponse";
	private static final String SCHEMA_IMAGE_MARKDOWN_DRAFT_REQUEST = "ImageMarkdownDraftRequest";
	private static final String SCHEMA_IMAGE_MARKDOWN_DRAFT_RESPONSE = "ImageMarkdownDraftResponse";
	private static final String SCHEMA_MARKDOWN_SAVE_REQUEST = "MarkdownSaveRequest";
	private static final String SCHEMA_MARKDOWN_UPDATE_REQUEST = "MarkdownUpdateRequest";
	private static final String SCHEMA_MARKDOWN_PREVIEW_REQUEST = "MarkdownPreviewRequest";
	private static final String SCHEMA_MARKDOWN_FILE_RESPONSE = "MarkdownFileResponse";
	private static final String SCHEMA_MARKDOWN_DOCUMENT_RESPONSE = "MarkdownDocumentResponse";
	private static final String SCHEMA_MARKDOWN_PREVIEW_RESPONSE = "MarkdownPreviewResponse";
	private static final String SCHEMA_MARKDOWN_PREVIEW_CONTENT_RESPONSE = "MarkdownPreviewContentResponse";
	private static final String SCHEMA_MARKDOWN_DELETE_RESPONSE = "MarkdownDeleteResponse";
	private static final String SCHEMA_API_MESSAGE = "ApiMessage";
	private static final String SCHEMA_API_RESULT_PREFIX = "ApiResult";
	private static final String SCHEMA_LIST_PREFIX = "List";
	private static final String SCHEMA_ERROR_RESPONSE = "ErrorResponse";
	private static final String SCHEMA_CUSTOM_FIELD_ERROR = "CustomFieldError";
	private static final int MARKDOWN_FILE_NAME_MAX_LENGTH = 255;
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GhostPdfService pdfService;

	@MockitoBean
	private PdfMarkdownDraftService pdfMarkdownDraftService;

	@MockitoBean
	private ImageMarkdownDraftService imageMarkdownDraftService;

	@MockitoBean
	private MarkdownDocumentService markdownDocumentService;

	@MockitoBean
	private AccessTokenValidator accessTokenValidator;

	/**
	 * PDF操作APIがOpenAPI定義に含まれ、画面・サンプル用Controllerが含まれないことを確認する。
	 *
	 * @throws Exception MockMvc実行時に例外が発生した場合
	 */
	@Test
	@DisplayName("OpenAPI定義にPDF操作APIだけが公開される")
	void pdfApiOnlyIsPublishedInOpenApiDocumentation() throws Exception {
		MvcResult result = mockMvc.perform(get(OPEN_API_DOCS_PATH)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
		JsonNode openApi = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());

		assertAll(() -> assertPdfPathVisibility(openApi), () -> assertPdfEndpoint(openApi, PATH_SHOW_PDF),
				() -> assertMetadataEndpoint(openApi), () -> assertTextEndpoint(openApi),
				() -> assertMarkdownDraftEndpoint(openApi),
				() -> assertMarkdownDraftImageEndpoint(openApi),
				() -> assertMarkdownEndpoint(openApi), () -> assertMarkdownFilesEndpoint(openApi),
				() -> assertMarkdownFileEndpoint(openApi), () -> assertMarkdownFileUpdateEndpoint(openApi),
				() -> assertMarkdownFileDeleteEndpoint(openApi), () -> assertMarkdownPreviewEndpoint(openApi),
				() -> assertMarkdownPreviewContentEndpoint(openApi), () -> assertPdfEndpoint(openApi, PATH_EXTRACT_PDF),
				() -> assertPdfEndpoint(openApi, PATH_MERGE_PDF), () -> assertZipEndpoint(openApi, PATH_SPLIT_PDF),
				() -> assertPdfEndpoint(openApi, PATH_DELETE_PDF), () -> assertPdfEndpoint(openApi, PATH_INSERT_PDF),
				() -> assertPdfRequestSchemas(openApi), () -> assertMetadataResponseSchema(openApi),
				() -> assertTextResponseSchema(openApi), () -> assertMarkdownDraftSchemas(openApi),
				() -> assertImageMarkdownDraftSchemas(openApi), () -> assertMarkdownSchemas(openApi),
				() -> assertErrorResponseSchemas(openApi));
	}

	/**
	 * PDF APIだけがOpenAPI pathsへ公開されていることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertPdfPathVisibility(JsonNode openApi) {
		JsonNode paths = openApi.path("paths");

		assertAll(() -> assertTrue(paths.has(PATH_SHOW_PDF), PATH_SHOW_PDF + " should be published."),
				() -> assertTrue(paths.has(PATH_METADATA_PDF), PATH_METADATA_PDF + " should be published."),
				() -> assertTrue(paths.has(PATH_TEXT_PDF), PATH_TEXT_PDF + " should be published."),
				() -> assertTrue(paths.has(PATH_MARKDOWN_DRAFT_PDF), PATH_MARKDOWN_DRAFT_PDF + " should be published."),
					() -> assertTrue(paths.has(PATH_MARKDOWN_DRAFT_IMAGE),
							PATH_MARKDOWN_DRAFT_IMAGE + " should be published."),
				() -> assertTrue(paths.has(PATH_SAVE_MARKDOWN), PATH_SAVE_MARKDOWN + " should be published."),
				() -> assertTrue(paths.has(PATH_MARKDOWN_FILES), PATH_MARKDOWN_FILES + " should be published."),
				() -> assertTrue(paths.has(PATH_MARKDOWN_FILE), PATH_MARKDOWN_FILE + " should be published."),
				() -> assertTrue(paths.has(PATH_MARKDOWN_PREVIEW), PATH_MARKDOWN_PREVIEW + " should be published."),
				() -> assertTrue(paths.has(PATH_EXTRACT_PDF), PATH_EXTRACT_PDF + " should be published."),
				() -> assertTrue(paths.has(PATH_MERGE_PDF), PATH_MERGE_PDF + " should be published."),
				() -> assertTrue(paths.has(PATH_SPLIT_PDF), PATH_SPLIT_PDF + " should be published."),
				() -> assertTrue(paths.has(PATH_DELETE_PDF), PATH_DELETE_PDF + " should be published."),
				() -> assertTrue(paths.has(PATH_INSERT_PDF), PATH_INSERT_PDF + " should be published."),
				() -> assertFalse(paths.has(PATH_GET_SAMPLE), PATH_GET_SAMPLE + " should be hidden."),
				() -> assertFalse(paths.has(PATH_SHOW_CSV), PATH_SHOW_CSV + " should be hidden."),
				() -> assertFalse(paths.has(PATH_PRINT_CSV), PATH_PRINT_CSV + " should be hidden."));
	}

	/**
	 * PDF APIのOpenAPI定義に、tokenヘッダー、multipart request、PDF/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 * @param path    API path
	 */
	private void assertPdfEndpoint(JsonNode openApi, String path) {
		JsonNode operation = openApi.path("paths").path(path).path(HTTP_METHOD_POST);

		assertAll(() -> assertFalse(operation.isMissingNode(), path + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, path), () -> assertMultipartRequestBody(operation, path),
				() -> assertPdfResponse(operation, path),
				() -> assertJsonErrorResponse(operation, path, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						path + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, path, HTTP_STATUS_PAYLOAD_TOO_LARGE),
				() -> assertJsonErrorResponse(operation, path, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * ZIPを返すPDF APIのOpenAPI定義に、tokenヘッダー、multipart
	 * request、ZIP/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 * @param path    API path
	 */
	private void assertZipEndpoint(JsonNode openApi, String path) {
		JsonNode operation = openApi.path("paths").path(path).path(HTTP_METHOD_POST);

		assertAll(() -> assertFalse(operation.isMissingNode(), path + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, path), () -> assertMultipartRequestBody(operation, path),
				() -> assertZipResponse(operation, path),
				() -> assertJsonErrorResponse(operation, path, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						path + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, path, HTTP_STATUS_PAYLOAD_TOO_LARGE),
				() -> assertJsonErrorResponse(operation, path, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * PDFメタデータAPIのOpenAPI定義に、tokenヘッダー、multipart request、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMetadataEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_METADATA_PDF).path(HTTP_METHOD_POST);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_METADATA_PDF + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_METADATA_PDF),
				() -> assertMultipartRequestBody(operation, PATH_METADATA_PDF),
				() -> assertApiResultOkResponse(openApi, operation, PATH_METADATA_PDF, SCHEMA_PDF_METADATA_RESPONSE),
				() -> assertJsonErrorResponse(operation, PATH_METADATA_PDF, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_METADATA_PDF + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, PATH_METADATA_PDF, HTTP_STATUS_PAYLOAD_TOO_LARGE),
				() -> assertJsonErrorResponse(operation, PATH_METADATA_PDF, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * PDFテキスト抽出APIのOpenAPI定義に、tokenヘッダー、multipart
	 * request、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertTextEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_TEXT_PDF).path(HTTP_METHOD_POST);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_TEXT_PDF + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_TEXT_PDF),
				() -> assertMultipartRequestBody(operation, PATH_TEXT_PDF),
				() -> assertApiResultOkResponse(openApi, operation, PATH_TEXT_PDF, SCHEMA_PDF_TEXT_RESPONSE),
				() -> assertJsonErrorResponse(operation, PATH_TEXT_PDF, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_TEXT_PDF + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, PATH_TEXT_PDF, HTTP_STATUS_PAYLOAD_TOO_LARGE),
				() -> assertJsonErrorResponse(operation, PATH_TEXT_PDF, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * ページ単位Markdown下書きAPIのtoken、multipart request、JSON/HTTP status契約を確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownDraftEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_DRAFT_PDF).path(HTTP_METHOD_POST);
		assertAll(
				() -> assertFalse(operation.isMissingNode(),
						PATH_MARKDOWN_DRAFT_PDF + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_DRAFT_PDF),
				() -> assertMultipartRequestBody(operation, PATH_MARKDOWN_DRAFT_PDF),
				() -> assertApiResultOkResponse(openApi, operation, PATH_MARKDOWN_DRAFT_PDF,
						SCHEMA_PDF_MARKDOWN_DRAFT_RESPONSE),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_PDF, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_DRAFT_PDF + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_PDF,
						HTTP_STATUS_PAYLOAD_TOO_LARGE),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_PDF,
						HTTP_STATUS_INTERNAL_SERVER_ERROR),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_PDF,
						HTTP_STATUS_SERVICE_UNAVAILABLE));
	}

	/**
	 * 画像Markdown下書きAPIのtoken、multipart request、JSON/HTTP status契約を確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownDraftImageEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_DRAFT_IMAGE).path(HTTP_METHOD_POST);
		assertAll(
				() -> assertFalse(operation.isMissingNode(),
						PATH_MARKDOWN_DRAFT_IMAGE + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_DRAFT_IMAGE),
				() -> assertMultipartRequestBody(operation, PATH_MARKDOWN_DRAFT_IMAGE),
				() -> assertApiResultOkResponse(openApi, operation, PATH_MARKDOWN_DRAFT_IMAGE,
						SCHEMA_IMAGE_MARKDOWN_DRAFT_RESPONSE),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_IMAGE, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_DRAFT_IMAGE + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_IMAGE, HTTP_STATUS_PAYLOAD_TOO_LARGE),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_IMAGE, HTTP_STATUS_INTERNAL_SERVER_ERROR),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_DRAFT_IMAGE, HTTP_STATUS_SERVICE_UNAVAILABLE));
	}

	/**
	 * 画像Markdown下書きAPIのrequest/response schemaに公開項目と型が定義されることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertImageMarkdownDraftSchemas(JsonNode openApi) {
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode requestSchema = schemas.path(SCHEMA_IMAGE_MARKDOWN_DRAFT_REQUEST);
		JsonNode requestProperties = requestSchema.path("properties");
		JsonNode imageFile = requestProperties.path("imageFile");
		JsonNode responseProperties = schemas.path(SCHEMA_IMAGE_MARKDOWN_DRAFT_RESPONSE).path("properties");

		assertAll(() -> assertTrue(schemas.has(SCHEMA_IMAGE_MARKDOWN_DRAFT_REQUEST)),
				() -> assertTrue(requestProperties.has("imageFile")),
				() -> assertRequiredProperty(requestSchema, SCHEMA_IMAGE_MARKDOWN_DRAFT_REQUEST, "imageFile"),
				() -> assertEquals("string", imageFile.path("type").asString()),
				() -> assertEquals("binary", imageFile.path("format").asString()),
				() -> assertTrue(schemas.has(SCHEMA_IMAGE_MARKDOWN_DRAFT_RESPONSE)),
				() -> assertTrue(responseProperties.has("fileName")),
				() -> assertTrue(responseProperties.has("fileSize")),
				() -> assertTrue(responseProperties.has("markdown")),
				() -> assertEquals("string", responseProperties.path("markdown").path("type").asString()));
	}

	/**
	 * Markdown保存APIのOpenAPI定義に、tokenヘッダー、JSON request、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_SAVE_MARKDOWN).path(HTTP_METHOD_POST);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_SAVE_MARKDOWN + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_SAVE_MARKDOWN),
				() -> assertJsonRequestBody(operation, PATH_SAVE_MARKDOWN),
				() -> assertApiResultOkResponse(openApi, operation, PATH_SAVE_MARKDOWN, SCHEMA_MARKDOWN_FILE_RESPONSE),
				() -> assertJsonErrorResponse(operation, PATH_SAVE_MARKDOWN, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_SAVE_MARKDOWN + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, PATH_SAVE_MARKDOWN, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * Markdown一覧取得APIのOpenAPI定義に、tokenヘッダー、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownFilesEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_FILES).path(HTTP_METHOD_GET);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_MARKDOWN_FILES + " get operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_FILES),
				() -> assertApiResultListOkResponse(openApi, operation, PATH_MARKDOWN_FILES,
						SCHEMA_MARKDOWN_FILE_RESPONSE),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_FILES + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_FILES, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * Markdown本文取得APIのOpenAPI定義に、tokenヘッダー、query
	 * parameter、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownFileEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_FILE).path(HTTP_METHOD_GET);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_MARKDOWN_FILE + " get operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_FILE),
				() -> assertQueryParameter(operation, PATH_MARKDOWN_FILE, "fileName"),
				() -> assertApiResultOkResponse(openApi, operation, PATH_MARKDOWN_FILE, SCHEMA_MARKDOWN_DOCUMENT_RESPONSE),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_BAD_REQUEST),
						PATH_MARKDOWN_FILE + " should define 400 response."),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_FILE + " should define 403 response."),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_NOT_FOUND),
						PATH_MARKDOWN_FILE + " should define 404 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_FILE, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * Markdown更新APIのOpenAPI定義に、tokenヘッダー、query parameter、JSON
	 * request、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownFileUpdateEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_FILE).path(HTTP_METHOD_PUT);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_MARKDOWN_FILE + " put operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_FILE),
				() -> assertQueryParameter(operation, PATH_MARKDOWN_FILE, "fileName"),
				() -> assertJsonRequestBody(operation, PATH_MARKDOWN_FILE),
				() -> assertApiResultOkResponse(openApi, operation, PATH_MARKDOWN_FILE, SCHEMA_MARKDOWN_FILE_RESPONSE),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_FILE, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_FILE + " should define 403 response."),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_NOT_FOUND),
						PATH_MARKDOWN_FILE + " should define 404 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_FILE, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * Markdown削除APIのOpenAPI定義に、tokenヘッダー、query parameter、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownFileDeleteEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_FILE).path(HTTP_METHOD_DELETE);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_MARKDOWN_FILE + " delete operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_FILE),
				() -> assertQueryParameter(operation, PATH_MARKDOWN_FILE, "fileName"),
				() -> assertApiResultOkResponse(openApi, operation, PATH_MARKDOWN_FILE, SCHEMA_MARKDOWN_DELETE_RESPONSE),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_BAD_REQUEST),
						PATH_MARKDOWN_FILE + " should define 400 response."),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_FILE + " should define 403 response."),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_NOT_FOUND),
						PATH_MARKDOWN_FILE + " should define 404 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_FILE, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * Markdownプレビュー取得APIのOpenAPI定義に、tokenヘッダー、query
	 * parameter、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownPreviewEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_PREVIEW).path(HTTP_METHOD_GET);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_MARKDOWN_PREVIEW + " get operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_PREVIEW),
				() -> assertQueryParameter(operation, PATH_MARKDOWN_PREVIEW, "fileName"),
				() -> assertApiResultOkResponse(openApi, operation, PATH_MARKDOWN_PREVIEW,
						SCHEMA_MARKDOWN_PREVIEW_RESPONSE),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_BAD_REQUEST),
						PATH_MARKDOWN_PREVIEW + " should define 400 response."),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_PREVIEW + " should define 403 response."),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_NOT_FOUND),
						PATH_MARKDOWN_PREVIEW + " should define 404 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_PREVIEW, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * Markdown本文プレビューAPIのOpenAPI定義に、tokenヘッダー、JSON
	 * request、JSON/エラーレスポンスが含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownPreviewContentEndpoint(JsonNode openApi) {
		JsonNode operation = openApi.path("paths").path(PATH_MARKDOWN_PREVIEW).path(HTTP_METHOD_POST);

		assertAll(() -> assertFalse(operation.isMissingNode(), PATH_MARKDOWN_PREVIEW + " post operation should exist."),
				() -> assertAccessTokenHeader(operation, PATH_MARKDOWN_PREVIEW),
				() -> assertJsonRequestBody(operation, PATH_MARKDOWN_PREVIEW),
				() -> assertApiResultOkResponse(openApi, operation, PATH_MARKDOWN_PREVIEW,
						SCHEMA_MARKDOWN_PREVIEW_CONTENT_RESPONSE),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_PREVIEW, HTTP_STATUS_BAD_REQUEST),
				() -> assertTrue(operation.path("responses").has(HTTP_STATUS_FORBIDDEN),
						PATH_MARKDOWN_PREVIEW + " should define 403 response."),
				() -> assertJsonErrorResponse(operation, PATH_MARKDOWN_PREVIEW, HTTP_STATUS_INTERNAL_SERVER_ERROR));
	}

	/**
	 * access-tokenヘッダーが必須header parameterとして定義されていることを確認する。
	 *
	 * @param operation OpenAPI operation
	 * @param path      API path
	 */
	private void assertAccessTokenHeader(JsonNode operation, String path) {
		boolean hasAccessTokenHeader = StreamSupport.stream(operation.path("parameters").spliterator(), false)
				.anyMatch(parameter -> ACCESS_TOKEN_HEADER_NAME.equals(parameter.path("name").asString())
						&& "header".equals(parameter.path("in").asString()) && parameter.path("required").asBoolean());

		assertTrue(hasAccessTokenHeader, path + " should require access-token header.");
	}

	/**
	 * query parameterが必須として定義されていることを確認する。
	 *
	 * @param operation OpenAPI operation
	 * @param path      API path
	 * @param name      query parameter名
	 */
	private void assertQueryParameter(JsonNode operation, String path, String name) {
		boolean hasQueryParameter = StreamSupport.stream(operation.path("parameters").spliterator(), false)
				.anyMatch(parameter -> name.equals(parameter.path("name").asString())
						&& "query".equals(parameter.path("in").asString()) && parameter.path("required").asBoolean());

		assertTrue(hasQueryParameter, path + " should require " + name + " query parameter.");
	}

	/**
	 * multipart/form-dataのrequestBodyが定義されていることを確認する。
	 *
	 * @param operation OpenAPI operation
	 * @param path      API path
	 */
	private void assertMultipartRequestBody(JsonNode operation, String path) {
		JsonNode multipartContent = operation.path("requestBody").path("content")
				.path(MediaType.MULTIPART_FORM_DATA_VALUE);

		assertFalse(multipartContent.isMissingNode(), path + " should define multipart/form-data requestBody.");
	}

	/**
	 * application/jsonのrequestBodyが定義されていることを確認する。
	 *
	 * @param operation OpenAPI operation
	 * @param path      API path
	 */
	private void assertJsonRequestBody(JsonNode operation, String path) {
		JsonNode jsonContent = operation.path("requestBody").path("content").path(MediaType.APPLICATION_JSON_VALUE);

		assertFalse(jsonContent.isMissingNode(), path + " should define application/json requestBody.");
	}

	/**
	 * PDFレスポンスがapplication/pdfとして定義されていることを確認する。
	 *
	 * @param operation OpenAPI operation
	 * @param path      API path
	 */
	private void assertPdfResponse(JsonNode operation, String path) {
		JsonNode pdfContent = operation.path("responses").path(HTTP_STATUS_OK).path("content")
				.path(MediaType.APPLICATION_PDF_VALUE);

		assertFalse(pdfContent.isMissingNode(), path + " should define application/pdf 200 response.");
	}

	/**
	 * ZIPレスポンスがapplication/zipとして定義されていることを確認する。
	 *
	 * @param operation OpenAPI operation
	 * @param path      API path
	 */
	private void assertZipResponse(JsonNode operation, String path) {
		JsonNode zipContent = operation.path("responses").path(HTTP_STATUS_OK).path("content")
				.path(MEDIA_TYPE_APPLICATION_ZIP_VALUE);

		assertFalse(zipContent.isMissingNode(), path + " should define application/zip 200 response.");
	}

	/**
	 * JSONレスポンスがapplication/jsonとして定義されていることを確認する。
	 *
	 * @param operation OpenAPI operation
	 * @param path      API path
	 */
	private void assertJsonOkResponse(JsonNode operation, String path) {
		JsonNode jsonContent = operation.path("responses").path(HTTP_STATUS_OK).path("content")
				.path(MediaType.APPLICATION_JSON_VALUE);

		assertFalse(jsonContent.isMissingNode(), path + " should define application/json 200 response.");
	}

	/**
	 * 200レスポンスが共通ラッパー {@code ApiResult<T>} の構造で公開され、{@code data} が用途別Responseになっていることを確認する。
	 * <p>
	 * Springdocのgeneric schema表現は崩れやすいため、ラッパーのschema名と {@code data} の中身の両方を固定する。
	 * 200の {@code @ApiResponse} に {@code content} を書くと戻り型からの推論が上書きされ、schemaが消えることを検知する狙いもある。
	 *
	 * @param openApi        OpenAPI JSON
	 * @param operation      OpenAPI operation
	 * @param path           API path
	 * @param dataSchemaName {@code data} に現れる用途別Responseのschema名
	 */
	private void assertApiResultOkResponse(JsonNode openApi, JsonNode operation, String path, String dataSchemaName) {
		String wrapperSchemaName = SCHEMA_API_RESULT_PREFIX + dataSchemaName;
		JsonNode responseSchema = operation.path("responses").path(HTTP_STATUS_OK).path("content")
				.path(MediaType.APPLICATION_JSON_VALUE).path("schema");
		JsonNode wrapperProperties = openApi.path("components").path("schemas").path(wrapperSchemaName)
				.path("properties");

		assertAll(
				() -> assertEquals("#/components/schemas/" + wrapperSchemaName, responseSchema.path("$ref").asString(),
						path + " 200 response should be wrapped by " + wrapperSchemaName + "."),
				() -> assertEquals("#/components/schemas/" + dataSchemaName,
						wrapperProperties.path("data").path("$ref").asString(),
						wrapperSchemaName + " data should reference " + dataSchemaName + "."),
				() -> assertApiResultCommonProperties(wrapperProperties, wrapperSchemaName));
	}

	/**
	 * 200レスポンスが共通ラッパーの構造で公開され、{@code data} が用途別Responseの配列になっていることを確認する。
	 *
	 * @param openApi        OpenAPI JSON
	 * @param operation      OpenAPI operation
	 * @param path           API path
	 * @param dataSchemaName {@code data} の配列要素に現れる用途別Responseのschema名
	 */
	private void assertApiResultListOkResponse(JsonNode openApi, JsonNode operation, String path,
			String dataSchemaName) {
		String wrapperSchemaName = SCHEMA_API_RESULT_PREFIX + SCHEMA_LIST_PREFIX + dataSchemaName;
		JsonNode responseSchema = operation.path("responses").path(HTTP_STATUS_OK).path("content")
				.path(MediaType.APPLICATION_JSON_VALUE).path("schema");
		JsonNode wrapperProperties = openApi.path("components").path("schemas").path(wrapperSchemaName)
				.path("properties");

		assertAll(
				() -> assertEquals("#/components/schemas/" + wrapperSchemaName, responseSchema.path("$ref").asString(),
						path + " 200 response should be wrapped by " + wrapperSchemaName + "."),
				() -> assertEquals("array", wrapperProperties.path("data").path("type").asString(),
						wrapperSchemaName + " data should be an array."),
				() -> assertEquals("#/components/schemas/" + dataSchemaName,
						wrapperProperties.path("data").path("items").path("$ref").asString(),
						wrapperSchemaName + " data items should reference " + dataSchemaName + "."),
				() -> assertApiResultCommonProperties(wrapperProperties, wrapperSchemaName));
	}

	/**
	 * 共通ラッパーの {@code resultType} と {@code messageList} の定義を確認する。
	 *
	 * @param wrapperProperties ラッパーschemaのproperties
	 * @param wrapperSchemaName ラッパーschema名
	 */
	private void assertApiResultCommonProperties(JsonNode wrapperProperties, String wrapperSchemaName) {
		JsonNode resultTypeValues = wrapperProperties.path("resultType").path("enum");

		assertAll(
				() -> assertTrue(
						StreamSupport.stream(resultTypeValues.spliterator(), false)
								.anyMatch(value -> "INFO".equals(value.asString())),
						wrapperSchemaName + " resultType should contain INFO."),
				() -> assertTrue(
						StreamSupport.stream(resultTypeValues.spliterator(), false)
								.anyMatch(value -> "WARNING".equals(value.asString())),
						wrapperSchemaName + " resultType should contain WARNING."),
				() -> assertEquals("#/components/schemas/" + SCHEMA_API_MESSAGE,
						wrapperProperties.path("messageList").path("items").path("$ref").asString(),
						wrapperSchemaName + " messageList items should reference " + SCHEMA_API_MESSAGE + "."));
	}

	/**
	 * エラーレスポンスがapplication/jsonとして定義されていることを確認する。
	 *
	 * @param operation  OpenAPI operation
	 * @param path       API path
	 * @param statusCode HTTP status code
	 */
	private void assertJsonErrorResponse(JsonNode operation, String path, String statusCode) {
		JsonNode jsonContent = operation.path("responses").path(statusCode).path("content")
				.path(MediaType.APPLICATION_JSON_VALUE);

		assertFalse(jsonContent.isMissingNode(), path + " should define application/json " + statusCode + " response.");
	}

	/**
	 * schemaのrequired配列に指定された項目名が含まれることを確認する。
	 *
	 * @param schema       OpenAPI schema
	 * @param schemaName   schema名
	 * @param propertyName 必須項目名
	 */
	private void assertRequiredProperty(JsonNode schema, String schemaName, String propertyName) {
		boolean hasRequiredProperty = StreamSupport.stream(schema.path("required").spliterator(), false)
				.anyMatch(requiredProperty -> propertyName.equals(requiredProperty.asString()));

		assertTrue(hasRequiredProperty, schemaName + " should require " + propertyName + ".");
	}

	/**
	 * PDF request schemaに既存multipartフォーム項目名が含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertPdfRequestSchemas(JsonNode openApi) {
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode originalPdfRequestProperties = schemas.path(SCHEMA_ORIGINAL_PDF_REQUEST).path("properties");
		JsonNode extractPdfRequestProperties = schemas.path(SCHEMA_EXTRACT_PDF_REQUEST).path("properties");
		JsonNode mergePdfRequestProperties = schemas.path(SCHEMA_MERGE_PDF_REQUEST).path("properties");
		JsonNode splitPdfRequestProperties = schemas.path(SCHEMA_SPLIT_PDF_REQUEST).path("properties");
		JsonNode insertPdfRequestProperties = schemas.path(SCHEMA_INSERT_PDF_REQUEST).path("properties");

		assertAll(() -> assertTrue(schemas.has(SCHEMA_ORIGINAL_PDF_REQUEST)),
				() -> assertTrue(originalPdfRequestProperties.has("originalFile")),
				() -> assertTrue(originalPdfRequestProperties.has("originalDeletePages")),
				() -> assertTrue(originalPdfRequestProperties.has("insertPdfForm")),
				() -> assertTrue(schemas.has(SCHEMA_EXTRACT_PDF_REQUEST)),
				() -> assertTrue(extractPdfRequestProperties.has("originalFile")),
				() -> assertTrue(extractPdfRequestProperties.has("extractPages")),
				() -> assertTrue(schemas.has(SCHEMA_MERGE_PDF_REQUEST)),
				() -> assertTrue(mergePdfRequestProperties.has("mergeFiles")),
				() -> assertTrue(schemas.has(SCHEMA_SPLIT_PDF_REQUEST)),
				() -> assertTrue(splitPdfRequestProperties.has("originalFile")),
				() -> assertTrue(schemas.has(SCHEMA_INSERT_PDF_REQUEST)),
				() -> assertTrue(insertPdfRequestProperties.has("insertFile")),
				() -> assertTrue(insertPdfRequestProperties.has("insertPage")),
				() -> assertTrue(insertPdfRequestProperties.has("insertOption")));
	}

	/**
	 * PDFメタデータレスポンスschemaにAPI項目名が含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMetadataResponseSchema(JsonNode openApi) {
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode metadataProperties = schemas.path(SCHEMA_PDF_METADATA_RESPONSE).path("properties");

		assertAll(() -> assertTrue(schemas.has(SCHEMA_PDF_METADATA_RESPONSE)),
				() -> assertTrue(metadataProperties.has("fileName")),
				() -> assertTrue(metadataProperties.has("fileSize")),
				() -> assertTrue(metadataProperties.has("pageCount")),
				() -> assertTrue(metadataProperties.has("encrypted")));
	}

	/**
	 * PDFテキスト抽出レスポンスschemaにAPI項目名が含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertTextResponseSchema(JsonNode openApi) {
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode textProperties = schemas.path(SCHEMA_PDF_TEXT_RESPONSE).path("properties");

		assertAll(() -> assertTrue(schemas.has(SCHEMA_PDF_TEXT_RESPONSE)),
				() -> assertTrue(textProperties.has("fileName")), () -> assertTrue(textProperties.has("fileSize")),
				() -> assertTrue(textProperties.has("pageCount")), () -> assertTrue(textProperties.has("text")));
	}

	/**
	 * ページ単位Markdown下書きAPIのrequest/response schemaに公開項目と型が定義されることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownDraftSchemas(JsonNode openApi) {
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode requestSchema = schemas.path(SCHEMA_PDF_MARKDOWN_DRAFT_REQUEST);
		JsonNode requestProperties = requestSchema.path("properties");
		JsonNode originalFile = requestProperties.path("originalFile");
		JsonNode responseProperties = schemas.path(SCHEMA_PDF_MARKDOWN_DRAFT_RESPONSE).path("properties");
		JsonNode pages = responseProperties.path("pages");
		JsonNode pageProperties = schemas.path(SCHEMA_PDF_MARKDOWN_DRAFT_PAGE_RESPONSE).path("properties");

		assertAll(() -> assertTrue(schemas.has(SCHEMA_PDF_MARKDOWN_DRAFT_REQUEST)),
				() -> assertTrue(requestProperties.has("originalFile")),
				() -> assertRequiredProperty(requestSchema, SCHEMA_PDF_MARKDOWN_DRAFT_REQUEST, "originalFile"),
				() -> assertEquals("string", originalFile.path("type").asString()),
				() -> assertEquals("binary", originalFile.path("format").asString()),
				() -> assertTrue(requestProperties.has("mode")),
				() -> assertTrue(schemas.has(SCHEMA_PDF_MARKDOWN_DRAFT_RESPONSE)),
				() -> assertTrue(responseProperties.has("fileName")),
				() -> assertTrue(responseProperties.has("fileSize")),
				() -> assertTrue(responseProperties.has("pageCount")),
				() -> assertTrue(responseProperties.has("markdown")),
				() -> assertEquals("string", responseProperties.path("markdown").path("type").asString()),
				() -> assertTrue(responseProperties.has("pages")),
				() -> assertEquals("array", pages.path("type").asString()),
				() -> assertEquals("#/components/schemas/" + SCHEMA_PDF_MARKDOWN_DRAFT_PAGE_RESPONSE,
						pages.path("items").path("$ref").asString()),
				() -> assertTrue(schemas.has(SCHEMA_PDF_MARKDOWN_DRAFT_PAGE_RESPONSE)),
				() -> assertTrue(pageProperties.has("pageNumber")),
				() -> assertEquals("integer", pageProperties.path("pageNumber").path("type").asString()),
				() -> assertTrue(pageProperties.has("text")),
				() -> assertEquals("string", pageProperties.path("text").path("type").asString()),
				() -> assertTrue(pageProperties.has("source")),
				() -> assertEquals("string", pageProperties.path("source").path("type").asString()));
	}

	/**
	 * Markdown保存APIのrequest/response schemaにAPI項目名が含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertMarkdownSchemas(JsonNode openApi) {
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode requestSchema = schemas.path(SCHEMA_MARKDOWN_SAVE_REQUEST);
		JsonNode updateRequestSchema = schemas.path(SCHEMA_MARKDOWN_UPDATE_REQUEST);
		JsonNode previewRequestSchema = schemas.path(SCHEMA_MARKDOWN_PREVIEW_REQUEST);
		JsonNode requestProperties = schemas.path(SCHEMA_MARKDOWN_SAVE_REQUEST).path("properties");
		JsonNode updateRequestProperties = schemas.path(SCHEMA_MARKDOWN_UPDATE_REQUEST).path("properties");
		JsonNode previewRequestProperties = schemas.path(SCHEMA_MARKDOWN_PREVIEW_REQUEST).path("properties");
		JsonNode responseProperties = schemas.path(SCHEMA_MARKDOWN_FILE_RESPONSE).path("properties");

		JsonNode documentProperties = schemas.path(SCHEMA_MARKDOWN_DOCUMENT_RESPONSE).path("properties");
		JsonNode previewProperties = schemas.path(SCHEMA_MARKDOWN_PREVIEW_RESPONSE).path("properties");
		JsonNode previewContentProperties = schemas.path(SCHEMA_MARKDOWN_PREVIEW_CONTENT_RESPONSE).path("properties");
		JsonNode deleteProperties = schemas.path(SCHEMA_MARKDOWN_DELETE_RESPONSE).path("properties");

		assertAll(() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_SAVE_REQUEST)),
				() -> assertTrue(requestProperties.has("fileName")), () -> assertTrue(requestProperties.has("content")),
				() -> assertRequiredProperty(requestSchema, SCHEMA_MARKDOWN_SAVE_REQUEST, "fileName"),
				() -> assertRequiredProperty(requestSchema, SCHEMA_MARKDOWN_SAVE_REQUEST, "content"),
				() -> assertEquals(MARKDOWN_FILE_NAME_MAX_LENGTH,
						requestProperties.path("fileName").path("maxLength").asInt()),
				() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_UPDATE_REQUEST)),
				() -> assertTrue(updateRequestProperties.has("content")),
				() -> assertRequiredProperty(updateRequestSchema, SCHEMA_MARKDOWN_UPDATE_REQUEST, "content"),
				() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_PREVIEW_REQUEST)),
				() -> assertTrue(previewRequestProperties.has("content")),
				() -> assertRequiredProperty(previewRequestSchema, SCHEMA_MARKDOWN_PREVIEW_REQUEST, "content"),
				() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_FILE_RESPONSE)),
				() -> assertTrue(responseProperties.has("fileName")),
				() -> assertTrue(responseProperties.has("byteSize")),
				() -> assertTrue(responseProperties.has("lineCount")),
				() -> assertTrue(responseProperties.has("lastModifiedTime")),
				() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_DOCUMENT_RESPONSE)),
				() -> assertTrue(documentProperties.has("fileName")),
				() -> assertTrue(documentProperties.has("byteSize")),
				() -> assertTrue(documentProperties.has("lineCount")),
				() -> assertTrue(documentProperties.has("lastModifiedTime")),
				() -> assertTrue(documentProperties.has("content")),
				() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_PREVIEW_RESPONSE)),
				() -> assertTrue(previewProperties.has("fileName")),
				() -> assertTrue(previewProperties.has("byteSize")),
				() -> assertTrue(previewProperties.has("lineCount")),
				() -> assertTrue(previewProperties.has("lastModifiedTime")),
				() -> assertTrue(previewProperties.has("html")),
				() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_PREVIEW_CONTENT_RESPONSE)),
				() -> assertTrue(previewContentProperties.has("html")),
				() -> assertTrue(schemas.has(SCHEMA_MARKDOWN_DELETE_RESPONSE)),
				() -> assertTrue(deleteProperties.has("fileName")), () -> assertTrue(deleteProperties.has("deleted")));
	}

	/**
	 * 共通エラーレスポンスschemaに既存JSON項目名が含まれることを確認する。
	 *
	 * @param openApi OpenAPI JSON
	 */
	private void assertErrorResponseSchemas(JsonNode openApi) {
		JsonNode schemas = openApi.path("components").path("schemas");
		JsonNode errorResponseProperties = schemas.path(SCHEMA_ERROR_RESPONSE).path("properties");
		JsonNode customFieldErrorProperties = schemas.path(SCHEMA_CUSTOM_FIELD_ERROR).path("properties");

		assertAll(() -> assertTrue(schemas.has(SCHEMA_ERROR_RESPONSE)),
				() -> assertTrue(errorResponseProperties.has("fieldErrors")),
				() -> assertTrue(schemas.has(SCHEMA_CUSTOM_FIELD_ERROR)),
				() -> assertTrue(customFieldErrorProperties.has("errorCode")),
				() -> assertTrue(customFieldErrorProperties.has("field")),
				() -> assertTrue(customFieldErrorProperties.has("message")));
	}
}
