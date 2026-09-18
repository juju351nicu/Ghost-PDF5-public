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
  METADATA_PDF: "/metadataPdf",
  TEXT_PDF: "/textPdf",
  MARKDOWN_DRAFT_PDF: "/markdownDraftPdf",
  MARKDOWN_PDF: "/markdownPdf",
  MARKDOWN_DRAFT_IMAGE: "/markdownDraftImage",
  MARKDOWN_DRAFT_HTML: "/markdownDraftHtml",
  MARKDOWN_DRAFT_URL: "/markdownDraftUrl",
  EXTRACT_PDF: "/extractPdf",
  MERGE_PDF: "/mergePdf",
  SPLIT_PDF: "/splitPdf",
  ROTATE_PDF: "/rotatePdf",
  IMAGES_PDF: "/imagesPdf",
  PDF_FROM_IMAGES: "/pdfFromImages",
  HTML_PDF: "/htmlPdf",
  PDF_FROM_HTML: "/pdfFromHtml",
  MARKDOWN_DRAFT_OFFICE: "/markdownDraftOffice",
  PDF_FROM_OFFICE: "/pdfFromOffice",
  OFFICE_FROM_PDF: "/officeFromPdf",
  EPUB_PDF: "/epubPdf",
  PDF_FROM_EPUB: "/pdfFromEpub",
  THUMBNAILS_PDF: "/thumbnailsPdf",
  DELETE_PDF: "/deletePdf",
  INSERT_PDF: "/insertPdf",
  SEARCHABLE_PDF: "/searchablePdf",
  SAVE_MARKDOWN: "/saveMarkdown",
  MARKDOWN_FILES: "/markdownFiles",
  MARKDOWN_FILES_CSV: "/markdownFilesCsv",
  MARKDOWN_FILE: "/markdownFile",
  MARKDOWN_PREVIEW: "/markdownPreview",
  MARKDOWN_AI_TRANSFORM: "/markdownAiTransform",
};

/**
 * アップロードファイルサイズの上限。
 *
 * MAX_PDF_BYTESはBE側の `UploadConstants.MAX_UPLOAD_FILE_SIZE_BYTES` と同じ値を保つ。
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
