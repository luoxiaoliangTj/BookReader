package com.workbuddy.bookreader.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * PDF 处理：
 * 1. PdfRenderer 逐页渲染成 Bitmap（原生无依赖）。
 * 2. ML Kit 中文文字识别（OCR）把页面转成文本。
 * 3. OCR 结果交给统一排版引擎，之后划线/翻译/朗读与其他格式完全一致。
 */
object PdfProcessor {

    /** 渲染 PDF 某页为 Bitmap */
    fun renderPage(ctx: Context, uri: Uri, pageIndex: Int, scale: Float = 2f): Bitmap {
        val fd = openFd(ctx, uri)
        fd.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount) { "页码越界" }
                renderer.openPage(pageIndex).use { page ->
                    val w = (page.width * scale).toInt()
                    val h = (page.height * scale).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    fun pageCount(ctx: Context, uri: Uri): Int {
        openFd(ctx, uri).use { pfd -> PdfRenderer(pfd).use { return it.pageCount } }
    }

    /** OCR 单页：返回识别出的文本行 */
    suspend fun ocrPage(ctx: Context, uri: Uri, pageIndex: Int): List<String> =
        withContext(Dispatchers.IO) {
            val bmp = renderPage(ctx, uri, pageIndex, scale = 2f)
            val image = InputImage.fromBitmap(bmp, 0)
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            try {
                val result = await(recognizer.process(image))
                result.textBlocks.flatMap { block -> block.lines.map { it.text } }
            } finally {
                recognizer.close()
                bmp.recycle()
            }
        }

    /** OCR 整本书（后台批量，可显示进度），返回 ParsedBook 结构 */
    suspend fun ocrWholePdf(
        ctx: Context, uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): com.workbuddy.bookreader.parser.ParsedBook = withContext(Dispatchers.IO) {
        val total = pageCount(ctx, uri)
        val chapters = mutableListOf<com.workbuddy.bookreader.parser.Chapter>()
        for (i in 0 until total) {
            val lines = ocrPage(ctx, uri, i)
            if (lines.isNotEmpty()) {
                chapters.add(com.workbuddy.bookreader.parser.Chapter("第 ${i + 1} 页", lines))
            }
            onProgress(i + 1, total)
        }
        if (chapters.isEmpty()) chapters.add(com.workbuddy.bookreader.parser.Chapter("空文档", listOf("未识别到文本")))
        com.workbuddy.bookreader.parser.ParsedBook(chapters, "PDF_OCR")
    }

    private fun openFd(ctx: Context, uri: Uri): ParcelFileDescriptor =
        ctx.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("无法打开 PDF 文件")

    /** 把 ML Kit Task 转成挂起函数 */
    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            task.addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
}
