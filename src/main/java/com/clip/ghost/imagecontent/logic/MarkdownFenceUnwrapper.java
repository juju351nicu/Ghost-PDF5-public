package com.clip.ghost.imagecontent.logic;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

/**
 * vision系の出力全体がコードフェンスで包まれている場合に、その外側フェンスだけを取り除くユーティリティ。
 * <p>
 * LLMは「Markdownで返す」という指示に対し、出力全体を ```markdown … ``` で包むことがある。そのままでは
 * プレビューでコードブロック扱いになり、表が表として描画されない。次の方針で外側フェンスだけを外す。
 * <ul>
 * <li>```markdown / ```md の包みは常に外す。</li>
 * <li>言語指定なしの ``` は、内側に他のフェンスが無い場合だけ外す。</li>
 * <li>```java などプログラミング言語指定のフェンスは、画像内のソースコードの正当な表現として残す。</li>
 * </ul>
 */
final class MarkdownFenceUnwrapper {
	private static final Pattern FENCE_OPEN = Pattern.compile("^```([A-Za-z0-9_+-]*)$");
	private static final String FENCE_MARKER = "```";

	private MarkdownFenceUnwrapper() {
	}

	/**
	 * 出力全体を包む外側のコードフェンスを取り除く。対象外の場合は入力をそのまま返す。
	 *
	 * @param markdown 変換結果のMarkdown
	 * @return 外側フェンスを外したMarkdown、または変更しない入力
	 */
	static String unwrap(String markdown) {
		if (StringUtils.isBlank(markdown)) {
			return markdown;
		}
		List<String> lines = markdown.strip().lines().toList();
		if (lines.size() < 2) {
			return markdown;
		}
		Matcher openMatcher = FENCE_OPEN.matcher(lines.get(0).strip());
		if (!openMatcher.matches() || !Strings.CS.equals(FENCE_MARKER, lines.get(lines.size() - 1).strip())) {
			return markdown;
		}
		String language = StringUtils.lowerCase(openMatcher.group(1));
		boolean wrapperLanguage = StringUtils.isEmpty(language) || Strings.CS.equalsAny(language, "markdown", "md");
		if (!wrapperLanguage) {
			// ```java などは画像内のソースコードの正当なフェンスとして残す。
			return markdown;
		}
		List<String> inner = lines.subList(1, lines.size() - 1);
		if (StringUtils.isEmpty(language) && containsFence(inner)) {
			// 言語指定なしで内側にもフェンスがある場合は、正当な内容の可能性があるため触らない。
			return markdown;
		}
		return String.join("\n", inner);
	}

	/**
	 * 行のいずれかがコードフェンス（```…）で始まるか判定する。
	 *
	 * @param lines 判定対象の行
	 * @return フェンス行を含む場合はtrue
	 */
	private static boolean containsFence(List<String> lines) {
		return lines.stream().map(String::strip).anyMatch(line -> Strings.CS.startsWith(line, FENCE_MARKER));
	}
}
