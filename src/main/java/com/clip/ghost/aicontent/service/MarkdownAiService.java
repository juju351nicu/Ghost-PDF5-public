package com.clip.ghost.aicontent.service;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.clip.ghost.aicontent.config.AiMarkdownProperties;
import com.clip.ghost.aicontent.dto.MarkdownAiTransformRequest;
import com.clip.ghost.aicontent.dto.MarkdownAiTransformResponse;
import com.clip.ghost.aicontent.enums.AiTaskType;
import com.clip.ghost.aicontent.exception.AiInputException;
import com.clip.ghost.aicontent.exception.AiUnavailableException;
import com.clip.ghost.aicontent.logic.MarkdownAiConverter;
import com.clip.ghost.aicontent.logic.MarkdownAiConverterResolver;
import com.clip.ghost.common.response.ApiResult;

import lombok.RequiredArgsConstructor;

/**
 * Markdown本文をAIで整形・要約するサービス。
 * <p>
 * 変換自体は {@link MarkdownAiConverter} へ委譲し、このクラスは有効性確認、入力文字数の上限チェック、
 * 変換結果の正規化、レスポンスDTOの組み立てを担当する。外部AIの実装詳細には依存しない。
 * 結果は自動保存せず、利用者が確認してから既存Markdown保存APIへ渡す導線に委ねる。
 */
@Service
@RequiredArgsConstructor
public class MarkdownAiService {
	private final MarkdownAiConverterResolver converterResolver;
	private final AiMarkdownProperties markdownProperties;

	/**
	 * Markdown本文をAIで整形・要約する。
	 *
	 * @param request 変換対象本文とタスクを含むリクエスト
	 * @return 変換結果を含むレスポンス
	 * @throws AiUnavailableException 機能が無効、またはAPIキー未設定の場合
	 * @throws AiInputException       入力文字数が上限を超えた場合
	 */
	public ResponseEntity<ApiResult<MarkdownAiTransformResponse>> transformMarkdown(MarkdownAiTransformRequest request) {
		Objects.requireNonNull(request, "request must not be null.");
		MarkdownAiConverter converter = converterResolver.resolve();
		if (!converter.isEnabled()) {
			throw new AiUnavailableException("Markdown本文のAI整形・要約機能は無効です。");
		}
		String content = request.getContent();
		validateInputLength(content);
		String markdown = normalizeMarkdown(converter.transform(content, request.getTask()));
		return ResponseEntity.ok(ApiResult.of(buildResponse(request.getTask(), content, markdown)));
	}

	/**
	 * 入力文字数が設定上限を超えていないか検証する。
	 * <p>
	 * 上限超過時は変換器を一度も呼ばずに例外を投げる。外部AIの課金が発生する前に止めるコストガードのため。
	 *
	 * @param content 変換対象のMarkdown本文
	 * @throws AiInputException 入力文字数が上限を超えた場合
	 */
	private void validateInputLength(String content) {
		int characterCount = StringUtils.length(content);
		int maxCharacters = markdownProperties.getMaxInputCharacters();
		if (characterCount > maxCharacters) {
			throw new AiInputException(characterCount, maxCharacters);
		}
	}

	/**
	 * 変換結果の改行を正規化する。CRLFとCRをLFへ統一し、末尾の空白文字を除去する。
	 *
	 * @param markdown 変換結果のMarkdown
	 * @return 正規化済みMarkdown
	 */
	private String normalizeMarkdown(String markdown) {
		return markdown.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
	}

	/**
	 * タスク、入力本文、変換結果からレスポンスDTOを生成する。
	 *
	 * @param task      実行した変換タスク
	 * @param content   入力Markdown本文
	 * @param markdown  正規化済みの変換結果Markdown
	 * @return レスポンスDTO
	 */
	private MarkdownAiTransformResponse buildResponse(AiTaskType task, String content, String markdown) {
		MarkdownAiTransformResponse response = new MarkdownAiTransformResponse();
		response.setTask(task);
		response.setMarkdown(markdown);
		response.setInputCharacterCount(StringUtils.length(content));
		response.setOutputCharacterCount(StringUtils.length(markdown));
		return response;
	}
}
