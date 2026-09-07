import Util from "../util.js";
import CONST from "../const.js";

/**
 * 削除ページ入力の解析結果。
 *
 * pagesはBEへ送信する1始まりのページ番号リスト。messageが空文字以外の場合は、
 * 画面へ表示する入力エラーとして扱い、API送信は行わない。
 *
 * @typedef {Object} DeletePagesParseResult
 * @property {number[]} pages 重複を除いた削除対象ページ番号リスト
 * @property {string} message 入力エラーメッセージ。正常時は空文字
 */

/** ページ範囲の形式不備や開始ページが終了ページ以上の場合のメッセージ。 */
const INVALID_PAGE_MESSAGE = "適切な値を入力してください。";
/** ページ番号が1以上の整数として扱えない場合のメッセージ。 */
const NON_NUMERIC_PAGE_MESSAGE = "整数の値を入力してください。";
/** 分割範囲の形式が正しくない場合のメッセージ。 */
const INVALID_SPLIT_RANGE_MESSAGE =
  "分割範囲は「1-5, 6-12」の形式で入力してください。";
/** 1以上の整数だけを許可する。 */
const POSITIVE_INTEGER_PATTERN = /^[1-9][0-9]*$/;
/** `1-3` のような昇順ページ範囲候補を判定する。大小関係はparse時に検証する。 */
const PAGE_RANGE_PATTERN = /^[1-9][0-9]*-[1-9][0-9]*$/;

/**
 * 削除ページ入力がページ番号またはページ範囲として扱えるか判定する。
 *
 * @param {string} pagesText 削除ページ入力。例: "1, 3-4"
 * @returns {boolean} 入力が空ではなく、すべてのページ指定が有効な場合はtrue
 */
const isValidDeletePagesText = (pagesText) => {
  if (Util.isEmpty(pagesText)) {
    return false;
  }
  const pageItems = Util.trimSpace(pagesText).split(CONST.DELIMITER.COMMA);
  for (const pageItem of pageItems) {
    if (Util.isEmpty(pageItem)) {
      continue;
    }
    if (
      !Util.checkByRegEx(pageItem, POSITIVE_INTEGER_PATTERN) &&
      !Util.checkByRegEx(pageItem, PAGE_RANGE_PATTERN)
    ) {
      return false;
    }
  }
  return true;
};

/**
 * 削除ページ入力をAPI送信用のページ番号リストに変換する。
 *
 * @param {string} pagesText 削除ページ入力。例: "1, 3-5"
 * @returns {DeletePagesParseResult} 変換後ページ番号リストとエラーメッセージ
 */
const parseDeletePagesText = (pagesText) => {
  const numericPageList = [];
  for (const pageItem of Util.getStrPageList(pagesText, CONST.DELIMITER.COMMA)) {
    if (Util.isEmpty(pageItem)) {
      continue;
    }
    if (pageItem.includes(CONST.DELIMITER.HYPHEN)) {
      const rangeParts = pageItem.split(CONST.DELIMITER.HYPHEN);
      if (Util.isEmpty(rangeParts[0]) || Util.isEmpty(rangeParts[1])) {
        return { pages: numericPageList, message: INVALID_PAGE_MESSAGE };
      }
      if (
        !POSITIVE_INTEGER_PATTERN.test(rangeParts[0]) ||
        !POSITIVE_INTEGER_PATTERN.test(rangeParts[1])
      ) {
        return { pages: numericPageList, message: NON_NUMERIC_PAGE_MESSAGE };
      }
      const startPage = Number.parseInt(rangeParts[0], 10);
      const endPage = Number.parseInt(rangeParts[1], 10);
      if (startPage >= endPage) {
        return { pages: numericPageList, message: INVALID_PAGE_MESSAGE };
      }
      for (let pageNumber = startPage; pageNumber <= endPage; pageNumber++) {
        numericPageList.push(pageNumber);
      }
      continue;
    }
    if (!POSITIVE_INTEGER_PATTERN.test(pageItem)) {
      return { pages: numericPageList, message: NON_NUMERIC_PAGE_MESSAGE };
    }
    numericPageList.push(Number.parseInt(pageItem, 10));
  }
  return { pages: Util.uniqArrayBySet(numericPageList), message: "" };
};

