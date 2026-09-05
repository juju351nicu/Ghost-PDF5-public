const SAVE_FOLDER_STORAGE_KEY = "saveFolder";

/**
 * localStorageを安全に取得する。
 *
 * Safariのプライベートブラウズやブラウザ設定によっては、localStorage参照だけで
 * 例外になることがあるため、Storage APIへの入口をこの関数に集約する。
 *
 * @returns {Storage|null} 利用可能なlocalStorage。利用できない場合はnull
 */
const getLocalStorageObject = () => {
  try {
    if (typeof window === "undefined") {
      return null;
    }
    if (!("localStorage" in window) || window.localStorage === null) {
      return null;
    }
    return window.localStorage;
  } catch (error) {
    return null;
  }
};

/**
 * 値があるかどうか判定する。
 * リストの場合は空かどうかを判定する。
 *
 * @param {*} target 判定対象の値
 * @returns {boolean} null/undefined/空文字/空配列の場合はtrue
 */
const isEmpty = (target) => {
  return (
    target === null ||
    target === undefined ||
    ((typeof target === "string" || Array.isArray(target)) &&
      target.length <= 0)
  );
};
/**
 * ブラウザがHTML5ローカルストレージをサポートしているかどうかを判断する。
 *
 * @returns {boolean} localStorageが使用可能な場合はtrue
 */
const canUseLocalStorage = () => {
  return getLocalStorageObject() !== null;
};
/**
 * ローカルストレージから値を取得する。
 *
 * @param {string} key キー
 * @returns {string|null} 取得結果
 */
const getLocalStorage = (key) => {
  return getLocalStorageObject()?.getItem(key) ?? null;
};

/**
 * ローカルストレージに値を設定する。
 *
 * @param {string} key キー
 * @param {string} value 値
 */
const setLocalStorage = (key, value) => {
  getLocalStorageObject()?.setItem(key, value);
};

/**
 * 既存仕様で保存フォルダとして使っていたローカルストレージ値を削除する。
 */
const removeLocalStorage = () => {
  getLocalStorageObject()?.removeItem(SAVE_FOLDER_STORAGE_KEY);
};

/**
 * 指定したkeyに該当するcookieの値を取得する。
 *
 * @param {string} key 指定したkey
 * @returns {string} cookieの値。存在しない場合は空文字
 */
const getCookieValue = (key) => {
  const cookies = document.cookie.split(";");
  const foundCookie = cookies.find(
    (cookie) => cookie.split("=")[0].trim() === key.trim()
  );
  if (foundCookie) {
    const cookieValue = decodeURIComponent(foundCookie.split("=")[1]);
    return cookieValue;
  }
  return "";
};
/**
 * userAgentからブラウザ名を判定する。
 *
 * EdgeやOperaなど、userAgentにChromeを含むブラウザを先に判定し、
 * Chromeとして誤分類しないようにする。
 *
 * @param {string} [userAgent] 判定対象のuserAgent。省略時は現在のブラウザを参照する
 * @returns {string} 判定したブラウザ名
 */
const detectBrowserName = (userAgent) => {
  try {
    const rawUserAgent = userAgent ?? window.navigator.userAgent;
    const normalizedUserAgent = rawUserAgent.toLowerCase();
    const isInternetExplorer =
      normalizedUserAgent.includes("msie") ||
      normalizedUserAgent.includes("trident");
    const isEdge =
      normalizedUserAgent.includes("edge/") ||
      normalizedUserAgent.includes("edg/") ||
      normalizedUserAgent.includes("edgios/") ||
      normalizedUserAgent.includes("edga/");
    const isOpera =
      normalizedUserAgent.includes("opera") ||
      normalizedUserAgent.includes("opr/") ||
      normalizedUserAgent.includes("opios/");
    const isFirefox =
      normalizedUserAgent.includes("firefox/") ||
      normalizedUserAgent.includes("fxios/");
    const isSamsungInternet = normalizedUserAgent.includes("samsungbrowser/");
    const isChromium = normalizedUserAgent.includes("chromium/");
    const isChrome =
      normalizedUserAgent.includes("chrome/") ||
      normalizedUserAgent.includes("crios/") ||
      isChromium;
    const isSafari =
      normalizedUserAgent.includes("safari/") &&
      !isChrome &&
      !isEdge &&
      !isOpera &&
      !isFirefox &&
      !isSamsungInternet;

    if (isInternetExplorer) {
      return "Internet Explorer";
    } else if (isEdge) {
      return "Edge";
    } else if (isOpera) {
      return "Opera";
    } else if (isFirefox) {
      return "FireFox";
    } else if (isSamsungInternet) {
      return "Samsung Internet";
    } else if (isChromium) {
      return "Chromium";
    } else if (isChrome) {
      return "Google Chrome";
    } else if (isSafari) {
      return "Safari";
    }
  } catch (error) {
    return "Unknown";
  }
  return "Unknown";
};

