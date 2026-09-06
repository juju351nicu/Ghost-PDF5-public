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

export default {
  DELIMITER,
  REST_PATH,
};
