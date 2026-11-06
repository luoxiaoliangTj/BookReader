package com.workbuddy.bookreader.reader

import com.workbuddy.bookreader.parser.Chapter

/**
 * 自动排版引擎：把章节段落排成"页"。
 * 根据屏幕宽度、字体大小、行高、边距动态分页，任何格式（TXT/EPUB/MOBI/OCR 文本）统一处理。
 */
data class TypesetConfig(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val fontSizePx: Float = 44f,
    val lineHeightFactor: Float = 1.5f,
    val marginPx: Float = 48f,
    val fontPath: String? = null   // 支持自定义字体
) {
    val pageWidth = screenWidthPx - 2 * marginPx
    val pageHeight = screenHeightPx - 2 * marginPx
    val lineHeight = fontSizePx * lineHeightFactor
    val linesPerPage = maxOf(1, (pageHeight / lineHeight).toInt())
}

data class Page(
    val chapterIndex: Int,
    val lines: List<Line>,
    val indexInBook: Int
)

data class Line(val text: String, val chapterIndex: Int, val globalStart: Int, val isTitle: Boolean)

object Paginator {

    /**
     * 简化测量：中文按 1 字宽、ASCII 按 0.55 字宽估算。
     * 精确排版可换成 Paint.measureText；此处估算保证离线快速分页。
     */
    fun measure(text: String, fontSizePx: Float): Float =
        text.sumOf { ch -> if (ch.code > 0x2E00 || ch.code > 0xFF00) 1.0 else 0.55 }.toFloat() * fontSizePx

    fun layoutChapters(chapters: List<Chapter>, cfg: TypesetConfig): List<Page> {
        val pages = mutableListOf<Page>()
        var pageIndex = 0
        var globalOffset = 0
        chapters.forEachIndexed { ci, chapter ->
            val lines = mutableListOf<Line>()
            fun flush() {
                if (lines.isNotEmpty()) {
                    pages.add(Page(ci, lines.toList(), pageIndex++))
                    lines.clear()
                }
            }
            // 标题行
            val title = chapter.title
            lines.add(Line(title, ci, globalOffset, true))
            globalOffset += title.length
            val titleWidth = measure(title, cfg.fontSizePx * 1.3f)
            var rem = cfg.pageHeight - cfg.lineHeight * 1.6f
            // 段落
            for (para in chapter.paragraphs) {
                rem -= cfg.lineHeight
                if (rem < 0) { flush(); rem = cfg.pageHeight - cfg.lineHeight }
                var start = 0
                val chars = para
                var indent = 0
                while (start < chars.length) {
                    val avail = cfg.pageWidth - (if (start == 0) cfg.fontSizePx * 2f else 0f)
                    var w = 0f; var end = start
                    while (end < chars.length) {
                        val cw = measure(chars[end].toString(), cfg.fontSizePx)
                        if (w + cw > avail) break
                        w += cw; end++
                    }
                    if (end == start) end = start + 1  // 单字符超宽兜底
                    lines.add(Line(chars.substring(start, end), ci, globalOffset + start, false))
                    start = end
                    rem -= cfg.lineHeight
                    if (rem < 0 && start < chars.length) {
                        flush(); rem = cfg.pageHeight
                    }
                }
                globalOffset += chars.length
                indent = 0
            }
            flush()
        }
        return pages
    }
}
