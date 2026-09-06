package com.clip.ghost.imagecontent.logic;

import java.util.List;

/**
 * 外部コマンドを実行する抽象。
 * <p>
 * プロセス起動の詳細を実装へ閉じ込め、変換器はコマンド引数の組み立てと結果の解釈に専念できるようにする。
 * 単体テストではこのinterfaceをmockし、実プロセスを起動せずに引数と結果処理を検証する。
 */
public interface CommandRunner {
	/**
	 * 外部コマンドを実行し、終了コードと標準出力を返す。
	 *
	 * @param command        実行するコマンドと引数。シェルを介さずそのままプロセスへ渡す
	 * @param timeoutSeconds タイムアウト秒数
	 * @return 実行結果
	 */
	CommandResult run(List<String> command, long timeoutSeconds);
}
