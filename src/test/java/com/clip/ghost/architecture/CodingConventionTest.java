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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.clip.ghost.pdfcontent.enums.CodeEnum;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
	private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE_PACKAGE);
	private static final Pattern SERVICE_DTO_CREATION = Pattern.compile("new\\s+\\w*Dto\\s*\\(");
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
	void productionCodeDoesNotUseLombokData() throws IOException {
		// lombok.DataはSOURCE retentionのため、コンパイル後のクラスを見るArchUnitでは検出できない。
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("import lombok.Data", "@Data"));
	}

	@Test
	void productionCodeDoesNotUseAutowiredFieldInjection() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE),
				List.of("import org.springframework.beans.factory.annotation.Autowired", "@Autowired"));
	}

	@Test
	void productionCodeDoesNotUseConsoleOutput() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("System.out", "printStackTrace("));
	}

	@Test
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
	void sourceJavadocsDoNotUseAuthorTags() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		targetFiles.addAll(javaFiles(MAIN_SOURCE));
		targetFiles.addAll(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("@author"));
	}

	@Test
	void productionUtilityClassesAreFinal() {
		classes().that().resideInAPackage(BASE_PACKAGE + ".common.utils..").should().haveModifier(JavaModifier.FINAL)
				.check(PRODUCTION_CLASSES);
	}

	@Test
	void productionCodeDoesNotUseDeletedStorageUtils() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("StorageUtils"));
	}

	@Test
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
	void multipartFileFieldsDoNotUseOptional() throws IOException {
		assertNoToken(javaFiles(MAIN_SOURCE), List.of("Optional<MultipartFile>"));
	}

	@Test
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
	void storageUtilsTypoMethodNamesDoNotReappear() throws IOException {
		List<Path> targetFiles = new ArrayList<>();
		targetFiles.addAll(javaFiles(MAIN_SOURCE));
		targetFiles.addAll(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("getExtention", "meargePathAndFileName"));
	}

	@Test
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
	void testCodeDoesNotUseDeprecatedSpringBootMockBean() throws IOException {
		List<Path> targetFiles = new ArrayList<>(javaFiles(TEST_SOURCE));
		// このテスト自身は検出対象の禁止トークンを定義として持つため、スキャン対象から外す。
		targetFiles.remove(TEST_SOURCE.resolve("com/clip/ghost/architecture/CodingConventionTest.java"));

		assertNoToken(targetFiles, List.of("org.springframework.boot.test.mock.mockito.MockBean", "@MockBean"));
	}

	@Test
	void springdocEndpointsAreDisabledByDefault() throws IOException {
		String applicationYaml = Files.readString(MAIN_APPLICATION_YAML, StandardCharsets.UTF_8);

		assertTrue(applicationYaml.contains("api-docs:\n    enabled: false"),
				"OpenAPI JSONは通常起動で公開しないため、springdoc.api-docs.enabled=falseを明示してください。");
		assertTrue(applicationYaml.contains("swagger-ui:\n    enabled: false"),
				"Swagger UIは通常起動で公開しないため、springdoc.swagger-ui.enabled=falseを明示してください。");
	}

	@Test
	void frontendCodeDoesNotUseDebugOutput() throws IOException {
		assertNoToken(scriptFiles(FRONTEND_SOURCE),
				List.of("console.log", "console.error", "console.warn", "console.info", "console.debug", "debugger"));
	}

	@Test
	void frontendCodeDoesNotAccessBrowserStorageDirectlyOutsideUtil() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_UTIL_SCRIPT);

		assertNoToken(targetFiles, List.of("localStorage", "sessionStorage"));
	}

	@Test
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
	void frontendCodeDoesNotUseDeprecatedUtilityAliasesOutsideUtil() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_UTIL_SCRIPT);

		assertNoToken(targetFiles, List.of("isLocalStorage", "checkBrowser"));
	}

	@Test
	void frontendCodeDoesNotBypassFetchClient() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_FETCH_CLIENT_SCRIPT);

		assertNoToken(targetFiles, List.of("fetch(", "new FormData", "new Headers"));
	}

	@Test
	void frontendCodeUsesApiErrorUtilsForFieldErrors() throws IOException {
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_API_ERROR_UTILS_SCRIPT);

		assertNoToken(targetFiles, List.of("fieldErrors"));
	}

	@Test
	void frontendCodeUsesApiResultUtilsForSuccessEnvelope() throws IOException {
		// JSON成功レスポンスの共通ラッパー(resultType / messageList)の解釈をapi-result-utils.jsへ閉じる。
		// api clientごとにラッパーを直接読むと、構造変更時の修正漏れが起きるため。
		List<Path> targetFiles = new ArrayList<>(scriptFiles(FRONTEND_SOURCE));
		targetFiles.remove(FRONTEND_API_RESULT_UTILS_SCRIPT);

		assertNoToken(targetFiles, List.of("resultType", "messageList"));
	}

	@Test
	void frontendCodeDoesNotUseDirectDomManipulation() throws IOException {
		assertNoToken(frontendVueFiles(),
				List.of("document.getElementById", "document.querySelector", "document.querySelectorAll",
						"document.getElementsByClassName", "document.getElementsByName",
						"document.getElementsByTagName", ".innerHTML", ".outerHTML", ".insertAdjacentHTML"));
	}

	@Test
	void frontendWindowOpenUsesNoopener() throws IOException {
		assertNoWindowOpenWithoutNoopener(scriptFiles(FRONTEND_SOURCE));
	}

	@Test
	void frontendCodeDoesNotUseBlockingBrowserDialogs() throws IOException {
		assertNoToken(scriptFiles(FRONTEND_SOURCE), List.of("alert(", "confirm(", "prompt("));
	}

	@Test
	void frontendCodeDoesNotUseLooseEquality() throws IOException {
		assertNoPattern(scriptFiles(FRONTEND_SOURCE), LOOSE_JAVASCRIPT_EQUALITY, "== / !=");
	}

	@Test
	void frontendVueFilesDoNotUseInlineStyleAttributes() throws IOException {
		assertNoPattern(frontendVueFiles(), INLINE_STYLE_ATTRIBUTE, "inline style属性");
	}

	@Test
	void frontendVueFilesDoNotUseDeprecatedStyleClasses() throws IOException {
		assertNoToken(frontendVueFiles(),
				List.of("class=\"card ", "class=\"card__", "class=\"card-skin", "class=\"button_box",
						"class=\"button_normal", "class=\"button_circle", "class=\"ECM_CheckboxInput",
						"class=\"selectbox", "class=\"modal__", "class=\"modal-overlay", "class=\"textbox\"",
						"class=\"textbox "));
	}

	@Test
	void frontendButtonsDeclareTypeAttribute() throws IOException {
		assertNoPattern(frontendVueFiles(), BUTTON_WITHOUT_TYPE, "type属性なしbutton");
	}

	@Test
	void frontendVForDeclaresKey() throws IOException {
		assertNoPattern(frontendVueFiles(), V_FOR_WITHOUT_KEY, "key属性なしv-for");
	}

	@Test
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
	void productionCodeDoesNotDependOnOpenPdfPackages() {
		noClasses().should().dependOnClassesThat().resideInAPackage("com.lowagie..")
				.because("PDF処理はPDFBox 3.xへ移行済みのため、OpenPDFへ戻さない。").check(PRODUCTION_CLASSES);
	}

	@Test
	void pdfEnumsImplementCodeEnum() {
		classes().that().resideInAPackage("..pdfcontent.enums..").and().areEnums().should()
				.beAssignableTo(CodeEnum.class).because("区分値enumはkey/valueを持つCodeEnumで扱います。").check(PRODUCTION_CLASSES);
	}

	@Test
	void pdfControllerServiceLogicDependenciesKeepDirection() {
		Architectures.layeredArchitecture().consideringAllDependencies().layer("Controller")
				.definedBy("..pdfcontent.controller..").layer("Service").definedBy("..pdfcontent.service..")
				.layer("Logic").definedBy("..pdfcontent.logic..").whereLayer("Controller").mayNotBeAccessedByAnyLayer()
				.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller").whereLayer("Logic")
				.mayOnlyBeAccessedByLayers("Service").because("PDF処理はController -> Service -> Logicの順に依存させます。")
				.check(PRODUCTION_CLASSES);
	}

	@Test
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
	void processExecutionIsLimitedToCommandRunner() throws IOException {
		// 外部プロセス起動はProcessCommandRunnerだけに限定する。他クラスへのProcessBuilder/exec混入を検出する。
		List<Path> targetFiles = new ArrayList<>(javaFiles(MAIN_SOURCE));
		targetFiles.remove(MAIN_SOURCE.resolve("com/clip/ghost/imagecontent/logic/ProcessCommandRunner.java"));

		assertNoToken(targetFiles, List.of("ProcessBuilder", "Runtime.getRuntime().exec"));
	}

	@Test
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
