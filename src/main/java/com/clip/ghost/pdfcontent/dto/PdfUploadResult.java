package com.clip.ghost.pdfcontent.dto;

import java.nio.file.Path;

/**
 * アップロードされたPDFを一時保存した結果を表す内部DTO。
 * <p>
 * パスワードで保護されたPDFは、保護を外した一時ファイルを作ってから以降の処理へ渡す。そのため
 * 保存先のPDFを読んでも、利用者がアップロードしたファイルが保護されていたかどうかは分からなくなる。
 * PDF情報の「暗号化」表示は利用者が渡したファイルについての情報なので、保存時点の事実をここで持ち回る。
 * <p>
 * このDTOはService/Logic間の受け渡し用で、JSONレスポンスには使わない。
 *
 * @param path              一時保存先のパス。保護を外した場合はその保存先
 * @param passwordProtected アップロードされたPDFがパスワードで保護されていた場合true
 */
public record PdfUploadResult(Path path, boolean passwordProtected) {
}
