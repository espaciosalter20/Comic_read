package com.comicreader.app.ui.viewmodels

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comicreader.app.core.detection.PanelDetector
import com.comicreader.app.core.detection.PanelDetectionConfig
import com.comicreader.app.core.extractors.ExtractorFactory
import com.comicreader.app.core.extractors.PageCache
import com.comicreader.app.data.models.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class ComicReaderViewModel(application: Application) : AndroidViewModel(application) {
    
    private val extractorFactory = ExtractorFactory(application)
    private val pageCache = PageCache(application)
    private val panelDetector = PanelDetector(PanelDetectionConfig())
    
    // Estado de la biblioteca
    private val _libraryState = MutableStateFlow(LibraryState())
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()
    
    // Estado de lectura
    private val _readingState = MutableStateFlow<ReadingState?>(null)
    val readingState: StateFlow<ReadingState?> = _readingState.asStateFlow()
    
    // Configuración de lectura
    private val _readingSettings = MutableStateFlow(ReadingSettings())
    val readingSettings: StateFlow<ReadingSettings> = _readingSettings.asStateFlow()
    
    // Eventos de UI
    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    
    /**
     * Abre un archivo de cómic
     */
    fun openComic(file: File) {
        viewModelScope.launch {
            val format = ComicFormat.fromExtension(file.extension)
            if (format == null) {
                _uiEvents.emit(UiEvent.ShowError("Formato no soportado: ${file.extension}"))
                return@launch
            }
            
            val extractor = extractorFactory.getExtractor(format)
            if (extractor == null) {
                _uiEvents.emit(UiEvent.ShowError("No se puede abrir este tipo de archivo"))
                return@launch
            }
            
            val comic = Comic(
                title = file.nameWithoutExtension,
                filePath = file.absolutePath,
                format = format
            )
            
            _readingState.value = ReadingState(
                comic = comic,
                isLoading = true
            )
            
            extractor.extract(file, pageCache.getCacheDir()).collect { result ->
                when (result) {
                    is ExtractionResult.Progress -> {
                        _readingState.update { state ->
                            state?.copy(
                                isLoading = true,
                                error = "Cargando página ${result.current} de ${result.total}"
                            )
                        }
                    }
                    is ExtractionResult.Success -> {
                        val pages = if (_readingSettings.value.autoDetectPanels) {
                            detectPanelsForPages(result.pages)
                        } else {
                            result.pages
                        }
                        
                        _readingState.update { state ->
                            state?.copy(
                                isLoading = false,
                                error = null,
                                pages = pages,
                                comic = comic.copy(totalPages = pages.size)
                            )
                        }
                    }
                    is ExtractionResult.Error -> {
                        _readingState.update { state ->
                            state?.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                        _uiEvents.emit(UiEvent.ShowError(result.message))
                    }
                }
            }
        }
    }
    
    /**
     * Detecta paneles para todas las páginas
     */
    private suspend fun detectPanelsForPages(pages: List<ComicPage>): List<ComicPage> {
        return pages.map { page ->
            val bitmap = BitmapFactory.decodeFile(page.imagePath)
            if (bitmap != null) {
                val result = panelDetector.detectPanels(
                    bitmap,
                    _readingSettings.value.readingDirection
                )
                bitmap.recycle()
                
                when (result) {
                    is PanelDetectionResult.Success -> page.copy(panels = result.panels)
                    else -> page
                }
            } else {
                page
            }
        }
    }
    
    /**
     * Detecta paneles para una página específica (lazy loading)
     */
    fun detectPanelsForPage(pageIndex: Int) {
        viewModelScope.launch {
            val state = _readingState.value ?: return@launch
            val page = state.pages.getOrNull(pageIndex) ?: return@launch
            
            if (page.panels.isNotEmpty()) return@launch // Ya detectados
            
            val bitmap = BitmapFactory.decodeFile(page.imagePath)
            if (bitmap != null) {
                val result = panelDetector.detectPanels(
                    bitmap,
                    _readingSettings.value.readingDirection
                )
                bitmap.recycle()
                
                if (result is PanelDetectionResult.Success) {
                    val updatedPages = state.pages.toMutableList()
                    updatedPages[pageIndex] = page.copy(panels = result.panels)
                    
                    _readingState.update { it?.copy(pages = updatedPages) }
                }
            }
        }
    }
    
    /**
     * Navega a la siguiente página
     */
    fun nextPage() {
        _readingState.update { state ->
            state?.let {
                val nextPage = (it.currentPage + 1).coerceAtMost(it.pages.size - 1)
                it.copy(currentPage = nextPage, currentPanel = 0)
            }
        }
    }
    
    /**
     * Navega a la página anterior
     */
    fun previousPage() {
        _readingState.update { state ->
            state?.let {
                val prevPage = (it.currentPage - 1).coerceAtLeast(0)
                it.copy(currentPage = prevPage, currentPanel = 0)
            }
        }
    }
    
    /**
     * Navega al siguiente panel
     */
    fun nextPanel() {
        _readingState.update { state ->
            state?.let {
                val currentPage = it.pages.getOrNull(it.currentPage) ?: return@let it
                val panelCount = currentPage.panels.size
                
                if (it.currentPanel < panelCount - 1) {
                    // Siguiente panel en la misma página
                    it.copy(currentPanel = it.currentPanel + 1)
                } else if (it.currentPage < it.pages.size - 1) {
                    // Primera panel de la siguiente página
                    it.copy(currentPage = it.currentPage + 1, currentPanel = 0)
                } else {
                    it
                }
            }
        }
    }
    
    /**
     * Navega al panel anterior
     */
    fun previousPanel() {
        _readingState.update { state ->
            state?.let {
                if (it.currentPanel > 0) {
                    // Panel anterior en la misma página
                    it.copy(currentPanel = it.currentPanel - 1)
                } else if (it.currentPage > 0) {
                    // Último panel de la página anterior
                    val prevPage = it.pages.getOrNull(it.currentPage - 1)
                    val lastPanel = (prevPage?.panels?.size ?: 1) - 1
                    it.copy(currentPage = it.currentPage - 1, currentPanel = lastPanel.coerceAtLeast(0))
                } else {
                    it
                }
            }
        }
    }
    
    /**
     * Navega a una página específica
     */
    fun goToPage(pageIndex: Int) {
        _readingState.update { state ->
            state?.let {
                val validIndex = pageIndex.coerceIn(0, it.pages.size - 1)
                it.copy(currentPage = validIndex, currentPanel = 0)
            }
        }
    }
    
    /**
     * Actualiza el modo de lectura
     */
    fun setReadingMode(mode: ReadingMode) {
        _readingSettings.update { it.copy(readingMode = mode) }
    }
    
    /**
     * Actualiza la dirección de lectura
     */
    fun setReadingDirection(direction: ReadingDirection) {
        _readingSettings.update { it.copy(readingDirection = direction) }
        
        // Re-detectar paneles con nueva dirección
        viewModelScope.launch {
            val state = _readingState.value ?: return@launch
            val updatedPages = detectPanelsForPages(state.pages)
            _readingState.update { it?.copy(pages = updatedPages) }
        }
    }
    
    /**
     * Activa/desactiva detección automática de paneles
     */
    fun setAutoDetectPanels(enabled: Boolean) {
        _readingSettings.update { it.copy(autoDetectPanels = enabled) }
    }
    
    /**
     * Cierra el cómic actual
     */
    fun closeComic() {
        _readingState.value = null
    }
    
    /**
     * Limpia la caché
     */
    fun clearCache() {
        viewModelScope.launch {
            pageCache.clearCache()
            _uiEvents.emit(UiEvent.ShowMessage("Caché limpiada"))
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            pageCache.trimCache()
        }
    }
}

/**
 * Estado de la biblioteca
 */
data class LibraryState(
    val comics: List<Comic> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.TITLE
)

enum class SortOrder {
    TITLE,
    DATE_ADDED,
    LAST_READ
}

/**
 * Eventos de UI
 */
sealed class UiEvent {
    data class ShowError(val message: String) : UiEvent()
    data class ShowMessage(val message: String) : UiEvent()
    object NavigateToReader : UiEvent()
    object NavigateBack : UiEvent()
}
