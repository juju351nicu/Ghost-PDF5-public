package com.clip.ghost.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.pdfcontent.enums.CodeEnum;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;

/**
 * プロジェクトのコーディング規約を守るための軽量アーキテクチャテスト。
 * <p>
 * クラス依存で検出できる規約はArchUnitで確認し、pom.xmlや固定パスなど
 * ファイル内容を直接見る必要がある規約はJUnitのファイルスキャンで確認する。
 */
class CodingConventionTest {
	private static final Path PROJECT_ROOT = Paths.get(".");
	private static final Path MAIN_SOURCE = Paths.get("src/main/java");
	private static final Path MAIN_APPLICATION_YAML = Paths.get("src/main/resources/application.yml");
	private static final Path FRONTEND_SOURCE = Paths.get("src/main/resources/static/js");
	private static final Path FRONTEND_MAIN_TEMPLATE = Paths.get("src/main/resources/templates/main.html");
	private static final Path FRONTEND_UTIL_SCRIPT = FRONTEND_SOURCE.resolve("util.js");
	private static final Path FRONTEND_FETCH_CLIENT_SCRIPT = FRONTEND_SOURCE.resolve("api/fetch-client.js");
	private static final Path FRONTEND_API_ERROR_UTILS_SCRIPT = FRONTEND_SOURCE.resolve("api/api-error-utils.js");
	private static final Path FRONTEND_API_RESULT_UTILS_SCRIPT = FRONTEND_SOURCE.resolve("api/api-result-utils.js");
	private static final Path FRONTEND_FILE_RESPONSE_HANDLER_SCRIPT = FRONTEND_SOURCE
			.resolve("api/file-response-handler.js");
	private static final Path TEST_SOURCE = Paths.get("src/test/java");
	private static final Path TEST_RESOURCES = Paths.get("src/test/resources");
	private static final String BASE_PACKAGE = "com.clip.ghost";
	private static final String UPLOAD_FILE_SIZE_VALIDATOR_SOURCE = "com/clip/ghost/common/utils/UploadFileSizeValidator.java";
	private static final String MARKDOWN_TEXT_NORMALIZER_SOURCE = "com/clip/ghost/common/utils/MarkdownTextNormalizer.java";
	private static final String COMMONS_LANG3_PACKAGE = "org.apache.commons.lang3";
	private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE_PACKAGE);
	private static final JavaClasses PRODUCTION_AND_TEST_CLASSES = new ClassFileImporter()
			.importPackages(BASE_PACKAGE);
	private static final Pattern SERVICE_DTO_CREATION = Pattern.compile("new\\s+\\w*Dto\\s*\\(");
	// @TestPropertySource や @TestInstance と取り違えないよう、テストメソッドのannotationだけを厳密に拾う。
	private static final Pattern TEST_ANNOTATION = Pattern
			.compile("^@(Test|ParameterizedTest|RepeatedTest)\\s*(\\(.*\\))?$");
	private static final String DISPLAY_NAME_ANNOTATION = "@DisplayName";
	private static final Pattern METHOD_DECLARATION = Pattern
			.compile("\\s*(?:private|public|protected)\\s+[^=;]+\\s+(\\w+)\\s*\\([^;]*\\).*");
	private static final Pattern PUBLIC_TYPE_DECLARATION = Pattern
			.compile("\\s*public\\s+(?:final\\s+)?(?:class|interface|enum|@interface)\\b.*");
	private static final Pattern PUBLIC_OR_PROTECTED_METHOD_DECLARATION = Pattern.compile(
			"\\s*(?:public|protected)\\s+(?:static\\s+)?(?:final\\s+)?[\\w<>?, ?\\[\\]]+\\s+\\w+\\s*\\([^;]*\\).*");
	private static final Pattern LOOSE_JAVASCRIPT_EQUALITY = Pattern.compile("(?<![=!])(?:==|!=)(?![=])");
	private static final Pattern INLINE_STYLE_ATTRIBUTE = Pattern.compile("\\sstyle=");
	private static final Pattern BUTTON_WITHOUT_TYPE = Pattern.compile("<button(?![^>]*\\btype=)");
	private static final Pattern V_FOR_WITHOUT_KEY = Pattern.compile("v-for=\"[^\"]+\"(?![^>]*:key=)");
	private static final Pattern WINDOW_OPEN = Pattern.compile("window\\.open\\([^)]*\\)");

	@Test
	@DisplayName("本番コードでLombokの@Dataを使わない")
	void productionCodeDoesNotUseLombokData() throws IOException {
		// lombok.DataはSOURCE retentionのため、コンパイル後のクラスを見るArchUnitでは検出できない。
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("import lombok.Data", "@Data"));
	}

	@Test
	@DisplayName("本番コードで@Autowiredのfield injectionを使わない")
	void productionCodeDoesNotUseAutowiredFieldInjection() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE),
				List.of("import org.springframework.beans.factory.annotation.Autowired", "@Autowired"));
	}

	@Test
	@DisplayName("本番コードでSystem.outとprintStackTraceを使わない")
	void productionCodeDoesNotUseConsoleOutput() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("System.out", "printStackTrace("));
	}

	@Test
	@DisplayName("本番コードのpublic宣言にJavadocを付ける")
	void productionPublicDeclarationsHaveJavadocs() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path javaFile : javaFiles(MAIN_SOURCE)) {
			List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
			for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
				String line = lines.get(lineIndex);
				if (isPublicDeclarationThatNeedsJavadoc(line) && !hasJavadocBeforeDeclaration(lines, lineIndex)) {
					violations.add(javaFile + ":" + (lineIndex + 1));
				}
			}
		}

		assertTrue(violations.isEmpty(),
				() -> "public class/interface/enum または public/protected method にはJavadocを付けてください: " + violations);
	}

	@Test
	@DisplayName("Javadocに@authorタグを書かない")
	void sourceJavadocsDoNotUseAuthorTags() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		targetFiles.addAll(javaFiles(MAIN_SOURCE));
		targetFiles.addAll(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("@author"));
	}

	@Test
	@DisplayName("common.utilsのユーティリティクラスをfinalにする")
	void productionUtilityClassesAreFinal() {
		classes().that().resideInAPackage(BASE_PACKAGE + ".common.utils..").should().haveModifier(JavaModifier.FINAL)
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("削除済みのStorageUtilsを本番コードで参照しない")
	void productionCodeDoesNotUseDeletedStorageUtils() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("StorageUtils"));
	}

	@Test
	@DisplayName("コレクションの空判定をCollectionUtilsへ寄せる")
	void productionCodeUsesCollectionUtilsForCollectionEmptyChecks() {
		// 受け手の型で判定するため、MultipartFile / Optional / Map の isEmpty は対象外になる。
		noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
				.should(callNoArgumentMethodOn(Collection.class, "isEmpty"))
				.because("コレクションの空判定は独自実装せず、null安全な CollectionUtils.isEmpty / isNotEmpty を使ってください。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("文字列の空判定をStringUtilsへ寄せる")
	void productionCodeUsesStringUtilsForStringEmptyChecks() {
		noClasses().that().resideInAPackage(BASE_PACKAGE + "..")
				.should(callNoArgumentMethodOn(CharSequence.class, "isEmpty", "isBlank"))
				.because("文字列の空判定は独自実装せず、null安全な StringUtils.isEmpty / isNotEmpty / isBlank / isNotBlank を使ってください。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("commons-lang3の非推奨APIを本番・テストとも呼ばない")
	void codeDoesNotCallDeprecatedCommonsLang3Apis() {
		// commons-lang3 3.19では StringUtils.equals / contains / startsWith などが非推奨で、後継は Strings.CS / Strings.CI。
		// 非推奨APIは本番コードと同じ理由でテストコードにも残さないため、テストクラスも対象にする。
		noClasses().that().resideInAPackage(BASE_PACKAGE + "..").should(callDeprecatedCommonsLang3Method())
				.because("非推奨APIは後継API（Strings.CS / Strings.CI など）へ寄せてください。")
				.check(PRODUCTION_AND_TEST_CLASSES);
	}

	@Test
	@DisplayName("アップロードサイズの上限判定をUploadFileSizeValidatorへ寄せる")
	void productionCodeUsesUploadFileSizeValidatorForUploadSizeChecks() throws IOException {
		// 上限判定を各Controllerへ書き写すと、「上限以上」と「上限超過」のような境界のずれが取り込み口ごとに入る。
		// 判定はUploadFileSizeValidatorの1箇所に置き、Controllerは上限値を渡すだけにする。
		List<Path> targetFiles = new ArrayList<>(javaFiles(MAIN_SOURCE));
		targetFiles.remove(MAIN_SOURCE.resolve(UPLOAD_FILE_SIZE_VALIDATOR_SOURCE));

		assertNoToken(targetFiles, List.of("new MultipartException("));
	}

	@Test
	@DisplayName("改行の正規化をMarkdownTextNormalizerへ寄せる")
	void productionCodeUsesMarkdownTextNormalizerForLineEndings() throws IOException {
		// 取り込み元ごとに書き写すと、同じ本文でも経路によって末尾の空行や改行コードが変わる。
		List<Path> targetFiles = new ArrayList<>(javaFiles(MAIN_SOURCE));
		targetFiles.remove(MAIN_SOURCE.resolve(MARKDOWN_TEXT_NORMALIZER_SOURCE));

		assertNoToken(targetFiles, List.of("replace(\"\\r\\n\", \"\\n\")"));
	}

	@Test
	@DisplayName("Javaファイル全体をコメントアウトして残さない")
	void productionJavaFilesAreNotFullyCommentedOut() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path javaFile : javaFiles(MAIN_SOURCE)) {
			if (isFullyCommentedOutJavaFile(javaFile)) {
				violations.add(javaFile.toString());
			}
		}

		assertTrue(violations.isEmpty(), () -> "Javaファイル全体をコメントアウトして残さず、不要なら削除してください: " + violations);
	}

	@Test
	@DisplayName("MultipartFileのフィールドにOptionalを使わない")
	void multipartFileFieldsDoNotUseOptional() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("Optional<MultipartFile>"));
	}

	@Test
	@DisplayName("旧pdfcontent.model packageを復活させない")
	void pdfContentModelPackageDoesNotReappear() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		targetFiles.addAll(javaFiles(MAIN_SOURCE));
		targetFiles.addAll(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles,
				List.of("com.clip.ghost.pdfcontent.model", "package com.clip.ghost.pdfcontent.model"));
	}

	@Test
	@DisplayName("旧StorageUtilsのタイポmethod名を復活させない")
	void storageUtilsTypoMethodNamesDoNotReappear() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		targetFiles.addAll(javaFiles(MAIN_SOURCE));
		targetFiles.addAll(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("getExtention", "meargePathAndFileName"));
	}

	@Test
	@DisplayName("JsonUtilsのnullを返す旧APIを復活させない")
	void jsonUtilsLegacyNullReturningApisDoNotReappear() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		targetFiles.addAll(javaFiles(MAIN_SOURCE));
		targetFiles.addAll(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("strFormatByJson", "strFormatByJsonBytes", "jsonParse(",
				"JsonUtils.convertValue(", "tryToJsonBytes", "toJsonBytesOrThrow"));
	}

	@Test
	@DisplayName("standaloneSetupのcontroller単体テストでSpring contextを起動しない")
	void standaloneMockMvcTestsDoNotStartSpringContext() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path javaFile : javaFiles(TEST_SOURCE)) {
			if (isCodingConventionTest(javaFile)) {
				continue;
			}
			String source = Files.readString(javaFile, StandardCharsets.UTF_8);
			if (source.contains("@SpringBootTest") && source.contains("standaloneSetup(")) {
				violations.add(javaFile.toString());
			}
		}

		assertTrue(violations.isEmpty(),
				() -> "standaloneSetupを使うcontroller単体テストでは@SpringBootTestを付けないでください: " + violations);
	}

	@Test
	@DisplayName("Spring contextを起動するテストにcontextタグを付ける")
	void springBootTestsDeclareContextTag() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path javaFile : javaFiles(TEST_SOURCE)) {
			if (isCodingConventionTest(javaFile)) {
				continue;
			}
			String source = Files.readString(javaFile, StandardCharsets.UTF_8);
			if (source.contains("@SpringBootTest") && !source.contains("@Tag(\"context\")")) {
				violations.add(javaFile.toString());
			}
		}

		assertTrue(violations.isEmpty(), () -> "@SpringBootTestを使う重いテストには@Tag(\"context\")を付けてください: " + violations);
	}

	@Test
	@DisplayName("mockだけで書けるテストでSpring contextを起動しない")
	void mockOnlyTestsDoNotStartSpringContext() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path javaFile : javaFiles(TEST_SOURCE)) {
			if (isCodingConventionTest(javaFile)) {
				continue;
			}
			String source = Files.readString(javaFile, StandardCharsets.UTF_8);
			boolean usesMockitoInjection = source.contains("@Mock") || source.contains("@InjectMocks");
			if (source.contains("@SpringBootTest") && usesMockitoInjection) {
				violations.add(javaFile.toString());
			}
		}

		assertTrue(violations.isEmpty(), () -> "@Mock/@InjectMocks中心の単体テストでは@SpringBootTestを付けないでください: " + violations);
	}

	@Test
	@DisplayName("テストコードで非推奨の@MockBeanを使わない")
	void testCodeDoesNotUseDeprecatedSpringBootMockBean() throws IOException {
		List<Path> targetFiles = new ArrayList<>(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("org.springframework.boot.test.mock.mockito.MockBean", "@MockBean"));
	}

	@Test
	@DisplayName("OpenAPI JSONとSwagger UIを通常起動で公開しない")
	void springdocEndpointsAreDisabledByDefault() throws IOException {
		String applicationYaml = Files.readString(MAIN_APPLICATION_YAML, StandardCharsets.UTF_8);

		assertTrue(applicationYaml.contains("api-docs:\n    enabled: false"),
				"OpenAPI JSONは通常起動で公開しないため、springdoc.api-docs.enabled=falseを明示してください。");
		assertTrue(applicationYaml.contains("swagger-ui:\n    enabled: false"),
				"Swagger UIは通常起動で公開しないため、springdoc.swagger-ui.enabled=falseを明示してください。");
	}

	@Test
	@DisplayName("フロントエンドでconsole出力とdebuggerを残さない")
	void frontendCodeDoesNotUseDebugOutput() throws IOException {
		assertNoToken(scriptFiles(FRONTEND_SOURCE),
				List.of("console.log", "console.error", "console.warn", "console.info", "console.debug", "debugger"));
	}

	@Test
	@DisplayName("ブラウザストレージへのアクセスをutil.jsへ閉じる")
	void frontendCodeDoesNotAccessBrowserStorageDirectlyOutsideUtil() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_UTIL_SCRIPT);

		assertNoToken(targetFiles, List.of("localStorage", "sessionStorage"));
	}

	@Test
	@DisplayName("File System Access APIをfile-response-handlerへ閉じる")
	void frontendCodeDoesNotUseFileSystemAccessApiOutsideFileResponseHandler() throws IOException {
		// File System Access APIはChrome / Edgeのみ対応で、Firefox / Safariは未対応。
		// 対応ブラウザ差の分岐が複数箇所へ散ると、フォールバックの挙動が場所によってずれるため、
		// 呼び出し口をfile-response-handler.jsだけに保つ。
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_FILE_RESPONSE_HANDLER_SCRIPT);

		assertNoToken(targetFiles,
				List.of("showSaveFilePicker", "showOpenFilePicker", "showDirectoryPicker", "createWritable"));
	}

	@Test
	@DisplayName("廃止したユーティリティ別名をutil.js以外で使わない")
	void frontendCodeDoesNotUseDeprecatedUtilityAliasesOutsideUtil() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_UTIL_SCRIPT);

		assertNoToken(targetFiles, List.of("isLocalStorage", "checkBrowser"));
	}

	@Test
	@DisplayName("HTTP呼び出しをfetch-client経由に限定する")
	void frontendCodeDoesNotBypassFetchClient() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_FETCH_CLIENT_SCRIPT);

		assertNoToken(targetFiles, List.of("fetch(", "new FormData", "new Headers"));
	}

	@Test
	@DisplayName("エラーレスポンスの解釈をapi-error-utilsへ寄せる")
	void frontendCodeUsesApiErrorUtilsForFieldErrors() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_API_ERROR_UTILS_SCRIPT);

		assertNoToken(targetFiles, List.of("fieldErrors"));
	}

	@Test
	@DisplayName("成功レスポンスの共通ラッパー解釈をapi-result-utilsへ寄せる")
	void frontendCodeUsesApiResultUtilsForSuccessEnvelope() throws IOException {
		// JSON成功レスポンスの共通ラッパー(resultType / messageList)の解釈をapi-result-utils.jsへ閉じる。
		// api clientごとにラッパーを直接読むと、構造変更時の修正漏れが起きるため。
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_API_RESULT_UTILS_SCRIPT);

		assertNoToken(targetFiles, List.of("resultType", "messageList"));
	}

	@Test
	@DisplayName("Vue管理下のDOMを直接操作しない")
	void frontendCodeDoesNotUseDirectDomManipulation() throws IOException {
		assertNoToken(frontendVueFiles(),
				List.of("document.getElementById", "document.querySelector", "document.querySelectorAll",
						"document.getElementsByClassName", "document.getElementsByName",
						"document.getElementsByTagName", ".innerHTML", ".outerHTML", ".insertAdjacentHTML"));
	}

	@Test
	@DisplayName("window.openにnoopenerを付ける")
	void frontendWindowOpenUsesNoopener() throws IOException {
		assertNoWindowOpenWithoutNoopener(scriptFiles(FRONTEND_SOURCE));
	}

	@Test
	@DisplayName("alert / confirm / promptで操作を止めない")
	void frontendCodeDoesNotUseBlockingBrowserDialogs() throws IOException {
		assertNoToken(scriptFiles(FRONTEND_SOURCE), List.of("alert(", "confirm(", "prompt("));
	}

	@Test
	@DisplayName("フロントエンドで曖昧比較（==）を使わない")
	void frontendCodeDoesNotUseLooseEquality() throws IOException {
		assertNoPattern(scriptFiles(FRONTEND_SOURCE), LOOSE_JAVASCRIPT_EQUALITY, "== / !=");
	}

	@Test
	@DisplayName("Vue templateでinline styleを使わない")
	void frontendVueFilesDoNotUseInlineStyleAttributes() throws IOException {
		assertNoPattern(frontendVueFiles(), INLINE_STYLE_ATTRIBUTE, "inline style属性");
	}

	@Test
	@DisplayName("廃止したスタイルクラスを使わない")
	void frontendVueFilesDoNotUseDeprecatedStyleClasses() throws IOException {
		assertNoToken(frontendVueFiles(),
				List.of("class=\"card ", "class=\"card__", "class=\"card-skin", "class=\"button_box",
						"class=\"button_normal", "class=\"button_circle", "class=\"ECM_CheckboxInput",
						"class=\"selectbox", "class=\"modal__", "class=\"modal-overlay", "class=\"textbox\"",
						"class=\"textbox "));
	}

	@Test
	@DisplayName("操作ボタンはPicoの見た目クラスではなく役割別のbtn-*クラスを使う")
	void frontendButtonsUseRoleBasedStyleClasses() throws IOException {
		assertNoToken(frontendVueFiles(),
				List.of("class=\"outline\"", "class=\"outline ", "class=\"secondary\"", "class=\"secondary ",
						"class=\"contrast\"", "class=\"contrast "));
	}

	@Test
	@DisplayName("buttonにtype属性を明示する")
	void frontendButtonsDeclareTypeAttribute() throws IOException {
		assertNoPattern(frontendVueFiles(), BUTTON_WITHOUT_TYPE, "type属性なしbutton");
	}

	@Test
	@DisplayName("v-forに:keyを付ける")
	void frontendVForDeclaresKey() throws IOException {
		assertNoPattern(frontendVueFiles(), V_FOR_WITHOUT_KEY, "key属性なしv-for");
	}

	@Test
	@DisplayName("OpenPDFの参照とローカル固定PDFパスを復活させない")
	void openPdfAndLocalPdfPathDoNotReappear() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		targetFiles.add(PROJECT_ROOT.resolve("pom.xml"));
		targetFiles.addAll(javaFiles(MAIN_SOURCE));
		targetFiles.addAll(javaFiles(TEST_SOURCE));
		targetFiles.addAll(textFiles(TEST_RESOURCES));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("com.lowagie", "openpdf", "PdfReader", "PdfCopy", "PdfStamper", "PdfWriter",
				"/Users/example/Documents/pdfTools", "PATH_ORIGINAL_FILE_DIRECTORY"));
	}

	@Test
	@DisplayName("本番コードがOpenPDF packageへ依存しない")
	void productionCodeDoesNotDependOnOpenPdfPackages() {
		noClasses().should().dependOnClassesThat().resideInAPackage("com.lowagie..")
				.because("PDF処理はPDFBox 3.xへ移行済みのため、OpenPDFへ戻さない。").check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("Apache POIへの依存をofficecontent.logicへ閉じる")
	void apachePoiIsLimitedToOfficeContentLogic() {
		// Office読み取りライブラリ(POI)の依存はofficecontent.logicだけに閉じ込める。
		// `docs/future-document-ai-roadmap.md` の Phase G の注意書き「PDFロジックへ混ぜない」を機械的に守る。
		noClasses().that().resideOutsideOfPackage(BASE_PACKAGE + ".officecontent.logic..").should()
				.dependOnClassesThat().resideInAPackage("org.apache.poi..")
				.because("Office読み取りの依存はofficecontent.logicへ閉じ込めます。").check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("commons-csvへの依存をexportcontent.logicへ閉じる")
	void commonsCsvIsLimitedToExportContentLogic() {
		// CSV出力ライブラリ(commons-csv)の依存はexportcontent.logicだけに閉じ込める。
		// `docs/future-document-ai-roadmap.md` の Phase G の注意書き
		// 「CSV読み書き専用クラスを作り、PDFロジックへ混ぜない」を機械的に守る。
		noClasses().that().resideOutsideOfPackage(BASE_PACKAGE + ".exportcontent.logic..").should()
				.dependOnClassesThat().resideInAPackage("org.apache.commons.csv..")
				.because("CSV出力の依存はexportcontent.logicへ閉じ込めます。").check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("本番コードがopenCsv packageへ依存しない")
	void productionCodeDoesNotDependOnOpenCsvPackages() {
		// openCsvはcommons-beanutils経由でcommons-collections 3.xを引き込み、
		// 本プロジェクトが統一しているcommons-collections4と2系統が同居する。CSVはcommons-csvへ寄せる。
		noClasses().should().dependOnClassesThat().resideInAPackage("com.opencsv..")
				.because("CSV出力はcommons-csvへ統一し、commons-collections 3.xを引き込むopenCsvへ戻さない。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("CSV出力の依存方向をController → Service → Logicに保つ")
	void exportControllerServiceLogicDependenciesKeepDirection() {
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..exportcontent.controller..").layer("Service").definedBy("..exportcontent.service..")
				.layer("Logic").definedBy("..exportcontent.logic..").whereLayer("Controller")
				.mayNotBeAccessedByAnyLayer().whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
				.whereLayer("Logic").mayOnlyBeAccessedByLayers("Service")
				.because("CSV出力もController -> Service -> Logicの順に依存させます。").check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("Office処理の依存方向を保ち、共有のOffice読み書きだけ横断利用を許す")
	void officeControllerServiceLogicDependenciesKeepDirection() {
		// Office読み書き(officecontent.logic)は共有機能として、PDFからOffice文書を起こす
		// pdfcontent.serviceからも使う。POIの依存を1packageへ閉じるという判断を守るには、
		// 書き出し側もここに置くしかないため。この横断利用だけを許可し、他の依存方向は維持する。
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..officecontent.controller..").layer("Service").definedBy("..officecontent.service..")
				.layer("Logic").definedBy("..officecontent.logic..").optionalLayer("PdfOfficeService")
				.definedBy("..pdfcontent.service..").whereLayer("Controller").mayNotBeAccessedByAnyLayer()
				.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller").whereLayer("Logic")
				.mayOnlyBeAccessedByLayers("Service", "PdfOfficeService")
				.because("Office処理もController -> Service -> Logicの順に依存させ、共有のOffice読み書きのみpdfcontent.serviceからの利用を許可します。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("pdfcontent.enumsの区分値enumがCodeEnumを実装する")
	void pdfEnumsImplementCodeEnum() {
		classes().that().resideInAPackage("..pdfcontent.enums..").and().areEnums().should()
				.beAssignableTo(CodeEnum.class).because("区分値enumはkey/valueを持つCodeEnumで扱います。").check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("PDF処理の依存方向をController → Service → Logicに保つ")
	void pdfControllerServiceLogicDependenciesKeepDirection() {
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..pdfcontent.controller..").layer("Service").definedBy("..pdfcontent.service..")
				.layer("Logic").definedBy("..pdfcontent.logic..").whereLayer("Controller").mayNotBeAccessedByAnyLayer()
				.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller").whereLayer("Logic")
				.mayOnlyBeAccessedByLayers("Service").because("PDF処理はController -> Service -> Logicの順に依存させます。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("画像処理の依存方向を保ち、共有変換器だけ横断利用を許す")
	void imageControllerServiceLogicDependenciesKeepDirection() {
		// 画像変換器(imagecontent.logic)は共有機能として、画像PDFのAUTO下書きを行うpdfcontent.serviceからも使う。
		// この横断利用だけを許可し、それ以外のController/Serviceの依存方向は維持する。
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..imagecontent.controller..").layer("Service").definedBy("..imagecontent.service..")
				.layer("Logic").definedBy("..imagecontent.logic..").optionalLayer("PdfDraftService")
				.definedBy("..pdfcontent.service..").whereLayer("Controller").mayNotBeAccessedByAnyLayer()
				.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller").whereLayer("Logic")
				.mayOnlyBeAccessedByLayers("Service", "PdfDraftService")
				.because("画像Markdown下書きはController -> Service -> Logicの順に依存させ、共有の画像変換器のみpdfcontent.serviceからの利用を許可します。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("Markdown AI変換の依存方向をController → Service → Logicに保つ")
	void aiControllerServiceLogicDependenciesKeepDirection() {
		// 共有ロジック(MarkdownFenceUnwrapper)はimagecontent.logicと同居させず、common.utilsへ出した。
		// そのため画像処理と違い横断利用のoptionalLayerを持たず、pdfControllerServiceLogicDependenciesKeepDirection
		// と同じ単純な3層のみで固定できる。
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..aicontent.controller..").layer("Service").definedBy("..aicontent.service..")
				.layer("Logic").definedBy("..aicontent.logic..").whereLayer("Controller").mayNotBeAccessedByAnyLayer()
				.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller").whereLayer("Logic")
				.mayOnlyBeAccessedByLayers("Service")
				.because("Markdown AI変換もController -> Service -> Logicの順に依存させます。").check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("Webページ取り込みの依存方向をController → Service → Logicに保つ")
	void webControllerServiceLogicDependenciesKeepDirection() {
		// 表の組み立てとブロック連結(MarkdownTableBuilder / MarkdownBlockJoiner)はOffice側と共有するため
		// common.utilsへ出してある。そのためWeb取り込みは横断利用のoptionalLayerを持たず、3層のみで固定できる。
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..webcontent.controller..").layer("Service").definedBy("..webcontent.service..")
				.layer("Logic").definedBy("..webcontent.logic..").whereLayer("Controller")
				.mayNotBeAccessedByAnyLayer().whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
				.whereLayer("Logic").mayOnlyBeAccessedByLayers("Service")
				.because("Webページ取り込みもController -> Service -> Logicの順に依存させます。").check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("任意の宛先へのHTTP接続をWebPageFetcherへ閉じる")
	void httpClientIsLimitedToWebPageFetcher() throws IOException {
		// 利用者が指定した宛先へ接続できる経路はWebPageFetcherだけにする。入口が増えると、
		// どの経路がSSRF検査(WebAddressValidator)を通っているのかを追えなくなる。
		// AI provider向けのSDK(OpenAI/Anthropic)は接続先が固定のため、この規約の対象外。
		List<Path> targetFiles = new ArrayList<>(javaFiles(MAIN_SOURCE));
		targetFiles.remove(MAIN_SOURCE.resolve("com/clip/ghost/webcontent/logic/WebPageFetcher.java"));

		assertNoToken(targetFiles,
				List.of("java.net.http.HttpClient", "HttpClient.newBuilder", "HttpClient.newHttpClient"));
	}

	@Test
	@DisplayName("本番コードでJsoup.connectを使わない")
	void jsoupConnectIsNotUsedInProductionCode() throws IOException {
		// Jsoup.connectは名前解決・リダイレクト追跡・取得をライブラリの内側でまとめて行うため、
		// 接続先IPを検査する隙間が無い。取得はSSRF検査を挟めるHTTPクライアントに限り、
		// jsoupはHTMLの解析(Jsoup.parse)だけに使う。
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("Jsoup.connect"));
	}

	@Test
	@DisplayName("Markdown処理の依存方向を保ち、共有HTMLレンダラーだけ横断利用を許す")
	void markdownControllerServiceLogicDependenciesKeepDirection() {
		// Markdown -> HTML変換(markdowncontent.logic)は共有機能として、PDFからHTMLを起こす
		// pdfcontent.serviceとOffice文書をPDF化するofficecontent.serviceからも使う。
		// 変換規則を分けると、画面プレビューとダウンロード結果で同じMarkdownの見た目が食い違うため。
		// 保存済みMarkdown一覧のCSV出力(exportcontent.service)は、画面と同じ一覧を出すために
		// markdowncontent.serviceを使う。CSV専用の一覧取得を作ると画面と内容がずれる。
		// これらの横断利用だけを許可し、他の依存方向は維持する。
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..markdowncontent.controller..").layer("Service").definedBy("..markdowncontent.service..")
				.layer("Logic").definedBy("..markdowncontent.logic..").optionalLayer("PdfHtmlService")
				.definedBy("..pdfcontent.service..").optionalLayer("OfficePdfService")
				.definedBy("..officecontent.service..").optionalLayer("MarkdownCsvService")
				.definedBy("..exportcontent.service..").whereLayer("Controller").mayNotBeAccessedByAnyLayer()
				.whereLayer("Service")
				.mayOnlyBeAccessedByLayers("Controller", "MarkdownCsvService").whereLayer("Logic")
				.mayOnlyBeAccessedByLayers("Service", "PdfHtmlService", "OfficePdfService")
				.because("Markdown処理もController -> Service -> Logicの順に依存させ、共有のHTML/PDFレンダラーのみpdfcontent.service・officecontent.serviceからの利用を許可します。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
	@DisplayName("HTML/CSSレンダラーの利用をMarkdownPdfRendererへ閉じる")
	void htmlToPdfRendererIsLimitedToMarkdownPdfRenderer() throws IOException {
		// HTML/CSSレンダラー(openhtmltopdf)の依存はMarkdownPdfRendererだけに閉じ込める。
		// PDF出力の実装差し替え時に影響範囲が広がるのを防ぐ。
		List<Path> targetFiles = new ArrayList<>(javaFiles(MAIN_SOURCE));
		targetFiles.remove(MAIN_SOURCE.resolve("com/clip/ghost/markdowncontent/logic/MarkdownPdfRenderer.java"));

		assertNoToken(targetFiles, List.of("com.openhtmltopdf"));
	}

	@Test
	@DisplayName("外部プロセス起動をProcessCommandRunnerへ閉じる")
	void processExecutionIsLimitedToCommandRunner() throws IOException {
		// 外部プロセス起動はProcessCommandRunnerだけに限定する。他クラスへのProcessBuilder/exec混入を検出する。
		List<Path> targetFiles = new ArrayList<>(javaFiles(MAIN_SOURCE));
		targetFiles.remove(MAIN_SOURCE.resolve("com/clip/ghost/imagecontent/logic/ProcessCommandRunner.java"));

		assertNoToken(targetFiles, List.of("ProcessBuilder", "Runtime.getRuntime().exec"));
	}

	@Test
	@DisplayName("テストメソッドに@DisplayNameを付ける")
	void testMethodsDeclareDisplayName() throws IOException {
		// テスト名はレポートでそのまま読む説明文になる。method名だけでは、落ちたときに
		// 何を守っていたテストなのかが英語の逐語訳からしか分からない。
		// この規約テスト自身も対象にする。禁止トークンのスキャンと違い、定義を持つことが違反にならないため。
		List<String> violations = new ArrayList<>();
		for (Path javaFile : javaFiles(TEST_SOURCE)) {
			violations.addAll(findTestMethodsWithoutDisplayName(javaFile));
		}

		assertTrue(violations.isEmpty(), () -> "テストメソッドには@DisplayNameで日本語の説明を付けてください: " + violations);
	}

	@Test
	@DisplayName("service層のDTO生成をprivate build〇〇へ集約する")
	void serviceDtoCreationIsHiddenBehindBuildMethods() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path serviceFile : javaFiles(MAIN_SOURCE)) {
			if (!serviceFile.getFileName().toString().endsWith("Service.java")) {
				continue;
			}
			String currentMethodName = "";
			List<String> lines = Files.readAllLines(serviceFile, StandardCharsets.UTF_8);
			for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
				String line = lines.get(lineIndex);
				java.util.regex.Matcher matcher = METHOD_DECLARATION.matcher(line);
				if (matcher.matches()) {
					currentMethodName = matcher.group(1);
				}
				if (SERVICE_DTO_CREATION.matcher(line).find() && !currentMethodName.startsWith("build")) {
					violations.add(serviceFile + ":" + (lineIndex + 1) + " method=" + currentMethodName);
				}
			}
		}

		assertTrue(violations.isEmpty(), () -> "service層のDTO生成はprivate build〇〇メソッドへ集約してください: " + violations);
	}

	private static List<Path> javaFiles(Path root) throws IOException {
		if (!Files.exists(root)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".java"))
					.toList();
		}
	}

	private static List<Path> textFiles(Path root) throws IOException {
		if (!Files.exists(root)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).filter(path -> {
				String fileName = path.getFileName().toString();
				return fileName.endsWith(".txt") || fileName.endsWith(".json") || fileName.endsWith(".xml")
						|| fileName.endsWith(".properties") || fileName.endsWith(".csv");
			}).toList();
		}
	}

	private static List<Path> scriptFiles(Path root) throws IOException {
		if (!Files.exists(root)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".js"))
					.toList();
		}
	}

	private static List<Path> frontendVueFiles() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		if (Files.exists(FRONTEND_MAIN_TEMPLATE)) {
			targetFiles.add(FRONTEND_MAIN_TEMPLATE);
		}
		targetFiles.addAll(scriptFiles(FRONTEND_SOURCE));
		return targetFiles;
	}

	private static ArchCondition<JavaClass> callNoArgumentMethodOn(Class<?> ownerType, String... methodNames) {
		List<String> targetMethodNames = List.of(methodNames);
		String description = ownerType.getSimpleName() + "の" + String.join(" / ", targetMethodNames) + "を直接呼び出している";
		return new ArchCondition<>(description) {
			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
					if (targetMethodNames.contains(call.getTarget().getName())
							&& call.getTarget().getRawParameterTypes().isEmpty()
							&& call.getTargetOwner().isAssignableTo(ownerType)) {
						events.add(SimpleConditionEvent.satisfied(javaClass, call.getDescription()));
					}
				}
			}
		};
	}

	private static ArchCondition<JavaClass> callDeprecatedCommonsLang3Method() {
		return new ArchCondition<>("commons-lang3の非推奨メソッドを呼び出している") {
			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
					if (call.getTargetOwner().getPackageName().startsWith(COMMONS_LANG3_PACKAGE)
							&& isDeprecatedTarget(call)) {
						events.add(SimpleConditionEvent.satisfied(javaClass, call.getDescription()));
					}
				}
			}
		};
	}

	private static boolean isDeprecatedTarget(JavaMethodCall call) {
		return call.getTarget().resolveMember().map(method -> method.isAnnotatedWith(Deprecated.class)).orElse(false);
	}

	private static List<String> findTestMethodsWithoutDisplayName(Path javaFile) throws IOException {
		List<String> violations = new ArrayList<>();
		List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
		for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
			if (!TEST_ANNOTATION.matcher(lines.get(lineIndex).strip()).matches()) {
				continue;
			}
			if (!hasDisplayNameInAnnotationBlock(lines, lineIndex)) {
				violations.add(javaFile + ":" + (lineIndex + 1));
			}
		}
		return violations;
	}

	private static boolean hasDisplayNameInAnnotationBlock(List<String> lines, int testAnnotationIndex) {
		// @DisplayNameは@Testの前後どちらに書いてもよいため、連続するannotationの並び全体を見る。
		for (int lineIndex = testAnnotationIndex - 1; lineIndex >= 0
				&& Strings.CS.startsWith(lines.get(lineIndex).strip(), "@"); lineIndex--) {
			if (Strings.CS.startsWith(lines.get(lineIndex).strip(), DISPLAY_NAME_ANNOTATION)) {
				return true;
			}
		}
		for (int lineIndex = testAnnotationIndex + 1; lineIndex < lines.size()
				&& Strings.CS.startsWith(lines.get(lineIndex).strip(), "@"); lineIndex++) {
			if (Strings.CS.startsWith(lines.get(lineIndex).strip(), DISPLAY_NAME_ANNOTATION)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCodingConventionTest(Path javaFile) {
		return javaFile.endsWith(Paths.get("src/test/java/com/clip/ghost/architecture/CodingConventionTest.java"));
	}

	private static void assertNoToken(List<Path> files, List<String> prohibitedTokens) throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : files) {
			String source = Files.readString(file, StandardCharsets.UTF_8);
			for (String prohibitedToken : prohibitedTokens) {
				if (source.contains(prohibitedToken)) {
					violations.add(file + " contains " + prohibitedToken);
				}
			}
		}

		assertTrue(violations.isEmpty(), () -> "禁止トークンが見つかりました: " + violations);
	}

	private static boolean isPublicDeclarationThatNeedsJavadoc(String line) {
		return PUBLIC_TYPE_DECLARATION.matcher(line).matches()
				|| PUBLIC_OR_PROTECTED_METHOD_DECLARATION.matcher(line).matches();
	}

	private static boolean hasJavadocBeforeDeclaration(List<String> lines, int declarationLineIndex) {
		int previousLineIndex = declarationLineIndex - 1;
		while (previousLineIndex >= 0) {
			String previousLine = lines.get(previousLineIndex).trim();
			if (previousLine.isEmpty() || isAnnotationLine(previousLine)) {
				previousLineIndex--;
				continue;
			}
			return previousLine.endsWith("*/");
		}
		return false;
	}

	private static boolean isAnnotationLine(String line) {
		return line.startsWith("@") || line.contains("@");
	}

	private static boolean isFullyCommentedOutJavaFile(Path javaFile) throws IOException {
		List<String> lines = Files.readAllLines(javaFile, StandardCharsets.UTF_8);
		List<String> meaningfulLines = lines.stream().map(String::trim).filter(line -> !line.isEmpty()).toList();
		return !meaningfulLines.isEmpty() && meaningfulLines.stream().allMatch(line -> line.startsWith("//"));
	}

	private static void assertNoPattern(List<Path> files, Pattern prohibitedPattern, String description)
			throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : files) {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
				if (prohibitedPattern.matcher(lines.get(lineIndex)).find()) {
					violations.add(file + ":" + (lineIndex + 1));
				}
			}
		}

		assertTrue(violations.isEmpty(), () -> description + " が見つかりました: " + violations);
	}

	private static void assertNoWindowOpenWithoutNoopener(List<Path> files) throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : files) {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
				String line = lines.get(lineIndex);
				if (WINDOW_OPEN.matcher(line).find() && !line.contains("noopener")) {
					violations.add(file + ":" + (lineIndex + 1));
				}
			}
		}

		assertTrue(violations.isEmpty(), () -> "noopenerなしのwindow.openが見つかりました: " + violations);
	}
}
