package com.comicreader.app.core.detection

import android.graphics.Bitmap
import android.graphics.RectF
import com.comicreader.app.data.models.Panel
import com.comicreader.app.data.models.PanelDetectionResult
import com.comicreader.app.data.models.ReadingDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Detector de paneles para cómics usando procesamiento de imágenes.
 * 
 * Algoritmo:
 * 1. Convertir imagen a escala de grises
 * 2. Aplicar detección de bordes (Sobel/Canny)
 * 3. Encontrar líneas horizontales y verticales (gutters)
 * 4. Crear rejilla de paneles basada en intersecciones
 * 5. Filtrar y ordenar paneles según dirección de lectura
 */
class PanelDetector(
    private val config: PanelDetectionConfig = PanelDetectionConfig()
) {
    
    /**
     * Detecta paneles en una imagen de página de cómic
     */
    suspend fun detectPanels(
        bitmap: Bitmap,
        readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT
    ): PanelDetectionResult = withContext(Dispatchers.Default) {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 1. Convertir a escala de grises
            val grayscale = toGrayscale(bitmap)
            
            // 2. Detectar bordes
            val edges = detectEdges(grayscale, width, height)
            
            // 3. Encontrar líneas (gutters) horizontales y verticales
            val horizontalLines = findHorizontalLines(edges, width, height)
            val verticalLines = findVerticalLines(edges, width, height)
            
            // 4. Agregar bordes de la imagen como líneas
            val allHorizontal = (listOf(0, height - 1) + horizontalLines).distinct().sorted()
            val allVertical = (listOf(0, width - 1) + verticalLines).distinct().sorted()
            
            // 5. Crear paneles a partir de la rejilla
            val rawPanels = createPanelsFromGrid(allHorizontal, allVertical, width, height)
            
            // 6. Filtrar paneles muy pequeños o muy grandes
            val filteredPanels = filterPanels(rawPanels, width, height)
            
            // 7. Fusionar paneles que se superpongan
            val mergedPanels = mergePanels(filteredPanels)
            
            // 8. Ordenar según dirección de lectura
            val orderedPanels = orderPanels(mergedPanels, readingDirection)
            
            if (orderedPanels.isEmpty()) {
                // Si no se detectan paneles, tratar toda la página como un panel
                val fullPagePanel = Panel(
                    id = 0,
                    bounds = RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    readingOrder = 0,
                    confidence = 0.5f
                )
                PanelDetectionResult.Success(listOf(fullPagePanel))
            } else {
                PanelDetectionResult.Success(orderedPanels)
            }
        } catch (e: Exception) {
            PanelDetectionResult.Error("Error detectando paneles: ${e.message}")
        }
    }
    
    /**
     * Convierte bitmap a array de valores en escala de grises
     */
    private fun toGrayscale(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Fórmula de luminancia
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }.toIntArray()
    }
    
    /**
     * Detecta bordes usando el operador Sobel
     */
    private fun detectEdges(grayscale: IntArray, width: Int, height: Int): IntArray {
        val edges = IntArray(width * height)
        
        // Kernels de Sobel
        val sobelX = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        val sobelY = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = grayscale[(y + ky) * width + (x + kx)]
                        gx += pixel * sobelX[ky + 1][kx + 1]
                        gy += pixel * sobelY[ky + 1][kx + 1]
                    }
                }
                
                val magnitude = min(255, kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt())
                edges[y * width + x] = if (magnitude > config.edgeThreshold) 255 else 0
            }
        }
        
        return edges
    }
    
    /**
     * Encuentra líneas horizontales (gutters entre filas de paneles)
     */
    private fun findHorizontalLines(edges: IntArray, width: Int, height: Int): List<Int> {
        val lines = mutableListOf<Int>()
        val minGutterWidth = (width * config.minGutterRatio).toInt()
        
        for (y in config.marginPixels until height - config.marginPixels) {
            var consecutiveWhite = 0
            var maxConsecutive = 0
            
            for (x in 0 until width) {
                // Un gutter es una línea con pocos bordes (área blanca/vacía)
                if (edges[y * width + x] == 0) {
                    consecutiveWhite++
                    maxConsecutive = max(maxConsecutive, consecutiveWhite)
                } else {
                    consecutiveWhite = 0
                }
            }
            
            // Si la mayor parte de la línea es "vacía", es un gutter
            if (maxConsecutive > minGutterWidth) {
                // Evitar líneas muy cercanas
                if (lines.isEmpty() || y - lines.last() > config.minPanelSize) {
                    lines.add(y)
                }
            }
        }
        
        return lines
    }
    
    /**
     * Encuentra líneas verticales (gutters entre columnas de paneles)
     */
    private fun findVerticalLines(edges: IntArray, width: Int, height: Int): List<Int> {
        val lines = mutableListOf<Int>()
        val minGutterHeight = (height * config.minGutterRatio).toInt()
        
        for (x in config.marginPixels until width - config.marginPixels) {
            var consecutiveWhite = 0
            var maxConsecutive = 0
            
            for (y in 0 until height) {
                if (edges[y * width + x] == 0) {
                    consecutiveWhite++
                    maxConsecutive = max(maxConsecutive, consecutiveWhite)
                } else {
                    consecutiveWhite = 0
                }
            }
            
            if (maxConsecutive > minGutterHeight) {
                if (lines.isEmpty() || x - lines.last() > config.minPanelSize) {
                    lines.add(x)
                }
            }
        }
        
        return lines
    }
    
    /**
     * Crea paneles a partir de las intersecciones de líneas
     */
    private fun createPanelsFromGrid(
        horizontalLines: List<Int>,
        verticalLines: List<Int>,
        width: Int,
        height: Int
    ): List<Panel> {
        val panels = mutableListOf<Panel>()
        var panelId = 0
        
        for (i in 0 until horizontalLines.size - 1) {
            for (j in 0 until verticalLines.size - 1) {
                val left = verticalLines[j].toFloat()
                val top = horizontalLines[i].toFloat()
                val right = verticalLines[j + 1].toFloat()
                val bottom = horizontalLines[i + 1].toFloat()
                
                // Agregar un pequeño padding para evitar incluir el gutter
                val padding = config.gutterPadding
                val bounds = RectF(
                    left + padding,
                    top + padding,
                    right - padding,
                    bottom - padding
                )
                
                if (bounds.width() > 0 && bounds.height() > 0) {
                    panels.add(
                        Panel(
                            id = panelId++,
                            bounds = bounds,
                            readingOrder = 0, // Se asignará después
                            confidence = calculateConfidence(bounds, width, height)
                        )
                    )
                }
            }
        }
        
        return panels
    }
    
    /**
     * Filtra paneles que son muy pequeños o muy grandes
     */
    private fun filterPanels(panels: List<Panel>, width: Int, height: Int): List<Panel> {
        val totalArea = width.toFloat() * height.toFloat()
        val minArea = totalArea * config.minPanelAreaRatio
        val maxArea = totalArea * config.maxPanelAreaRatio
        
        return panels.filter { panel ->
            val area = panel.area
            area in minArea..maxArea &&
            panel.width > config.minPanelSize &&
            panel.height > config.minPanelSize
        }
    }
    
    /**
     * Fusiona paneles que se superponen significativamente
     */
    private fun mergePanels(panels: List<Panel>): List<Panel> {
        if (panels.size <= 1) return panels
        
        val merged = mutableListOf<Panel>()
        val used = BooleanArray(panels.size)
        
        for (i in panels.indices) {
            if (used[i]) continue
            
            var currentBounds = panels[i].bounds
            used[i] = true
            
            for (j in i + 1 until panels.size) {
                if (used[j]) continue
                
                val overlap = calculateOverlap(currentBounds, panels[j].bounds)
                if (overlap > config.mergeOverlapThreshold) {
                    // Fusionar bounds
                    currentBounds = RectF(
                        min(currentBounds.left, panels[j].bounds.left),
                        min(currentBounds.top, panels[j].bounds.top),
                        max(currentBounds.right, panels[j].bounds.right),
                        max(currentBounds.bottom, panels[j].bounds.bottom)
                    )
                    used[j] = true
                }
            }
            
            merged.add(panels[i].copy(bounds = currentBounds))
        }
        
        return merged
    }
    
    /**
     * Ordena los paneles según la dirección de lectura
     */
    private fun orderPanels(
        panels: List<Panel>,
        direction: ReadingDirection
    ): List<Panel> {
        // Agrupar paneles por "fila" (similar posición vertical)
        val rowThreshold = panels.minOfOrNull { it.height }?.times(0.5f) ?: 50f
        val rows = mutableListOf<MutableList<Panel>>()
        
        val sortedByTop = panels.sortedBy { it.bounds.top }
        
        for (panel in sortedByTop) {
            val existingRow = rows.find { row ->
                row.any { abs(it.bounds.centerY() - panel.bounds.centerY()) < rowThreshold }
            }
            
            if (existingRow != null) {
                existingRow.add(panel)
            } else {
                rows.add(mutableListOf(panel))
            }
        }
        
        // Ordenar cada fila horizontalmente
        val ordered = mutableListOf<Panel>()
        var order = 0
        
        for (row in rows) {
            val sortedRow = when (direction) {
                ReadingDirection.LEFT_TO_RIGHT -> row.sortedBy { it.bounds.left }
                ReadingDirection.RIGHT_TO_LEFT -> row.sortedByDescending { it.bounds.left }
            }
            
            for (panel in sortedRow) {
                ordered.add(panel.copy(readingOrder = order++))
            }
        }
        
        return ordered
    }
    
    /**
     * Calcula la confianza de la detección basada en el tamaño del panel
     */
    private fun calculateConfidence(bounds: RectF, imageWidth: Int, imageHeight: Int): Float {
        val panelArea = bounds.width() * bounds.height()
        val imageArea = imageWidth.toFloat() * imageHeight.toFloat()
        val areaRatio = panelArea / imageArea
        
        // Paneles entre 5% y 50% del área total tienen mayor confianza
        return when {
            areaRatio < 0.02f -> 0.3f
            areaRatio < 0.05f -> 0.6f
            areaRatio < 0.5f -> 0.9f
            areaRatio < 0.8f -> 0.7f
            else -> 0.5f
        }
    }
    
    /**
     * Calcula el porcentaje de superposición entre dos rectángulos
     */
    private fun calculateOverlap(a: RectF, b: RectF): Float {
        val intersectLeft = max(a.left, b.left)
        val intersectTop = max(a.top, b.top)
        val intersectRight = min(a.right, b.right)
        val intersectBottom = min(a.bottom, b.bottom)
        
        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }
        
        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val minArea = min(a.width() * a.height(), b.width() * b.height())
        
        return intersectArea / minArea
    }
}

