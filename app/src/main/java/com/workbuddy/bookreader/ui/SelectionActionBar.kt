package com.workbuddy.bookreader.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 划线/翻译/朗读/笔记 操作栏 */
@Composable
fun SelectionActionBar(
    onHighlight: (Long) -> Unit,
    onTranslate: () -> Unit,
    onSpeak: () -> Unit,
    onNote: () -> Unit
) {
    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp)) {
            // 多色划线
            listOf(0xFFFFF176, 0xFFA5D6A7, 0xFF90CAF9, 0xFFF48FB1).forEach { c ->
                TextButton(onClick = { onHighlight(c) }) {
                    Text("▬", color = Color(c))
                }
            }
            TextButton(onClick = onTranslate) { Text("翻译") }
            TextButton(onClick = onSpeak) { Text("朗读") }
            TextButton(onClick = onNote) { Text("笔记") }
        }
    }
}
