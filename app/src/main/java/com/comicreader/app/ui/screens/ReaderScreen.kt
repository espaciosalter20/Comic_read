package com.comicreader.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.comicreader.app.data.models.*
import com.comicreader.app.ui.viewmodels.ComicReaderViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ComicReaderViewModel,
    onNavigateBack: () -> Unit
) {
    val readingState by viewModel.readingState.collectAsState()
    val readingSettings by viewModel.readingSettings.collectAsState()
    
    val state = readingState ?: return
    
    var showControls by remember { mutableStateOf(true) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    
    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(readingSettings.backgroundColor))
    ) {
        when {
            state.isLoading -> {
                LoadingView(state.error ?: "Cargando...")
            }
            state.error != null && state.pages.isEmpty() -> {
                ErrorView(state.error!!, onRetry = { /* Retry logic */ })
            }
            else -> {
                when (readingSettings.readingMode) {
                    ReadingMode.PAGE -> PageModeReader(
                        state = state,
                        settings = readingSettings,
                        onTap = { showControls = !showControls },
                        onPageChange = { viewModel.goToPage(it) }
                    )
                    ReadingMode.PANEL -> PanelModeReader(
                        state = state,
                        settings = readingSettings,
                        viewModel = viewModel,
                        onTap = { showControls = !showControls }
                    )
                    ReadingMode.CONTINUOUS -> ContinuousModeReader(
                        state = state,
                        settings = readingSettings,
                        onTap = { showControls = !showControls }
                    )
                    ReadingMode.WEBTOON -> WebtoonModeReader(
                        state = state,
                        settings = readingSettings,
                        onTap = { showControls = !showControls }
                    )
                }
            }
        }
        
        // Controles superpuestos
        if (showControls) {
            ReaderControls(
                state = state,
                settings = readingSettings,
                onBack = onNavigateBack,
                onSettingsClick = { showSettingsSheet = true },
                onModeChange = { viewModel.setReadingMode(it) },
                onPageChange = { viewModel.goToPage(it) }
            )
        }
        
        // Panel de ajustes
        if (showSettingsSheet) {
            SettingsBottomSheet(
                settings = readingSettings,
                onDismiss = { showSettingsSheet = false },
                onReadingModeChange = { viewModel.setReadingMode(it) },
                onDirectionChange = { viewModel.setReadingDirection(it) },
                onAutoDetectChange = { viewModel.setAutoDetectPanels(it) }
            )
        }
    }
}

@Composable
fun PageModeReader(
    state: ReadingState,
    settings: ReadingSettings,
    onTap: () -> Unit,
    onPageChange: (Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentPage,
        pageCount = { state.pages.size }
    )
    
    LaunchedEffect(pagerState.currentPage) {
        onPageChange(pagerState.currentPage)
    }
    
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage)
        }
    }
    
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() },
        reverseLayout = settings.readingDirection == ReadingDirection.RIGHT_TO_LEFT
    ) { pageIndex ->
        val page = state.pages[pageIndex]
        ZoomableImage(
            imagePath = page.imagePath,
            contentDescription = "Página ${pageIndex + 1}"
        )
    }
}