/**
 * Configuración para la detección de paneles
 */
data class PanelDetectionConfig(
    val edgeThreshold: Int = 50,              // Umbral para detección de bordes
    val minGutterRatio: Float = 0.6f,         // Mínimo ratio de línea vacía para ser gutter
    val minPanelSize: Int = 100,              // Tamaño mínimo de panel en píxeles
    val minPanelAreaRatio: Float = 0.02f,     // Área mínima del panel (2% de la página)
    val maxPanelAreaRatio: Float = 0.95f,     // Área máxima del panel (95% de la página)
    val marginPixels: Int = 20,               // Margen a ignorar en los bordes
    val gutterPadding: Float = 5f,            // Padding dentro de los paneles
    val mergeOverlapThreshold: Float = 0.3f   // Umbral para fusionar paneles superpuestos
)

/**
 * Implementación alternativa usando algoritmo de detección basado en contornos
 * Más precisa para cómics con paneles irregulares
 */
class AdvancedPanelDetector(
    private val config: PanelDetectionConfig = PanelDetectionConfig()
) {
    
    /**
     * Detecta paneles usando análisis de contornos conectados
     */
    suspend fun detectPanelsAdvanced(
        bitmap: Bitmap,
        readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT
    ): PanelDetectionResult = withContext(Dispatchers.Default) {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 1. Preprocesamiento
            val grayscale = toGrayscale(bitmap)
            val binary = binarize(grayscale, width, height)
            
            // 2. Dilatar para cerrar pequeños gaps
            val dilated = dilate(binary, width, height, 3)
            
            // 3. Encontrar componentes conectados (regiones blancas = contenido)
            val components = findConnectedComponents(dilated, width, height)
            
            // 4. Convertir componentes a paneles
            val panels = components.mapIndexed { index, bounds ->
                Panel(
                    id = index,
                    bounds = bounds,
                    readingOrder = 0,
                    confidence = calculatePanelConfidence(bounds, width, height)
                )
            }
            
            // 5. Filtrar y ordenar
            val filtered = panels.filter { it.area > width * height * config.minPanelAreaRatio }
            val ordered = orderPanelsByReading(filtered, readingDirection)
            
            if (ordered.isEmpty()) {
                PanelDetectionResult.NoPanelsFound
            } else {
                PanelDetectionResult.Success(ordered)
            }
        } catch (e: Exception) {
            PanelDetectionResult.Error(e.message ?: "Error desconocido")
        }
    }
    
    private fun toGrayscale(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }.toIntArray()
    }
    
    /**
     * Binarización usando umbral adaptativo de Otsu
     */
    private fun binarize(grayscale: IntArray, width: Int, height: Int): BooleanArray {
        // Calcular histograma
        val histogram = IntArray(256)
        grayscale.forEach { histogram[it]++ }
        
        // Método de Otsu para encontrar umbral óptimo
        val total = grayscale.size
        var sum = 0
        for (i in 0..255) sum += i * histogram[i]
        
        var sumB = 0
        var wB = 0
        var maxVariance = 0.0
        var threshold = 0
        
        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0) continue
            
            val wF = total - wB
            if (wF == 0) break
            
            sumB += t * histogram[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            
            val variance = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }
        
        // Aplicar umbral - invertimos porque queremos el contenido como true
        return grayscale.map { it < threshold }.toBooleanArray()
    }
    
    /**
     * Operación de dilatación morfológica
     */
    private fun dilate(binary: BooleanArray, width: Int, height: Int, kernelSize: Int): BooleanArray {
        val result = BooleanArray(binary.size)
        val half = kernelSize / 2
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var hasNeighbor = false
                
                outer@ for (ky in -half..half) {
                    for (kx in -half..half) {
                        val ny = y + ky
                        val nx = x + kx
                        
                        if (ny in 0 until height && nx in 0 until width) {
                            if (binary[ny * width + nx]) {
                                hasNeighbor = true
                                break@outer
                            }
                        }
                    }
                }
                
                result[y * width + x] = hasNeighbor
            }
        }
        
        return result
    }
    
    /**
     * Encuentra componentes conectados y retorna sus bounding boxes
     */
    private fun findConnectedComponents(binary: BooleanArray, width: Int, height: Int): List<RectF> {
        val labels = IntArray(binary.size) { -1 }
        var currentLabel = 0
        
        // Primera pasada: etiquetar componentes
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!binary[idx] || labels[idx] >= 0) continue
                
                // BFS para encontrar componente conectado
                val queue = ArrayDeque<Int>()
                queue.add(idx)
                labels[idx] = currentLabel
                
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    val cy = current / width
                    val cx = current % width
                    
                    // 4-conectividad
                    val neighbors = listOf(
                        (cy - 1) * width + cx,
                        (cy + 1) * width + cx,
                        cy * width + (cx - 1),
                        cy * width + (cx + 1)
                    )
                    
                    for (neighbor in neighbors) {
                        val ny = neighbor / width
                        val nx = neighbor % width
                        
                        if (ny in 0 until height && nx in 0 until width &&
                            binary[neighbor] && labels[neighbor] < 0) {
                            labels[neighbor] = currentLabel
                            queue.add(neighbor)
                        }
                    }
                }
                
                currentLabel++
            }
        }
        
        // Segunda pasada: calcular bounding boxes
        val bounds = Array(currentLabel) { 
            floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, 0f, 0f) // left, top, right, bottom
        }
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val label = labels[y * width + x]
                if (label >= 0) {
                    bounds[label][0] = min(bounds[label][0], x.toFloat())
                    bounds[label][1] = min(bounds[label][1], y.toFloat())
                    bounds[label][2] = max(bounds[label][2], x.toFloat())
                    bounds[label][3] = max(bounds[label][3], y.toFloat())
                }
            }
        }
        
        return bounds.map { RectF(it[0], it[1], it[2], it[3]) }
    }
    
    private fun calculatePanelConfidence(bounds: RectF, width: Int, height: Int): Float {
        val aspectRatio = bounds.width() / bounds.height()
        val areaRatio = (bounds.width() * bounds.height()) / (width * height)
        
        // Penalizar paneles con aspect ratio muy extremo
        val aspectScore = when {
            aspectRatio < 0.2f || aspectRatio > 5f -> 0.5f
            aspectRatio < 0.5f || aspectRatio > 2f -> 0.8f
            else -> 1f
        }
        
        // Penalizar paneles muy pequeños o muy grandes
        val areaScore = when {
            areaRatio < 0.05f -> 0.5f
            areaRatio < 0.1f -> 0.8f
            areaRatio > 0.8f -> 0.6f
            else -> 1f
        }
        
        return aspectScore * areaScore
    }
    
    private fun orderPanelsByReading(
        panels: List<Panel>,
        direction: ReadingDirection
    ): List<Panel> {
        // Similar al método en PanelDetector
        val rowThreshold = panels.minOfOrNull { it.height }?.times(0.3f) ?: 50f
        val rows = mutableListOf<MutableList<Panel>>()
        
        val sortedByTop = panels.sortedBy { it.bounds.top }
        
        for (panel in sortedByTop) {
            val existingRow = rows.find { row ->
                row.any { abs(it.bounds.centerY() - panel.bounds.centerY()) < rowThreshold }
            }
            
            if (existingRow != null) {
                existingRow.add(panel)
            } else {
                rows.add(mutableListOf(panel))
            }
        }
        
        val ordered = mutableListOf<Panel>()
        var order = 0
        
        for (row in rows) {
            val sortedRow = when (direction) {
                ReadingDirection.LEFT_TO_RIGHT -> row.sortedBy { it.bounds.left }
                ReadingDirection.RIGHT_TO_LEFT -> row.sortedByDescending { it.bounds.left }
            }
            
            for (panel in sortedRow) {
                ordered.add(panel.copy(readingOrder = order++))
            }
        }
        
        return ordered
    }
}
