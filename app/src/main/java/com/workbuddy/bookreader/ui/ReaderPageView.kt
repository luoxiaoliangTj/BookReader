package com.workbuddy.bookreader.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workbuddy.bookreader.data.Highlight
import com.workbuddy.bookreader.reader.Page

/**
 * 单页渲染：带高亮背景、长按拖动选区、跟读高亮。
 */
@Composable
fun ReaderPageView(
    page: Page,
    fontSize: Float,
    highlights: List<Highlight>,
    selectionRange: Pair<Int, Int>,
    onSelection: (Int, Int) -> Unit,
    onTap: (Float) -> Unit,
    onLayoutResult: (TextLayoutResult) -> Unit
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotated = remember(page, highlights, selectionRange) {
        buildPageText(page, highlights, selectionRange)
    }
    val scroll = rememberScrollState()

    Column(Modifier
        .fillMaxSize()
        .verticalScroll(scroll)
        .pointerInput(page) {
            detectTapGestures(onTap = { onTap(it.x) })
        }
        .pointerInput(page) {
            // 长按开始选择，拖动扩展选区
            var down = false
            detectDragGesturesAfterLongPress(
                onDragStart = { pos ->
                    down = true
                    val o = layout?.getOffsetForPosition(pos) ?: 0
                    onSelection(o, o + 1)
                },
                onDrag = { change, _ ->
                    if (down) {
                        val o = layout?.getOffsetForPosition(change.position) ?: 0
                        val s = selectionRange.first
                        onSelection(minOf(s, o), maxOf(s + 1, o + 1))
                    }
                },
                onDragEnd = { down = false },
                onDragCancel = { down = false }
            )
        }
        .padding(20.dp)
    ) {
        Text(annotated,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.5f).sp,
            onTextLayout = { layout = it; onLayoutResult(it) })
    }
}

/** 组装带样式的页面文本 */
fun buildPageText(page: Page, highlights: List<Highlight>, selection: Pair<Int, Int>): AnnotatedString {
    return buildAnnotatedString {
        var local = 0
        for (line in page.lines) {
            val start = local
            val end = local + line.text.length
            if (line.isTitle) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp))
                append(line.text)
                pop()
            } else append(line.text)
            // 高亮划线
            for (h in highlights) {
                // globalStart 跨页近似：对同章节按文本匹配
                val gs = h.startOffset - line.globalStart
                val ge = h.endOffset - line.globalStart
                val s = gs.coerceIn(start, end); val e = ge.coerceIn(start, end)
                if (s < e) addStyle(SpanStyle(background = Color(h.color)), s, e)
            }
            // 当前选区
            val selS = selection.first.coerceIn(start, end)
            val selE = selection.second.coerceIn(start, end)
            if (selS < selE) addStyle(SpanStyle(background = Color(0x662196F3)), selS, selE)
            append("\n")
            local = end + 1
        }
    }
}
