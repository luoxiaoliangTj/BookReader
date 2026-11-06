package com.workbuddy.bookreader.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * 朗读引擎：系统 TTS，支持段落队列朗读、语速/音调调节、
 * 当前朗读位置回调（用于高亮跟读）、暂停/继续。
 */
class ReaderTTS(context: Context) {

    var speed = 1.0f
    var pitch = 1.0f

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingInit: MutableList<(Boolean) -> Unit> = mutableListOf()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.CHINA
            }
            synchronized(pendingInit) { pendingInit.forEach { it(ready) }; pendingInit.clear() }
        }
    }

    suspend fun awaitReady(): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        if (ready) cont.resume(true, null)
        else synchronized(pendingInit) {
            pendingInit.add { ok -> if (cont.isActive) cont.resume(ok, null) }
        }
    }

    /** 依次朗读一段文本列表，通过 flow 回调当前段落索引（用于跟读高亮） */
    fun speak(paragraphs: List<String>, startIndex: Int = 0): Flow<Int> = callbackFlow {
        var idx = startIndex
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { trySend(idx) }
            override fun onDone(utteranceId: String?) {
                idx++
                if (idx < paragraphs.size) {
                    speakOne(paragraphs[idx], idx)
                    trySend(idx)
                } else close()
            }
            override fun onError(utteranceId: String?) { close() }
        })
        if (idx < paragraphs.size) speakOne(paragraphs[idx], idx)
        awaitClose { tts?.stop() }
    }

    private fun speakOne(text: String, idx: Int) {
        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)
        tts?.speak(text.take(3800), TextToSpeech.QUEUE_ADD, null, "utter_$idx")
    }

    fun pause() { tts?.stop() }

    fun shutdown() {
        tts?.stop(); tts?.shutdown(); tts = null
    }
}
