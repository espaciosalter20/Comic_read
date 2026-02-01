package com.comicreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.comicreader.app.ui.screens.LibraryScreen
import com.comicreader.app.ui.screens.ReaderScreen
import com.comicreader.app.ui.theme.ComicReaderTheme
import com.comicreader.app.ui.viewmodels.ComicReaderViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: ComicReaderViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            ComicReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ComicReaderApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun ComicReaderApp(viewModel: ComicReaderViewModel) {
    val navController = rememberNavController()
    
    // Observar eventos de UI
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is com.comicreader.app.ui.viewmodels.UiEvent.NavigateToReader -> {
                    navController.navigate("reader")
                }
                is com.comicreader.app.ui.viewmodels.UiEvent.NavigateBack -> {
                    navController.popBackStack()
                }
                else -> { /* Manejar otros eventos */ }
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = "library"
    ) {
        composable("library") {
            LibraryScreen(
                viewModel = viewModel,
                onNavigateToReader = {
                    navController.navigate("reader")
                }
            )
        }
        
        composable("reader") {
            ReaderScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    viewModel.closeComic()
                    navController.popBackStack()
                }
            )
        }
    }
}
