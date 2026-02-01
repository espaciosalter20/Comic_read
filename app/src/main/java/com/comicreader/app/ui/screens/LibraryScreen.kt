package com.comicreader.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.comicreader.app.data.models.Comic
import com.comicreader.app.data.models.ComicFormat
import com.comicreader.app.ui.viewmodels.ComicReaderViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: ComicReaderViewModel,
    onNavigateToReader: () -> Unit
) {
    val context = LocalContext.current
    val libraryState by viewModel.libraryState.collectAsState()
    
    // Selector de archivos
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Copiar archivo a caché y abrirlo
            val inputStream = context.contentResolver.openInputStream(it)
            val fileName = getFileNameFromUri(context, it) ?: "comic"
            val tempFile = File(context.cacheDir, fileName)
            
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            viewModel.openComic(tempFile)
            onNavigateToReader()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biblioteca de Cómics") },
                actions = {
                    IconButton(onClick = {
                        // Abrir selector de archivos
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/epub+zip",
                                "application/x-cbr",
                                "application/x-cbz",
                                "application/zip",
                                "application/x-rar-compressed",
                                "image/*"
                            )
                        )
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir cómic")
                    }
                    IconButton(onClick = { viewModel.clearCache() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Limpiar caché")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/epub+zip",
                            "application/x-cbr",
                            "application/x-cbz",
                            "application/zip",
                            "application/x-rar-compressed",
                            "image/*"
                        )
                    )
                },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                text = { Text("Abrir archivo") }
            )
        }
    ) { paddingValues ->
        if (libraryState.comics.isEmpty()) {
            EmptyLibraryView(
                onOpenFile = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/epub+zip",
                            "application/x-cbr",
                            "application/x-cbz",
                            "application/zip",
                            "application/x-rar-compressed",
                            "image/*"
                        )
                    )
                },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(paddingValues)
            ) {
                items(libraryState.comics) { comic ->
                    ComicCard(
                        comic = comic,
                        onClick = {
                            viewModel.openComic(File(comic.filePath))
                            onNavigateToReader()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryView(
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            
            Text(
                "Tu biblioteca está vacía",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                "Abre un archivo para comenzar a leer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(onClick = onOpenFile) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Abrir archivo")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Formatos soportados:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CBR", "CBZ", "PDF", "EPUB", "ZIP", "RAR", "JPG").forEach { format ->
                    SuggestionChip(
                        onClick = { },
                        label = { Text(format) }
                    )
                }
            }
        }
    }
}

@Composable
fun ComicCard(
    comic: Comic,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            // Portada
            if (comic.coverPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(comic.coverPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = comic.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (comic.format) {
                            ComicFormat.PDF -> Icons.Default.PictureAsPdf
                            ComicFormat.EPUB -> Icons.Default.Book
                            else -> Icons.Default.MenuBook
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Información en la parte inferior
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        comic.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            comic.format.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        
                        if (comic.currentPage > 0) {
                            Text(
                                "${comic.currentPage + 1}/${comic.totalPages}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    // Barra de progreso
                    if (comic.totalPages > 0 && comic.currentPage > 0) {
                        LinearProgressIndicator(
                            progress = { comic.currentPage.toFloat() / comic.totalPages },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
            
            // Badge de favorito
            if (comic.isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Favorito",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
        }
    }
}

// Utilidad para obtener nombre de archivo desde URI
private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var fileName: String? = null
    
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
    }
    
    if (fileName == null) {
        fileName = uri.path?.substringAfterLast('/')
    }
    
    return fileName
}
