package com.comicreader.app.data.models

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Representa un cómic en la biblioteca
 */
data class Comic(
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val coverPath: String? = null,
    val format: ComicFormat,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val lastReadTimestamp: Long = 0,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

/**
 * Formatos de archivo soportados
 */
enum class ComicFormat(val extensions: List<String>) {
    CBR(listOf("cbr")),
    CBZ(listOf("cbz")),
    ZIP(listOf("zip")),
    RAR(listOf("rar")),
    PDF(listOf("pdf")),
    EPUB(listOf("epub")),
    IMAGE(listOf("jpg", "jpeg", "png", "webp", "gif"));
    
    companion object {
        fun fromExtension(extension: String): ComicFormat? {
            val ext = extension.lowercase()
            return entries.find { format -> 
                format.extensions.contains(ext) 
            }
        }
        
        fun getAllSupportedExtensions(): List<String> {
            return entries.flatMap { it.extensions }
        }
    }
}

/**
 * Representa una página del cómic
 */
data class ComicPage(
    val pageNumber: Int,
    val imagePath: String,
    val bitmap: Bitmap? = null,
    val panels: List<Panel> = emptyList(),
    val width: Int = 0,
    val height: Int = 0
)

/**
 * Representa un panel detectado en una página
 */
data class Panel(
    val id: Int,
    val bounds: RectF,          // Coordenadas del panel (left, top, right, bottom)
    val readingOrder: Int,       // Orden de lectura (0, 1, 2, ...)
    val confidence: Float = 1f   // Confianza en la detección (0-1)
) {
    val width: Float get() = bounds.width()
    val height: Float get() = bounds.height()
    val centerX: Float get() = bounds.centerX()
    val centerY: Float get() = bounds.centerY()
    val area: Float get() = width * height
}

/**
 * Configuración de lectura
 */
data class ReadingSettings(
    val readingMode: ReadingMode = ReadingMode.PAGE,
    val readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    val autoDetectPanels: Boolean = true,
    val panelTransitionAnimation: PanelTransition = PanelTransition.SLIDE,
    val backgroundColor: Int = 0xFF000000.toInt(),
    val keepScreenOn: Boolean = true,
    val showPageNumber: Boolean = true,
    val panelZoomLevel: Float = 1.2f,
    val panelPadding: Float = 0.05f // Porcentaje de padding alrededor del panel
)

/**
 * Modos de lectura
 */
enum class ReadingMode {
    PAGE,           // Página completa
    PANEL,          // Panel por panel
    CONTINUOUS,     // Scroll continuo vertical
    WEBTOON         // Optimizado para webtoons (scroll vertical largo)
}

/**
 * Dirección de lectura
 */
enum class ReadingDirection {
    LEFT_TO_RIGHT,  // Occidental (izquierda a derecha, arriba a abajo)
    RIGHT_TO_LEFT   // Manga (derecha a izquierda, arriba a abajo)
}

/**
 * Animaciones de transición entre paneles
 */
enum class PanelTransition {
    NONE,
    SLIDE,
    FADE,
    ZOOM
}

/**
 * Estado de la lectura actual
 */
data class ReadingState(
    val comic: Comic,
    val currentPage: Int = 0,
    val currentPanel: Int = 0,
    val totalPanels: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val pages: List<ComicPage> = emptyList()
)

/**
 * Resultado de la extracción de archivos
 */
sealed class ExtractionResult {
    data class Success(val pages: List<ComicPage>) : ExtractionResult()
    data class Error(val message: String, val exception: Throwable? = null) : ExtractionResult()
    data class Progress(val current: Int, val total: Int) : ExtractionResult()
}

/**
 * Resultado de la detección de paneles
 */
sealed class PanelDetectionResult {
    data class Success(val panels: List<Panel>) : PanelDetectionResult()
    data class Error(val message: String) : PanelDetectionResult()
    object NoPanelsFound : PanelDetectionResult()
}
