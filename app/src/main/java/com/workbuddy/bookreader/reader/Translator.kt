package com.workbuddy.bookreader.reader

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 翻译引擎：ML Kit 翻译（首次下载离线模型，之后可离线使用）。
 * 支持中英日韩法德西俄等 50+ 语言互译。
 */
object Translator {

    private val translators = mutableMapOf<String, Translator>()

    /** 语言代码：源 -> 目标，如 "zh" -> "en" */
    fun translatorFor(source: String, target: String): Translator {
        val key = "$source-$target"
        return translators.getOrPut(key) {
            val opts = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.fromLanguageTag(source) ?: TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.ENGLISH)
                .build()
            com.google.mlkit.nl.translate.Translation.getClient(opts)
        }
    }

    /** 确保离线模型已下载 */
    suspend fun ensureModel(source: String, target: String): Unit =
        suspendCancellableCoroutine { cont ->
            val t = translatorFor(source, target)
            t.downloadModelIfNeeded(
                DownloadConditions.Builder().build()
            ).addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    suspend fun translate(text: String, source: String = "zh", target: String = "en"): String {
        ensureModel(source, target)
        return suspendCancellableCoroutine { cont ->
            translatorFor(source, target).translate(text)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    fun closeAll() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
