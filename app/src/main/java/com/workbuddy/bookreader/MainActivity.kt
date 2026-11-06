package com.workbuddy.bookreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.workbuddy.bookreader.ui.LibraryScreen
import com.workbuddy.bookreader.ui.ReaderScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialApp()
        }
    }
}

@Composable
fun MaterialApp() {
    val nav = rememberNavController()
    androidx.compose.material3.MaterialTheme {
        var currentBook by remember { mutableStateOf<Pair<String, String>?>(null) } // id to format
        NavHost(nav, startDestination = "library") {
            composable("library") {
                LibraryScreen(onOpenBook = { id, format ->
                    currentBook = id to format
                    nav.navigate("reader")
                })
            }
            composable("reader") {
                ReaderScreen(
                    bookId = currentBook?.first ?: "",
                    format = currentBook?.second ?: "TXT",
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