/**
 * @deprecated 新規コードでは canUseLocalStorage を使用する。
 * @returns {boolean} localStorageが使用可能な場合はtrue
 */
const isLocalStorage = canUseLocalStorage;

/**
 * @deprecated 新規コードでは detectBrowserName を使用する。
 * @returns {string} 判定したブラウザ名
 */
const checkBrowser = detectBrowserName;

/**
 * 値が正規表現とマッチしているかどうかをチェックする。
 *
 * 空文字は既存仕様としてtrueを返す。
 *
 * @param {string} target チェックする値
 * @param {RegExp} pattern 正規表現
 * @returns {boolean} 空文字または正規表現に一致した場合はtrue
 */
const checkByRegEx = (target, pattern) => {
  if (target === "") {
    return true;
  }
  return pattern.test(target);
};
/**
 * 文字列の中にあるスペースを削除して返却する。
 *
 * @param {string} target 文字列
 * @returns {string} 変換した文字列
 */
const trimSpace = (target) => {
  return target.replace(/\s*/g, "");
};

/**
 * 全角英数字を半角に変換する。
 *
 * @param {string} target 文字列
 * @returns {string} 変換した文字列
 */
const toHalfWidth = (target) => {
  const convertedText = target.replace(/[Ａ-Ｚａ-ｚ０-９]/g, (s) => {
    return String.fromCharCode(s.charCodeAt(0) - 0xfee0);
  });
  return convertedText;
};

/**
 * 引数に指定した複数の値の中から最大の値を 1 つ戻り値として返します。
 *
 * @param {number} a 数値a
 * @param {number} b 数値b
 * @returns {number} 最大の値
 */
const aryMax = (a, b) => {
  return Math.max(a, b);
};

/**
 * 引数に指定した複数の値の中から最小の値を 1 つ戻り値として返します。
 *
 * @param {number} a 数値a
 * @param {number} b 数値b
 * @returns {number} 最小の値
 */
const aryMin = (a, b) => {
  return Math.min(a, b);
};

/**
 * ページ番号が1以上の整数文字列かどうかチェックする。
 *
 * @param {string} pageText 差し替えページ番号
 * @returns {boolean} 1以上の整数文字列の場合はtrue
 */
const checkNumericPage = (pageText) => {
  if (isEmpty(pageText)) {
    return false;
  }
  // BEのinsertPageは1以上の整数のため、parseIntで許容される"1abc"などは弾く。
  return /^[1-9][0-9]*$/.test(pageText);
};
/**
 * 配列の重複を無くす。
 *
 * @param {Array<*>} array 配列
 * @returns {Array<*>} 重複を無くした配列
 */
const uniqArrayBySet = (array) => {
  return Array.from(new Set(array));
};

/**
 * 改行、水平タブを削除する。
 *
 * @param {string} target 文字列
 * @returns {string|null} 改行等を削除した文字列。空値の場合はnull
 */
const replaceBlank = (target) => {
  let dest = null;
  if (!isEmpty(target)) {
    dest = target.replace(/\\s*|\t|\r|\n/, "");
  }
  return dest;
};
/**
 * 文字列から、スペース、改行等を削除しリスト型にして返却する。
 *
 * @param {string} target 文字列
 * @param {string} delimiter 区切り文字
 * @returns {string[]} 文字列リスト
 */
const getStrPageList = (target, delimiter) => {
  let strPageList = [];
  if (!isEmpty(target)) {
    strPageList = replaceBlank(target).toLowerCase().split(delimiter);
    return strPageList;
  }
  return strPageList;
};

export default {
  isEmpty,
  getLocalStorageObject,
  canUseLocalStorage,
  isLocalStorage,
  getLocalStorage,
  setLocalStorage,
  removeLocalStorage,
  getCookieValue,
  detectBrowserName,
  checkBrowser,
  checkByRegEx,
  trimSpace,
  toHalfWidth,
  aryMax,
  aryMin,
  checkNumericPage,
  getStrPageList,
  uniqArrayBySet,
  replaceBlank,
};
