package com.workbuddy.bookreader.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workbuddy.bookreader.data.AppDatabase
import com.workbuddy.bookreader.data.Book
import com.workbuddy.bookreader.parser.BookParser
import com.workbuddy.bookreader.reader.PdfProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@Suppress("ExperimentalMaterial3Api")
@Composable
fun LibraryScreen(onOpenBook: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()
    val books by db.bookDao().all().collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingFormat by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && pendingFormat != null) {
            scope.launch {
                busy = "导入中…"
                try {
                    val format = pendingFormat!!
                    val id = uriHash(ctx, uri)
                    val title = uri.lastPathSegment?.substringAfterLast('/') ?: "未命名"
                    if (format == "PDF") {
                        busy = "OCR 识别中（仅首次）…"
                        // PDF：先 OCR 一页做预览；完整 OCR 在打开后按需进行
                        withContext(Dispatchers.IO) {
                            PdfProcessor.ocrPage(ctx, uri, 0)
                        }
                    }
                    db.bookDao().upsert(Book(id = id, title = title,
                        path = uri.toString(), format = format))
                } catch (e: Exception) {
                    busy = "导入失败：${e.message}"
                    return@launch
                }
                busy = null
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("书架", fontSize = 22.sp) })
    }, floatingActionButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtendedFloatingActionButton(onClick = {
                pendingFormat = "TXT"; picker.launch(arrayOf("text/plain", "*/*"))
            }) { Text("TXT") }
            ExtendedFloatingActionButton(onClick = {
                pendingFormat = "EPUB"; picker.launch(arrayOf("application/epub+zip"))
            }) { Text("EPUB") }
            ExtendedFloatingActionButton(onClick = {
                pendingFormat = "MOBI"; picker.launch(arrayOf("*/*"))
            }) { Text("MOBI") }
            ExtendedFloatingActionButton(onClick = {
                pendingFormat = "PDF"; picker.launch(arrayOf("application/pdf"))
            }) { Text("PDF") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(busy!!, Modifier.padding(8.dp)) }
            LazyVerticalGrid(GridCells.Adaptive(100.dp),
                Modifier.fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(books) { book ->
                    Column(Modifier
                        .clickable { onOpenBook(book.id, book.format) }
                        .background(Color(book.coverColor), RoundedCornerShape(10.dp))
                        .height(140.dp).width(100.dp).padding(8.dp),
                        verticalArrangement = Arrangement.Bottom) {
                        Text(book.title, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(book.format + " · ${book.progressPercent}%", color = Color(0xCCFFFFFF), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

suspend fun uriHash(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(uri.toString().toByteArray())
    bytes.joinToString("") { "%02x".format(it) }
}
