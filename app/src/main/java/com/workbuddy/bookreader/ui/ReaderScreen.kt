package com.workbuddy.bookreader.ui

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workbuddy.bookreader.data.AppDatabase
import com.workbuddy.bookreader.data.Bookmark
import com.workbuddy.bookreader.data.Highlight
import com.workbuddy.bookreader.parser.BookParser
import com.workbuddy.bookreader.parser.ParsedBook
import com.workbuddy.bookreader.reader.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 阅读器界面：
 * - 文本渲染 + 自动分页（上一页/下一页/翻页手势）
 * - 长按选词划线（多色）、添加笔记、书签
 * - 点按选中文本弹翻译
 * - 朗读（跟读高亮）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: String, format: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var book by remember { mutableStateOf<com.workbuddy.bookreader.data.Book?>(null) }
    var parsed by remember { mutableStateOf<ParsedBook?>(null) }
    var cfg by remember { mutableStateOf<TypesetConfig?>(null) }
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var pageIndex by remember { mutableStateOf(0) }
    var fontSize by remember { mutableStateOf(44f) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // 选中划线状态
    var selStart by remember { mutableStateOf(-1) }
    var selEnd by remember { mutableStateOf(-1) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showBookmarkList by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf<String?>(null) }
    var highlights by remember { mutableStateOf<List<Highlight>>(emptyList()) }

    // 朗读
    var tts by remember { mutableStateOf<ReaderTTS?>(null) }
    var speakingIdx by remember { mutableStateOf(-1) }
    var ttsJob by remember { mutableStateOf<Job?>(null) }
    var speaking by remember { mutableStateOf(false) }

    // 加载 & 排版
    LaunchedEffect(bookId, fontSize) {
        loading = true; error = null
        val b = db.bookDao().byId(bookId)
        if (b == null) { error = "找不到书籍"; loading = false; return@LaunchedEffect }
        book = b
        if (parsed == null) {
            try {
                parsed = withContext(Dispatchers.IO) {
                    if (format == "PDF" || format == "PDF_OCR") {
                        PdfProcessor.ocrWholePdf(ctx, Uri.parse(b.path)) { done, total ->
                            loading = false
                            error = "OCR 识别中 $done/$total 页"
                        }
                    } else {
                        BookParser.parse(ctx, Uri.parse(b.path), format)
                    }
                }
            } catch (e: Exception) { error = "解析失败：${e.message}"; loading = false; return@LaunchedEffect }
        }
        val w = ctx.resources.displayMetrics.widthPixels
        val h = ctx.resources.displayMetrics.heightPixels
        cfg = TypesetConfig(w, h, fontSizePx = fontSize)
        pages = Paginator.layoutChapters(parsed!!.chapters, cfg!!)
        pageIndex = (b.progressPercent / 100f * pages.size).toInt().coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        error = null; loading = false
    }

    LaunchedEffect(bookId) {
        db.highlightDao().forBook(bookId).collect { highlights = it }
    }

    DisposableEffect(Unit) {
        onDispose { tts?.shutdown(); ttsJob?.cancel(); scope.launch {
            book?.let { b -> db.bookDao().updateProgress(
                b.id, if (pages.isEmpty()) 0 else (pageIndex * 100 / pages.size), System.currentTimeMillis()) }
        } }
    }

    val page = pages.getOrNull(pageIndex)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(book?.title ?: "阅读", fontSize = 18.sp, maxLines = 1) },
            navigationIcon = { TextButton(onClick = onBack) { Text("书架") } },
            actions = {
                IconButton(onClick = { showChapterList = true }) { Text("目录", fontSize = 13.sp) }
                IconButton(onClick = {
                    scope.launch {
                        db.bookmarkDao().insert(Bookmark(bookId = bookId,
                            position = pageIndex, label = "第 ${pageIndex + 1} 页"))
                    }
                }) { Text("🔖", fontSize = 18.sp) }
                IconButton(onClick = { showBookmarkList = true }) { Text("书签", fontSize = 13.sp) }
            }
        )
    }, bottomBar = {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            if (showActionMenu && selStart in 0 until selEnd) {
                SelectionActionBar(
                    onHighlight = { color ->
                        scope.launch {
                            val text = selectedText(page, selStart, selEnd)
                            db.highlightDao().upsert(Highlight(
                                bookId = bookId, chapterIndex = page?.chapterIndex ?: 0,
                                startOffset = globalOffsetOf(page, selStart),
                                endOffset = globalOffsetOf(page, selEnd),
                                text = text, color = color))
                        }
                        showActionMenu = false; selStart = -1; selEnd = -1
                    },
                    onTranslate = {
                        scope.launch {
                            val text = selectedText(page, selStart, selEnd)
                            translation = "翻译中…"
                            try {
                                val lang = detectLang(text)
                                translation = Translator.translate(text,
                                    source = lang, target = if (lang == "zh") "en" else "zh")
                            } catch (e: Exception) { translation = "翻译失败：${e.message}" }
                        }
                        showActionMenu = false
                    },
                    onSpeak = {
                        val text = selectedText(page, selStart, selEnd)
                        startSpeaking(ctx, listOf(text)) { speakingIdx = it }
                        showActionMenu = false
                    },
                    onNote = {
                        // 弹出笔记输入（简化：用 AlertDialog 在下方渲染）
                        showNoteDialog = true
                    }
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (pageIndex > 0) pageIndex-- }) { Text("上一页") }
                Text("${pageIndex + 1}/${pages.size}", fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
                TextButton(onClick = { if (pageIndex < pages.size - 1) pageIndex++ }) { Text("下一页") }
                TextButton(onClick = { fontSize = (fontSize - 4f).coerceAtLeast(24f) }) { Text("A-") }
                TextButton(onClick = { fontSize = (fontSize + 4f).coerceAtMost(72f) }) { Text("A+") }
                TextButton(onClick = {
                    if (speaking) { tts?.pause(); ttsJob?.cancel(); speaking = false; speakingIdx = -1 }
                    else {
                        val paras = pages.drop(pageIndex).flatMap { p -> p.lines.filter { !it.isTitle }.map { it.text } }
                        startSpeaking(ctx, paras) { speakingIdx = it }
                        speaking = true
                    }
                }) { Text(if (speaking) "⏸ 朗读" else "▶ 朗读") }
            }
            translation?.let { t ->
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("翻译", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { translation = null }) { Text("关闭") }
                        }
                        Text(t, fontSize = 15.sp)
                    }
                }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(error!!, Modifier.align(Alignment.Center).padding(24.dp))
                page != null -> ReaderPageView(
                    page = page,
                    fontSize = fontSize,
                    highlights = highlights.filter {
                        it.chapterIndex == page.chapterIndex
                    },
                    selectionRange = selStart to selEnd,
                    onSelection = { s, e ->
                        selStart = s; selEnd = e; showActionMenu = s in 0 until e
                    },
                    onTap = { x ->
                        val w = ctx.resources.displayMetrics.widthPixels
                        when { x < w / 4f -> if (pageIndex > 0) pageIndex--
                            x > w * 3 / 4f -> if (pageIndex < pages.size - 1) pageIndex++ }
                    },
                    onLayoutResult = { }
                )
            }

            if (showChapterList) {
                AlertDialog(onDismissRequest = { showChapterList = false },
                    title = { Text("目录") },
                    text = {
                        LazyColumn(Modifier.height(400.dp)) {
                            items(parsed?.chapters ?: emptyList()) { ch ->
                                Text(ch.title, Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val idx = pages.indexOfFirst { p ->
                                            parsed?.chapters?.getOrNull(p.chapterIndex)?.title == ch.title
                                        }
                                        if (idx >= 0) pageIndex = idx
                                        showChapterList = false
                                    }
                                    .padding(10.dp))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showChapterList = false }) { Text("关闭") } })
            }
            if (showBookmarkList) {
                val marks by db.bookmarkDao().forBook(bookId).collectAsState(initial = emptyList())
                AlertDialog(onDismissRequest = { showBookmarkList = false },
                    title = { Text("书签") },
                    text = {
                        LazyColumn(Modifier.height(300.dp)) {
                            if (marks.isEmpty()) item { Text("暂无书签") }
                            items(marks) { m ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    pageIndex = m.position; showBookmarkList = false
                                }.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(m.label)
                                    Text("删除", color = Color.Red, modifier = Modifier.clickable {
                                        scope.launch { db.bookmarkDao().delete(m.id) }
                                    })
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showBookmarkList = false }) { Text("关闭") } })
            }
            if (showNoteDialog) {
                var noteText by remember { mutableStateOf("") }
                AlertDialog(onDismissRequest = { showNoteDialog = false },
                    title = { Text("添加笔记") },
                    text = {
                        OutlinedTextField(value = noteText, onValueChange = { noteText = it },
                            placeholder = { Text("写下你的想法…") }, minLines = 3)
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                db.highlightDao().upsert(Highlight(
                                    bookId = bookId, chapterIndex = page?.chapterIndex ?: 0,
                                    startOffset = globalOffsetOf(page, selStart),
                                    endOffset = globalOffsetOf(page, selEnd),
                                    text = selectedText(page, selStart, selEnd),
                                    note = noteText))
                            }
                            showNoteDialog = false; showActionMenu = false; selStart = -1; selEnd = -1
                        }) { Text("保存") }
                    },
                    dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("取消") } })
            }
        }
    }
}

