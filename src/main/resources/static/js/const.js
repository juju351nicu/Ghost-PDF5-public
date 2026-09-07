/**
 * 区切り文字の定数。
 */
const DELIMITER = {
  COMMA: ",",
  HYPHEN: "-",
};

/**
 * PDF / Markdown操作APIのURL。
 */
const REST_PATH = {
  SHOW_PDF: "/showPdf",
  METADATA_PDF: "/metadataPdf",
  TEXT_PDF: "/textPdf",
  MARKDOWN_DRAFT_PDF: "/markdownDraftPdf",
  MARKDOWN_DRAFT_IMAGE: "/markdownDraftImage",
  EXTRACT_PDF: "/extractPdf",
  MERGE_PDF: "/mergePdf",
  SPLIT_PDF: "/splitPdf",
  DELETE_PDF: "/deletePdf",
  INSERT_PDF: "/insertPdf",
  SAVE_MARKDOWN: "/saveMarkdown",
  MARKDOWN_FILES: "/markdownFiles",
  MARKDOWN_FILE: "/markdownFile",
  MARKDOWN_PREVIEW: "/markdownPreview",
};

/**
 * アップロードファイルサイズの上限。
 *
 * MAX_PDF_BYTESはBE側の `PdfConstants.MAX_PDF_FILE_SIZE_BYTES` と同じ値を保つ。
 * 値がずれるとFEを通り抜けたリクエストがサーバー側で拒否されるため、
 * `FrontendUploadSizeContractTest` が一致を検証する。
 */
const FILE_SIZE = {
  MAX_PDF_BYTES: 20971520,
};

export default {
  DELIMITER,
  REST_PATH,
  FILE_SIZE,
};