@Composable
fun PanelModeReader(
    state: ReadingState,
    settings: ReadingSettings,
    viewModel: ComicReaderViewModel,
    onTap: () -> Unit
) {
    val currentPage = state.pages.getOrNull(state.currentPage)
    val currentPanel = currentPage?.panels?.getOrNull(state.currentPanel)
    
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Animación de transición
    val animatedScale by animateFloatAsState(
        targetValue = if (currentPanel != null) settings.panelZoomLevel else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "panelScale"
    )
    
    val animatedOffsetX by animateFloatAsState(
        targetValue = calculatePanelOffsetX(currentPanel, currentPage, containerSize),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "panelOffsetX"
    )
    
    val animatedOffsetY by animateFloatAsState(
        targetValue = calculatePanelOffsetY(currentPanel, currentPage, containerSize),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "panelOffsetY"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = containerSize.width
                        when {
                            offset.x < width * 0.3f -> viewModel.previousPanel()
                            offset.x > width * 0.7f -> viewModel.nextPanel()
                            else -> onTap()
                        }
                    },
                    onDoubleTap = {
                        // Reset zoom
                        viewModel.setReadingMode(ReadingMode.PAGE)
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (abs(dragAmount) > 50) {
                        if (dragAmount > 0) {
                            when (settings.readingDirection) {
                                ReadingDirection.LEFT_TO_RIGHT -> viewModel.previousPanel()
                                ReadingDirection.RIGHT_TO_LEFT -> viewModel.nextPanel()
                            }
                        } else {
                            when (settings.readingDirection) {
                                ReadingDirection.LEFT_TO_RIGHT -> viewModel.nextPanel()
                                ReadingDirection.RIGHT_TO_LEFT -> viewModel.previousPanel()
                            }
                        }
                    }
                }
            }
    ) {
        if (currentPage != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentPage.imagePath)
                    .crossfade(true)
                    .build(),
                contentDescription = "Página ${state.currentPage + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        translationX = animatedOffsetX
                        translationY = animatedOffsetY
                    }
            )
            
            // Indicador de panel actual
            if (currentPanel != null && currentPage.panels.size > 1) {
                PanelIndicator(
                    currentPanel = state.currentPanel,
                    totalPanels = currentPage.panels.size,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun ContinuousModeReader(
    state: ReadingState,
    settings: ReadingSettings,
    onTap: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
    ) {
        items(state.pages.size) { pageIndex ->
            val page = state.pages[pageIndex]
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(page.imagePath)
                    .crossfade(true)
                    .build(),
                contentDescription = "Página ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun WebtoonModeReader(
    state: ReadingState,
    settings: ReadingSettings,
    onTap: () -> Unit
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() },
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(state.pages.size) { pageIndex ->
            val page = state.pages[pageIndex]
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(page.imagePath)
                    .crossfade(true)
                    .build(),
                contentDescription = "Página ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ZoomableImage(
    imagePath: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offset = Offset(
                        x = (offset.x + pan.x).coerceIn(-1000f, 1000f),
                        y = (offset.y + pan.y).coerceIn(-1000f, 1000f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // Reset zoom
                        scale = 1f
                        offset = Offset.Zero
                    }
                )
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imagePath)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderControls(
    state: ReadingState,
    settings: ReadingSettings,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onModeChange: (ReadingMode) -> Unit,
    onPageChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top bar
        TopAppBar(
            title = { Text(state.comic.title, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Volver")
                }
            },
            actions = {
                // Botón de modo de lectura
                IconButton(onClick = {
                    val nextMode = when (settings.readingMode) {
                        ReadingMode.PAGE -> ReadingMode.PANEL
                        ReadingMode.PANEL -> ReadingMode.CONTINUOUS
                        ReadingMode.CONTINUOUS -> ReadingMode.WEBTOON
                        ReadingMode.WEBTOON -> ReadingMode.PAGE
                    }
                    onModeChange(nextMode)
                }) {
                    Icon(
                        when (settings.readingMode) {
                            ReadingMode.PAGE -> Icons.Default.Fullscreen
                            ReadingMode.PANEL -> Icons.Default.GridView
                            ReadingMode.CONTINUOUS -> Icons.Default.ViewAgenda
                            ReadingMode.WEBTOON -> Icons.Default.ViewDay
                        },
                        contentDescription = "Modo: ${settings.readingMode.name}"
                    )
                }
                
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Ajustes")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.7f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Bottom bar con slider de página
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Página ${state.currentPage + 1}",
                        color = Color.White
                    )
                    Text(
                        "de ${state.pages.size}",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                if (state.pages.size > 1) {
                    Slider(
                        value = state.currentPage.toFloat(),
                        onValueChange = { onPageChange(it.toInt()) },
                        valueRange = 0f..(state.pages.size - 1).toFloat(),
                        steps = state.pages.size - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White
                        )
                    )
                }
                
                // Info del panel si está en modo panel
                if (settings.readingMode == ReadingMode.PANEL) {
                    val currentPage = state.pages.getOrNull(state.currentPage)
                    if (currentPage != null && currentPage.panels.isNotEmpty()) {
                        Text(
                            "Panel ${state.currentPanel + 1} de ${currentPage.panels.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: ReadingSettings,
    onDismiss: () -> Unit,
    onReadingModeChange: (ReadingMode) -> Unit,
    onDirectionChange: (ReadingDirection) -> Unit,
    onAutoDetectChange: (Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Ajustes de lectura",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Modo de lectura
            Text("Modo de lectura", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.readingMode == mode,
                        onClick = { onReadingModeChange(mode) },
                        label = { Text(mode.name) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Dirección de lectura
            Text("Dirección de lectura", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.readingDirection == ReadingDirection.LEFT_TO_RIGHT,
                    onClick = { onDirectionChange(ReadingDirection.LEFT_TO_RIGHT) },
                    label = { Text("Occidental →") }
                )
                FilterChip(
                    selected = settings.readingDirection == ReadingDirection.RIGHT_TO_LEFT,
                    onClick = { onDirectionChange(ReadingDirection.RIGHT_TO_LEFT) },
                    label = { Text("← Manga") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Detección automática de paneles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detectar paneles automáticamente")
                Switch(
                    checked = settings.autoDetectPanels,
                    onCheckedChange = onAutoDetectChange
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PanelIndicator(
    currentPanel: Int,
    totalPanels: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp)
            .background(
                Color.Black.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(totalPanels) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (index == currentPanel) Color.White else Color.White.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

@Composable
fun LoadingView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

// Funciones de cálculo para la animación del panel
private fun calculatePanelOffsetX(
    panel: Panel?,
    page: ComicPage?,
    containerSize: IntSize
): Float {
    if (panel == null || page == null || containerSize.width == 0) return 0f
    
    val scaleX = containerSize.width.toFloat() / page.width
    val panelCenterX = panel.bounds.centerX() * scaleX
    val screenCenterX = containerSize.width / 2f
    
    return screenCenterX - panelCenterX
}

private fun calculatePanelOffsetY(
    panel: Panel?,
    page: ComicPage?,
    containerSize: IntSize
): Float {
    if (panel == null || page == null || containerSize.height == 0) return 0f
    
    val scaleY = containerSize.height.toFloat() / page.height
    val panelCenterY = panel.bounds.centerY() * scaleY
    val screenCenterY = containerSize.height / 2f
    
    return screenCenterY - panelCenterY
}
