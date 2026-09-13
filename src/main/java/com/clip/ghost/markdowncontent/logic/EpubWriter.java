package com.clip.ghost.markdowncontent.logic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.Entities.EscapeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;

import lombok.NoArgsConstructor;

/**
 * sanitize済みHTMLから1章構成のEPUBを組み立てる内部ロジック。
 * <p>
 * 専用ライブラリは入れない。EPUBはZIPと数個のXMLで構成でき、必要なのは標準APIと
 * 導入済みのjsoupだけのため。
 * <p>
 * 章分けはしない。変換元が「PDFから起こした1本の文書」なので、どこで章を切るかの根拠が無い。
 * 機械的にページで切ると、リーダー上で文の途中に章境界が入る。
 */
@Component
@NoArgsConstructor
public class EpubWriter {
	private static final Logger LOGGER = LoggerFactory.getLogger(EpubWriter.class);
	private static final String MIMETYPE_ENTRY_NAME = "mimetype";
	private static final String MIMETYPE_VALUE = "application/epub+zip";
	private static final String CONTAINER_ENTRY_NAME = "META-INF/container.xml";
	private static final String OPF_ENTRY_NAME = "OEBPS/content.opf";
	private static final String NCX_ENTRY_NAME = "OEBPS/toc.ncx";
	private static final String CONTENT_ENTRY_NAME = "OEBPS/index.xhtml";
	private static final String CONTENT_HREF = "index.xhtml";

	private static final String CONTAINER_XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
			  <rootfiles>
			    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
			  </rootfiles>
			</container>
			""";

	private static final String OPF_XML_FORMAT = """
			<?xml version="1.0" encoding="UTF-8"?>
			<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookId">
			  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
			    <dc:title>%s</dc:title>
			    <dc:language>ja</dc:language>
			    <dc:identifier id="bookId">urn:uuid:%s</dc:identifier>
			  </metadata>
			  <manifest>
			    <item id="content" href="index.xhtml" media-type="application/xhtml+xml"/>
			    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
			  </manifest>
			  <spine toc="ncx">
			    <itemref idref="content"/>
			  </spine>
			</package>
			""";

	private static final String NCX_XML_FORMAT = """
			<?xml version="1.0" encoding="UTF-8"?>
			<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
			  <head>
			    <meta name="dtb:uid" content="urn:uuid:%s"/>
			  </head>
			  <docTitle><text>%s</text></docTitle>
			  <navMap>
			    <navPoint id="navPoint-1" playOrder="1">
			      <navLabel><text>%s</text></navLabel>
			      <content src="index.xhtml"/>
			    </navPoint>
			  </navMap>
			</ncx>
			""";

	private static final String XHTML_FORMAT = """
			<?xml version="1.0" encoding="UTF-8"?>
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ja" lang="ja">
			<head><meta charset="utf-8"/><title>%s</title></head>
			<body>
			%s
			</body>
			</html>
			""";

	/**
	 * sanitize済みHTMLからEPUBのbyte配列を生成する。
	 *
	 * @param title         文書のタイトル
	 * @param sanitizedHtml sanitize済みHTML本文
	 * @return EPUBのbyte配列
	 * @throws MarkdownPdfException EPUBの組み立てに失敗した場合
	 */
	public byte[] write(String title, String sanitizedHtml) {
		String documentTitle = StringUtils.defaultIfBlank(title, "document");
		String escapedTitle = Entities.escape(documentTitle);
		String bookId = UUID.randomUUID().toString();
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
			writeMimetypeEntry(zipOutputStream);
			writeEntry(zipOutputStream, CONTAINER_ENTRY_NAME, CONTAINER_XML);
			writeEntry(zipOutputStream, OPF_ENTRY_NAME, OPF_XML_FORMAT.formatted(escapedTitle, bookId));
			writeEntry(zipOutputStream, NCX_ENTRY_NAME,
					NCX_XML_FORMAT.formatted(bookId, escapedTitle, escapedTitle));
			writeEntry(zipOutputStream, CONTENT_ENTRY_NAME,
					XHTML_FORMAT.formatted(escapedTitle, toXhtmlBody(sanitizedHtml)));
			zipOutputStream.finish();
			LOGGER.info("EPUBを生成しました。contentHref={}", CONTENT_HREF);
			return outputStream.toByteArray();
		} catch (IOException e) {
			throw new MarkdownPdfException("EPUBの生成に失敗しました。", e);
		}
	}

	/**
	 * {@code mimetype} エントリを無圧縮でZIPの先頭へ書き込む。
	 * <p>
	 * EPUB仕様は「{@code mimetype} を最初のエントリとし、圧縮も追加フィールドも付けない」ことを求める。
	 * リーダーはZIPの先頭バイト列を直接見てEPUBかどうかを判定するため、ここを外すとファイルを開けない。
	 * 無圧縮（STORED）で書くにはサイズとCRCを自分で設定する必要がある。
	 *
	 * @param zipOutputStream 書き込み先のZIP
	 * @throws IOException 書き込みに失敗した場合
	 */
	private void writeMimetypeEntry(ZipOutputStream zipOutputStream) throws IOException {
		byte[] contents = MIMETYPE_VALUE.getBytes(StandardCharsets.US_ASCII);
		ZipEntry entry = new ZipEntry(MIMETYPE_ENTRY_NAME);
		entry.setMethod(ZipEntry.STORED);
		entry.setSize(contents.length);
		entry.setCompressedSize(contents.length);
		CRC32 crc = new CRC32();
		crc.update(contents);
		entry.setCrc(crc.getValue());
		zipOutputStream.putNextEntry(entry);
		zipOutputStream.write(contents);
		zipOutputStream.closeEntry();
	}

	/**
	 * UTF-8のテキストをZIPエントリとして書き込む。
	 *
	 * @param zipOutputStream 書き込み先のZIP
	 * @param entryName       ZIP内パス
	 * @param contents        書き込む内容
	 * @throws IOException 書き込みに失敗した場合
	 */
	private void writeEntry(ZipOutputStream zipOutputStream, String entryName, String contents) throws IOException {
		zipOutputStream.putNextEntry(new ZipEntry(entryName));
		zipOutputStream.write(contents.getBytes(StandardCharsets.UTF_8));
		zipOutputStream.closeEntry();
	}

	/**
	 * HTML本文をXHTMLとして出力し直す。
	 * <p>
	 * EPUBの本文はXMLとして解析されるため、{@code <br>} のような閉じないタグが残っていると
	 * リーダーが整形式エラーで開けない。PDF出力側と同じくjsoupのXML出力で閉じ直す。
	 *
	 * @param sanitizedHtml sanitize済みHTML本文
	 * @return XHTML本文
	 */
	private String toXhtmlBody(String sanitizedHtml) {
		Document bodyDocument = Jsoup.parseBodyFragment(StringUtils.defaultString(sanitizedHtml));
		bodyDocument.outputSettings().syntax(Document.OutputSettings.Syntax.xml).escapeMode(EscapeMode.xhtml)
				.charset(StandardCharsets.UTF_8).prettyPrint(false);
		return bodyDocument.body().html();
	}
}
