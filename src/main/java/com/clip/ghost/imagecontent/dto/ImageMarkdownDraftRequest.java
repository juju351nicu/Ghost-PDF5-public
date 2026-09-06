package com.clip.ghost.imagecontent.dto;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 画像Markdown下書きの生成元画像を受け取るリクエストフォーム。
 * <p>
 * 既存PDF下書きと同様にmultipartでファイルを1つ受け取る。文字起こしだけを目的とし、保存条件は持たない。
 */
@Schema(description = "画像Markdown下書きの生成元画像を受け取るmultipartフォーム。")
@Getter
@Setter
public class ImageMarkdownDraftRequest {
	/** Markdown下書きの生成元になる画像ファイル。 */
	@Schema(description = "Markdown下書きの生成元になる画像ファイル。", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
	@JsonProperty("imageFile")
	@NotNull(message = "ファイルを入れてください。")
	private MultipartFile imageFile;
}
