package com.clip.ghost.pdfcontent.dto;

import java.nio.file.Path;

/**
 * PDF化する画像1件分の、保存先と利用者から見たファイル名。
 * <p>
 * 一時ファイル名はUUIDで、利用者がアップロードしたファイル名とは無関係になる。
 * エラーメッセージにパスだけを載せると「どのファイルが読めなかったのか」が利用者に伝わらないため、
 * 元のファイル名を一緒に持ち回る。
 *
 * @param path     アップロード画像の一時保存先、またはローカル画像のパス
 * @param fileName 利用者が指定したファイル名
 */
public record PdfImageSource(Path path, String fileName) {
}
