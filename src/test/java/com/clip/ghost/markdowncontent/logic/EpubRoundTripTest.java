package com.clip.ghost.markdowncontent.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.clip.ghost.markdowncontent.exception.MarkdownPdfException;

/**
 * {@link EpubWriter} と {@link EpubReader} の組み立て・読み取りを検証するテスト。
 */
class EpubRoundTripTest {

	private final EpubWriter epubWriter = new EpubWriter();
	private final EpubReader epubReader = new EpubReader();

	@Test
	@DisplayName("書き出したEPUBを読み戻すと本文が復元できる")
	void writtenEpubCanBeReadBack() {
		byte[] epub = epubWriter.write("設計書", "<h1>設計書</h1><p>本文テスト</p>");

		String bodyHtml = epubReader.readBodyHtml(epub);

		assertTrue(bodyHtml.contains("設計書"));
		assertTrue(bodyHtml.contains("本文テスト"));
	}

	@Test
	@DisplayName("mimetypeは無圧縮でZIPの先頭エントリになる")
	void mimetypeEntryIsStoredFirstWithoutCompression() throws IOException {
		byte[] epub = epubWriter.write("設計書", "<p>本文</p>");

		try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(epub),
				StandardCharsets.UTF_8)) {
			ZipEntry first = zipInputStream.getNextEntry();
			// 先頭・無圧縮はEPUB仕様の要求。ここを外すとリーダーがEPUBとして認識できない。
			assertEquals("mimetype", first.getName());
			assertEquals(ZipEntry.STORED, first.getMethod());
			assertEquals("application/epub+zip",
					new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@Test
	@DisplayName("閉じないタグはXHTMLとして閉じた形で書き出す")
	void writtenEpubClosesVoidElementsAsXhtml() throws IOException {
		byte[] epub = epubWriter.write("改行", "<p>1行目<br>2行目</p>");

		String xhtml = new String(readEntries(epub).get("OEBPS/index.xhtml"), StandardCharsets.UTF_8);

		assertTrue(xhtml.contains("<br />"));
		assertFalse(xhtml.contains("<br>"));
	}

	@Test
	@DisplayName("本文はspineの順に連結する")
	void readBodyHtmlFollowsSpineOrder() throws IOException {
		byte[] epub = createEpub(List.of("second.xhtml", "first.xhtml"),
				Map.of("first.xhtml", "<p>1番目のファイル</p>", "second.xhtml", "<p>2番目のファイル</p>"));

		String bodyHtml = epubReader.readBodyHtml(epub);

		// ZIPのエントリ順やファイル名順ではなく、spineの並びが優先されることを固定する。
		assertTrue(bodyHtml.indexOf("2番目のファイル") < bodyHtml.indexOf("1番目のファイル"));
	}

	@Test
	@DisplayName("spineが指すファイルが欠けていても残りの本文を読める")
	void readBodyHtmlSkipsMissingSpineEntry() throws IOException {
		byte[] epub = createEpub(List.of("missing.xhtml", "first.xhtml"),
				Map.of("first.xhtml", "<p>読める本文</p>"));

		assertTrue(epubReader.readBodyHtml(epub).contains("読める本文"));
	}

	@Test
	@DisplayName("container.xmlが無いファイルはEPUBとして読めない")
	void readBodyHtmlThrowsWhenContainerIsMissing() throws IOException {
		byte[] notEpub = createZip(Map.of("OEBPS/index.xhtml", "<html><body><p>本文</p></body></html>"));

		assertThrows(MarkdownPdfException.class, () -> epubReader.readBodyHtml(notEpub));
	}

	@Test
	@DisplayName("ZIPですらないファイルはEPUBとして読めない")
	void readBodyHtmlThrowsWhenFileIsNotZip() {
		byte[] notZip = "これはEPUBではありません。".getBytes(StandardCharsets.UTF_8);

		assertThrows(MarkdownPdfException.class, () -> epubReader.readBodyHtml(notZip));
	}

	/**
	 * spineの並びと本文を指定してEPUBを組み立てる。
	 *
	 * @param spineHrefs spineに並べる本文のhref
	 * @param bodies     hrefごとの本文HTML
	 * @return EPUBのbyte配列
	 * @throws IOException 組み立てに失敗した場合
	 */
	private byte[] createEpub(List<String> spineHrefs, Map<String, String> bodies) throws IOException {
		Map<String, String> entries = new LinkedHashMap<>();
		entries.put("META-INF/container.xml", """
				<?xml version="1.0" encoding="UTF-8"?>
				<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
				  <rootfiles>
				    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
				  </rootfiles>
				</container>
				""");
		List<String> manifestItems = new ArrayList<>();
		List<String> spineItems = new ArrayList<>();
		for (String href : spineHrefs) {
			String id = href.replace(".xhtml", "");
			manifestItems
					.add("<item id=\"" + id + "\" href=\"" + href + "\" media-type=\"application/xhtml+xml\"/>");
			spineItems.add("<itemref idref=\"" + id + "\"/>");
		}
		entries.put("OEBPS/content.opf", """
				<?xml version="1.0" encoding="UTF-8"?>
				<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookId">
				  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>test</dc:title></metadata>
				  <manifest>%s</manifest>
				  <spine>%s</spine>
				</package>
				""".formatted(String.join("", manifestItems), String.join("", spineItems)));
		bodies.forEach((href, body) -> entries.put("OEBPS/" + href,
				"<html><body>" + body + "</body></html>"));
		return createZip(entries);
	}

	/**
	 * エントリ名と内容からZIPを組み立てる。
	 *
	 * @param entries エントリ名と内容
	 * @return ZIPのbyte配列
	 * @throws IOException 組み立てに失敗した場合
	 */
	private byte[] createZip(Map<String, String> entries) throws IOException {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
				zipOutputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				zipOutputStream.closeEntry();
			}
			zipOutputStream.finish();
			return outputStream.toByteArray();
		}
	}

	/**
	 * ZIPのエントリ名と内容を読み出す。
	 *
	 * @param zipBytes ZIPのbyte配列
	 * @return エントリ名をキーにした内容のMap
	 * @throws IOException 読み込みに失敗した場合
	 */
	private Map<String, byte[]> readEntries(byte[] zipBytes) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes),
				StandardCharsets.UTF_8)) {
			ZipEntry entry = zipInputStream.getNextEntry();
			while (entry != null) {
				entries.put(entry.getName(), zipInputStream.readAllBytes());
				entry = zipInputStream.getNextEntry();
			}
		}
		return entries;
	}
}
