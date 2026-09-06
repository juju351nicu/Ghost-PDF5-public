/**
 * 画像Markdown下書きAPI用のmultipart payloadを生成する。
 *
 * @param {File} imageFile 文字起こし対象の画像
 * @returns {{key: string, value: unknown}[]} multipart payload
 */
const buildImageDraftPayload = (imageFile) => {
  return [{ key: "imageFile", value: imageFile }];
};

export default {
  buildImageDraftPayload,
};
