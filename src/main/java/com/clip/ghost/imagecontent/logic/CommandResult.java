package com.clip.ghost.imagecontent.logic;

/**
 * 外部コマンド実行結果を表す内部DTO。
 *
 * @param exitCode 終了コード
 * @param output   標準出力の内容
 */
public record CommandResult(int exitCode, String output) {
}
