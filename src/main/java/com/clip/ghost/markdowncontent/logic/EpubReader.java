package com.clip.ghost.markdowncontent.logic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;

import lombok.NoArgsConstructor;

/**
 * EPUBを読み、本文をHTMLとして取り出す内部ロジック。
 * <p>
 * EPUBは「ZIP + {@code META-INF/container.xml} + OPF + XHTML」でできている。専用ライブラリは入れない。
 * Javaで保守されているEPUBライブラリは更新が止まっているものが多く、
 * 必要な操作がZIP（標準API）とXMLの読み取り（導入済みのjsoup）だけで足りるため。
 * <p>
 * 本文はspine（読む順序）に従って連結する。ZIPのエントリ順やファイル名順で並べると、
 * 章立てが崩れて別の文書になる。
 * <p>
 * 画像・CSS・フォントは取り込まない。PDF描画側が外部リソースを解決しない方針のため、
 * 取り込んでも描画されず、ZIPを展開する分だけ重くなる。
 */
@Component
@NoArgsConstructor
public class EpubReader {
	private static final Logger LOGGER = LoggerFactory.getLogger(EpubReader.class);
	private static final String CONTAINER_ENTRY_NAME = "META-INF/container.xml";
	private static final String XHTML_MEDIA_TYPE = "application/xhtml+xml";
	private static final String PATH_SEPARATOR = "/";
	private static final long MAX_ENTRY_BYTES = 20_971_520L;

	/**
	 * EPUBの本文をspine順に連結したHTMLとして取り出す。
	 *
	 * @param epubBytes EPUBのbyte配列
	 * @return 連結した本文HTML
	 * @throws MarkdownPdfException EPUBとして読めない場合
	 */
	public String readBodyHtml(byte[] epubBytes) {
		Map<String, byte[]> entries = readEntries(epubBytes);
		String opfPath = resolveOpfPath(entries);
		Document opf = parseXml(entries.get(opfPath), opfPath);
		String opfDirectory = resolveDirectory(opfPath);
		List<String> spineHrefs = resolveSpineHrefs(opf);
		if (CollectionUtils.isEmpty(spineHrefs)) {
			throw new MarkdownPdfException("EPUBに読み取れる本文がありません。");
		}
		List<String> bodies = new ArrayList<>(spineHrefs.size());
		for (String href : spineHrefs) {
			appendBody(bodies, entries, opfDirectory + href);
		}
		LOGGER.info("EPUBの本文を読み取りました。documentCount={}", bodies.size());
		return String.join("\n", bodies);
	}

