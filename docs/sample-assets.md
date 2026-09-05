# 同梱サンプル素材の由来

## 目的

`/getSample`の画像・PDF表示確認に使用する素材の作成元と用途を記録します。
実在する会社、人物、業務資料、第三者製品画面をfixtureへ含めない方針です。

## `src/main/resources/static/img/sample.png`

- 作成日: 2026-09-06
- 用途: `/getSample`でbase64画像表示を確認するためのsynthetic sample
- 作成方法: OpenAIの画像生成機能を使い、Ghost-PDF5専用の新規画像として生成
- 内容: 抽象的な文書、paper clip、PDFを示す無文字のcorner tab
- 除外指定: 人物、企業logo、製品名、第三者UI、watermark、可読文字
- repository格納時のsize: 850 x 566 pixels
- SHA-1: `3e12ccca983911a4b81462ef25ba554f69f0897e`

生成時のprompt概要:

```text
Create a clean, original document illustration for an open-source PDF utility sample page.
Use abstract text lines and simple paper elements.
Do not include people, brand logos, company names, copyrighted interfaces,
readable words, watermarks, laptops or phones.
```

生成画像はmacOSの`sips`で850 x 566 pixelsへ変換しています。

## `src/main/resources/static/img/sample.pdf`

- 作成日: 2026-09-06
- 用途: `/getSample`でbase64 PDF表示を確認するためのsynthetic sample
- 作成方法: Apache PDFBox 3.0.7で新規生成
- page数: 1
- page size: A4
- encryption: なし
- JavaScript: なし
- 個人情報・第三者文書内容: なし
- SHA-1: `5a84ddc2febe938a448f3602a26de46e00157295`

PDF metadata:

```text
Title: Ghost-PDF5 Synthetic Sample
Subject: Public repository sample fixture
Author: Ghost-PDF5 Contributors
Creator: Apache PDFBox 3.0.7
```

PDF本文:

```text
Ghost-PDF5 Synthetic Sample
This document is generated with Apache PDFBox for local display tests.
It contains no personal information or third-party document content.
Pages: 1
```

## `src/test/resources/pdf/sample.pdf`

PDF service / controller testで使用する最小PDF fixtureです。
1 pageの空PDFで、画像、font、JavaScript、個人情報、第三者contentを含みません。
SHA-1は`4b6bdbc4c6220240e65fde64a93fb15de7d3f055`です。

## 更新ルール

- sample素材を変更した場合は、この資料も更新します。
- Internetから取得した画像やPDFを、出所・license未確認のまま追加しません。
- 実在する業務資料や個人情報をtest fixtureとして使用しません。
- binary metadataも公開前checkの対象にします。
- repository全体へ適用するlicenseはrootの`LICENSE`で別途定めます。
