package com.clip.ghost.imagecontent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 画像Markdown下書きのTesseract（ローカルOCR）providerに関する設定。
 * <p>
 * 既定は無効で、有効化した場合のみローカルのTesseractコマンドを実行する。外部送信は行わない。
 * オフラインや大量バッチ向けのproviderとして用いる。
 */
@ConfigurationProperties(prefix = "ghost.ocr.tesseract")
@Getter
@Setter
public class TesseractProperties {
	/** Tesseract providerの有効化フラグ。 */
	private boolean enabled = false;

	/** Tesseract実行ファイルのコマンドまたはフルパス。 */
	private String command = "tesseract";

	/** tessdataディレクトリ。指定時に {@code --tessdata-dir} を付ける。空なら付けない。 */
	private String tessdataDirectory = "";

	/** 認識言語。例: {@code jpn+eng}。設定値のみを使い、リクエストからは受け取らない。 */
	private String languages = "jpn+eng";

	/** ページセグメンテーションモード（{@code --psm}）。 */
	private int psm = 6;

	/** 単語間の空白を保持するか（{@code -c preserve_interword_spaces=1}）。 */
	private boolean preserveInterwordSpaces = true;

	/** 1回の実行のタイムアウト秒数。 */
	private int timeoutSeconds = 60;
}
