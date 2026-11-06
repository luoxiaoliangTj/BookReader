package com.workbuddy.bookreader.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import org.mozilla.universalchardet.UniversalDetector
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.ByteArrayOutputStream
import kotlin.text.Charsets
import java.nio.charset.Charset
import java.io.InputStream
import java.util.zip.ZipInputStream

/** 统一的解析结果：章节列表 */
data class ParsedBook(
    val chapters: List<Chapter>,
    val format: String
)

data class Chapter(val title: String, val paragraphs: List<String>)

object BookParser {

    fun detectFormat(name: String, mime: String?): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in listOf("txt", "text") || mime == "text/plain" -> "TXT"
            ext == "epub" || mime == "application/epub+zip" -> "EPUB"
            ext in listOf("mobi", "azw", "azw3", "prc") -> "MOBI"
            ext == "pdf" || mime == "application/pdf" -> "PDF"
            else -> "TXT"
        }
    }

    suspend fun parse(ctx: Context, uri: Uri, format: String): ParsedBook = when (format) {
        "EPUB" -> parseEpub(ctx, uri)
        "MOBI" -> parseMobi(ctx, uri)
        else -> parseTxt(ctx, uri)
    }

    /* ---------- TXT：编码自动检测 + 章节自动切分 ---------- */
    suspend fun parseTxt(ctx: Context, uri: Uri): ParsedBook {
        val bytes = readAll(ctx.contentResolver.openInputStream(uri))
        val charset = java.nio.charset.Charset.forName("UTF-8")
        val text = String(bytes, charset)
        return ParsedBook(splitChapters(text), "TXT")
    }

    fun detectCharset(bytes: ByteArray): java.nio.charset.Charset {
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, minOf(bytes.size, 65536))
        detector.dataEnd()
        val name = detector.detectedCharset
        return when {
            name != null -> charsetFor(name)
            isUtf8(bytes) -> Charsets.UTF_8
            else -> Charset.forName("GBK")
        }
    }

    private fun charsetFor(name: String): java.nio.charset.Charset = try {
        java.nio.charset.Charset.forName(name)
    } catch (e: Exception) {
        Charsets.UTF_8
    }

    private fun isUtf8(b: ByteArray): Boolean {
        var i = 0; var valid = true
        while (i < b.size) {
            val c = b[i].toInt() and 0xFF
            val n = when { c < 0x80 -> 0; c shr 5 == 0b110 -> 1; c shr 4 == 0b1110 -> 2
                c shr 3 == 0b11110 -> 3; else -> return false }
            if (i + n >= b.size) break
            for (j in 1..n) if (b[i + j].toInt() and 0xC0 != 0x80) { valid = false; break }
            i += n + 1
        }
        return valid
    }

    /** 自动切分章节：识别 "第x章/卷/节"、数字标题等 */
    fun splitChapters(text: String): List<Chapter> {
        val paraRe = Regex("\\n+")
        val titleRe = Regex(
            "^\\s*(第[0-9〇零一二三四五六七八九十百千万]+[章节卷部回篇集]|序章|楔子|前言|后记|附录|Chapter\\s+\\d+|CHAPTER\\s+\\d+)\\b.{0,40}$"
        )
        val paragraphs = text.split(paraRe).map { it.trim() }.filter { it.isNotEmpty() }
        val chapters = mutableListOf<Chapter>()
        var title = "正文"
        var buf = mutableListOf<String>()
        for (p in paragraphs) {
            if (p.length < 60 && titleRe.matches(p)) {
                if (buf.isNotEmpty()) { chapters.add(Chapter(title, buf)); buf = mutableListOf() }
                title = p
            } else buf.add(p)
        }
        if (buf.isNotEmpty()) chapters.add(Chapter(title, buf))
        if (chapters.isEmpty()) chapters.add(Chapter("全文", paragraphs))
        return chapters
    }

    /* ---------- EPUB：解压 zip，解析 OPF 阅读顺序，抽取正文 ---------- */
    suspend fun parseEpub(ctx: Context, uri: Uri): ParsedBook {
        val items = mutableMapOf<String, ByteArray>()   // zip 内路径 -> 内容
        ZipInputStream(ctx.contentResolver.openInputStream(uri)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) items[e.name] = readAll(zis)
                e = zis.nextEntry
            }
        }
        val opfPath = items.keys.firstOrNull { it.endsWith(".opf") }
            ?: return ParsedBook(listOf(Chapter("正文", listOf("EPUB 结构异常"))), "EPUB")
        val opf = Jsoup.parse(String(items[opfPath]!!, Charsets.UTF_8), "")
        val opfBase = opfPath.substringBeforeLast('/', "")
        // manifest id -> href
        val manifest = opf.select("manifest > item").associate {
            it.attr("id") to resolvePath(opfBase, it.attr("href"))
        }
        // spine 阅读顺序
        val spineIds = opf.select("spine > itemref").map { it.attr("idref") }
        val chapters = mutableListOf<Chapter>()
        var idx = 0
        for (id in spineIds) {
            val path = manifest[id] ?: continue
            val html = items[path] ?: continue
            val doc = Jsoup.parse(String(html, Charsets.UTF_8))
            val title = doc.selectFirst("h1,h2,h3,title")?.text()?.takeIf { it.isNotBlank() }
                ?: "章节 ${idx + 1}"
            val paras = doc.body()?.select("p, div, h1, h2, h3, blockquote")
                ?.mapNotNull { el: Element ->
                    val t = el.ownText().trim()
                    if (t.isBlank() || el.children().isNotEmpty()) null else t
                }?.ifEmpty {
                    val t = doc.body()?.text()?.trim().orEmpty()
                    if (t.isBlank()) emptyList() else listOf(t)
                } ?: emptyList()
            if (paras.isNotEmpty()) { chapters.add(Chapter(title, paras)); idx++ }
        }
        if (chapters.isEmpty()) chapters.add(Chapter("正文", listOf("未解析到内容")))
        return ParsedBook(chapters, "EPUB")
    }

    private fun resolvePath(base: String, href: String): String {
        val parts = (if (base.isBlank()) "" else "$base/") + href.removePrefix("./")
        val stack = mutableListOf<String>()
        for (seg in parts.split('/')) {
            when (seg) { "", "." -> {} ; ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(seg) }
        }
        return stack.joinToString("/")
    }

    /* ---------- MOBI：解析 PalmDOC 记录，提取纯文本（无 DRM 场景） ---------- */
    suspend fun parseMobi(ctx: Context, uri: Uri): ParsedBook {
        val bytes = readAll(ctx.contentResolver.openInputStream(uri))
        return try {
            val text = MobiTextExtractor.extract(bytes)
            ParsedBook(splitChapters(text), "MOBI")
        } catch (ex: Exception) {
            Log.e("BookParser", "MOBI parse failed", ex)
            ParsedBook(listOf(Chapter("提示", listOf(
                "该 MOBI 文件可能带有 DRM 保护或格式较新（AZW3/KFX），暂不支持解析。"))), "MOBI")
        }
    }

    private fun readAll(ins: InputStream?): ByteArray {
        requireNotNull(ins) { "无法打开文件" }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        ins.use {
            var n: Int
            while (it.read(buf).also { r -> n = r } > 0) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