/* ---- 以下为辅助状态与函数（与 ReaderScreen 同文件作用域） ---- */

var showNoteDialog by mutableStateOf(false)

private fun startSpeaking(ctx: Context, paras: List<String>, onIndex: (Int) -> Unit) {
    val engine = ReaderTTSHolder.get(ctx)
    kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
        if (engine.awaitReady()) {
            engine.speak(paras).collect { onIndex(it) }
        }
    }
}

object ReaderTTSHolder {
    private var tts: ReaderTTS? = null
    fun get(ctx: Context): ReaderTTS = tts ?: ReaderTTS(ctx).also { tts = it }
}

private fun detectLang(text: String): String {
    val cjk = text.count { it.code in 0x4E00..0x9FFF }
    return if (cjk > text.length / 4) "zh" else "en"
}

private fun selectedText(page: Page?, start: Int, end: Int): String {
    page ?: return ""
    val sb = StringBuilder()
    var offset = 0
    for (line in page.lines) {
        val ls = offset; val le = offset + line.text.length
        if (le > start && ls < end) {
            sb.append(line.text.substring(
                (start - ls).coerceAtLeast(0).coerceAtMost(line.text.length),
                (end - ls).coerceAtMost(line.text.length)))
            if (ls < start) sb.insert(0, "…")
        }
        offset = le
        if (ls >= end) break
    }
    return sb.toString().trimEnd('…')
}

private fun globalOffsetOf(page: Page?, localIndex: Int): Int {
    page ?: return 0
    var offset = 0
    for (line in page.lines) {
        if (offset + line.text.length >= localIndex) return line.globalStart + (localIndex - offset)
        offset += line.text.length
    }
    return line0(page)?.globalStart ?: 0
}

private fun line0(page: Page) = page.lines.firstOrNull()