	/**
	 * ZIPのエントリ名と内容をすべて読み出す。
	 *
	 * @param epubBytes EPUBのbyte配列
	 * @return エントリ名をキーにした内容のMap
	 * @throws MarkdownPdfException ZIPとして読めない場合
	 */
	private Map<String, byte[]> readEntries(byte[] epubBytes) {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(epubBytes),
				StandardCharsets.UTF_8)) {
			ZipEntry entry = zipInputStream.getNextEntry();
			while (entry != null) {
				readEntry(entries, zipInputStream, entry);
				entry = zipInputStream.getNextEntry();
			}
		} catch (IOException | IllegalArgumentException e) {
			throw new MarkdownPdfException("EPUBとして読み込めないファイルです。", e);
		}
		return entries;
	}

	/**
	 * ZIPの1エントリを読み出す。
	 * <p>
	 * ディレクトリと、1件あたりの上限を超えるエントリは読み飛ばす。上限を設けるのは、
	 * 展開後サイズが極端に大きいZIP（zip bomb）でヒープを使い切らないため。
	 *
	 * @param entries        読み出し先のMap
	 * @param zipInputStream ZIPの入力ストリーム
	 * @param entry          読み出すエントリ
	 * @throws IOException エントリを読み込めない場合
	 */
	private void readEntry(Map<String, byte[]> entries, ZipInputStream zipInputStream, ZipEntry entry)
			throws IOException {
		if (entry.isDirectory()) {
			return;
		}
		byte[] contents = zipInputStream.readNBytes((int) MAX_ENTRY_BYTES);
		if (contents.length >= MAX_ENTRY_BYTES) {
			LOGGER.warn("EPUB内のファイルが上限を超えたため読み飛ばします。entryName={}", entry.getName());
			return;
		}
		entries.put(entry.getName(), contents);
	}

	/**
	 * {@code META-INF/container.xml} からOPFのパスを取り出す。
	 *
	 * @param entries ZIPのエントリ
	 * @return OPFのZIP内パス
	 * @throws MarkdownPdfException container.xmlまたはOPFが無い場合
	 */
	private String resolveOpfPath(Map<String, byte[]> entries) {
		byte[] container = entries.get(CONTAINER_ENTRY_NAME);
		if (container == null) {
			throw new MarkdownPdfException("EPUBに " + CONTAINER_ENTRY_NAME + " がありません。");
		}
		Element rootFile = parseXml(container, CONTAINER_ENTRY_NAME).selectFirst("rootfile[full-path]");
		String opfPath = rootFile == null ? StringUtils.EMPTY : StringUtils.trim(rootFile.attr("full-path"));
		if (StringUtils.isEmpty(opfPath) || !entries.containsKey(opfPath)) {
			throw new MarkdownPdfException("EPUBの目次ファイル(OPF)を特定できません。");
		}
		return opfPath;
	}

	/**
	 * OPFのmanifestとspineから、本文XHTMLの相対パスを読む順に取り出す。
	 *
	 * @param opf OPFのXML
	 * @return spine順の本文XHTML相対パス
	 */
	private List<String> resolveSpineHrefs(Document opf) {
		Map<String, String> manifest = new LinkedHashMap<>();
		for (Element item : opf.select("manifest > item[id][href]")) {
			// XHTML以外（画像・CSS・フォント・目次のncx）は本文ではないため対象にしない。
			if (Strings.CI.equals(StringUtils.trim(item.attr("media-type")), XHTML_MEDIA_TYPE)) {
				manifest.put(StringUtils.trim(item.attr("id")), StringUtils.trim(item.attr("href")));
			}
		}
		List<String> hrefs = new ArrayList<>();
		for (Element itemRef : opf.select("spine > itemref[idref]")) {
			String href = manifest.get(StringUtils.trim(itemRef.attr("idref")));
			if (StringUtils.isNotEmpty(href)) {
				hrefs.add(href);
			}
		}
		return hrefs;
	}

	/**
	 * 本文XHTMLのbody部分を取り出して追加する。
	 *
	 * @param bodies    追加先のリスト
	 * @param entries   ZIPのエントリ
	 * @param entryName 本文XHTMLのZIP内パス
	 */
	private void appendBody(List<String> bodies, Map<String, byte[]> entries, String entryName) {
		byte[] contents = entries.get(entryName);
		if (contents == null) {
			// spineが指すファイルが無いEPUBは実在する。1つ欠けても残りは読めるため、ここでは止めない。
			LOGGER.warn("EPUBのspineが指すファイルが見つかりません。entryName={}", entryName);
			return;
		}
		Document document = Jsoup.parse(new String(contents, StandardCharsets.UTF_8));
		bodies.add(document.body().html());
	}

	/**
	 * XMLをjsoupで解析する。
	 *
	 * @param contents  XMLのbyte配列
	 * @param entryName エラーメッセージへ出すエントリ名
	 * @return 解析結果
	 * @throws MarkdownPdfException XMLとして解析できない場合
	 */
	private Document parseXml(byte[] contents, String entryName) {
		try {
			return Jsoup.parse(new String(contents, StandardCharsets.UTF_8), StringUtils.EMPTY, Parser.xmlParser());
		} catch (RuntimeException e) {
			throw new MarkdownPdfException("EPUBの " + entryName + " を解析できません。", e);
		}
	}

	/**
	 * ZIP内パスからディレクトリ部分（末尾の区切り込み）を取り出す。
	 *
	 * @param entryName ZIP内パス
	 * @return ディレクトリ部分。ルート直下の場合は空文字
	 */
	private String resolveDirectory(String entryName) {
		int separatorIndex = entryName.lastIndexOf(PATH_SEPARATOR);
		return separatorIndex < 0 ? StringUtils.EMPTY : entryName.substring(0, separatorIndex + 1);
	}
}