/**
 * 分割範囲入力の解析結果。
 *
 * rangesはBEへ送信する `"1-5"` 形式の範囲文字列リスト。空配列は「範囲指定なし（1ページずつ分割）」を表す。
 * messageが空文字以外の場合は入力エラーとして扱い、API送信は行わない。
 *
 * @typedef {Object} SplitRangesParseResult
 * @property {string[]} ranges 入力順を保った分割範囲リスト
 * @property {string} message 入力エラーメッセージ。正常時は空文字
 */

/**
 * 1件の分割範囲が `5` または `5-12` として扱えるか判定する。
 *
 * 削除ページ入力と違い、開始と終了が同じ範囲（`3-3`）も許可する。1ページだけを1ファイルにする
 * 指定として意味があり、BE側の検証も同じ条件にしている。
 *
 * @param {string} rangeItem 分割範囲1件。例: "1-5"
 * @returns {boolean} 分割範囲として扱える場合はtrue
 */
const isValidSplitRangeItem = (rangeItem) => {
  if (POSITIVE_INTEGER_PATTERN.test(rangeItem)) {
    return true;
  }
  if (!PAGE_RANGE_PATTERN.test(rangeItem)) {
    return false;
  }
  const rangeParts = rangeItem.split(CONST.DELIMITER.HYPHEN);
  return (
    Number.parseInt(rangeParts[0], 10) <= Number.parseInt(rangeParts[1], 10)
  );
};

/**
 * 分割範囲入力が形式として正しいか判定する。
 *
 * 空欄は「1ページずつ分割」を表す正常な入力として扱う。
 *
 * @param {string} rangesText 分割範囲入力。例: "1-5, 6-12"
 * @returns {boolean} 空欄、またはすべての範囲が正しい形式の場合はtrue
 */
const isValidSplitRangesText = (rangesText) => {
  return parseSplitRangesText(rangesText).message === "";
};

/**
 * 分割範囲入力をAPI送信用の範囲文字列リストへ変換する。
 *
 * ページ数との突き合わせ（総ページ数を超えていないか）と範囲の重複判定はBEに任せる。
 * 総ページ数はPDFを開くまで分からず、画面側で判定すると二重管理になるため。
 *
 * @param {string} rangesText 分割範囲入力。例: "1-5, 6-12"
 * @returns {SplitRangesParseResult} 変換後の範囲リストとエラーメッセージ
 */
const parseSplitRangesText = (rangesText) => {
  if (Util.isEmpty(rangesText)) {
    return { ranges: [], message: "" };
  }
  const splitRanges = [];
  const rangeItems = Util.trimSpace(rangesText).split(CONST.DELIMITER.COMMA);
  for (const rangeItem of rangeItems) {
    if (Util.isEmpty(rangeItem)) {
      continue;
    }
    if (!isValidSplitRangeItem(rangeItem)) {
      return { ranges: [], message: INVALID_SPLIT_RANGE_MESSAGE };
    }
    splitRanges.push(rangeItem);
  }
  return { ranges: splitRanges, message: "" };
};

/**
 * 差し込みページ番号が1つの数値として扱えるか判定する。
 *
 * @param {string} pageText 差し込みページ番号入力
 * @returns {boolean} 数値として扱える場合はtrue
 */
const isValidInsertPageText = (pageText) => {
  return Util.checkNumericPage(pageText);
};

export default {
  isValidDeletePagesText,
  parseDeletePagesText,
  isValidSplitRangesText,
  parseSplitRangesText,
  isValidInsertPageText,
};
