package com.comicreader.app.core.extractors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.comicreader.app.data.models.ComicFormat
import com.comicreader.app.data.models.ComicPage
import com.comicreader.app.data.models.ExtractionResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer as PdfBoxRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader

/**
 * Interfaz base para extractores de archivos
 */
interface ComicExtractor {
    suspend fun extract(file: File, cacheDir: File): Flow<ExtractionResult>
    fun supports(format: ComicFormat): Boolean
}

/**
 * Factory para obtener el extractor correcto según el formato
 */
class ExtractorFactory(private val context: Context) {
    
    private val extractors: List<ComicExtractor> by lazy {
        listOf(
            ZipExtractor(),
            RarExtractor(),
            PdfExtractor(context),
            EpubExtractor(context),
            ImageExtractor()
        )
    }
    
    fun getExtractor(format: ComicFormat): ComicExtractor? {
        return extractors.find { it.supports(format) }
    }
    
    fun getExtractor(file: File): ComicExtractor? {
        val extension = file.extension.lowercase()
        val format = ComicFormat.fromExtension(extension) ?: return null
        return getExtractor(format)
    }
}

/**
 * Extractor para archivos ZIP, CBZ
 */
class ZipExtractor : ComicExtractor {
    
    override fun supports(format: ComicFormat): Boolean {
        return format in listOf(ComicFormat.ZIP, ComicFormat.CBZ)
    }
    
    override suspend fun extract(file: File, cacheDir: File): Flow<ExtractionResult> = flow {
        withContext(Dispatchers.IO) {
            try {
                val zipFile = ZipFile(file)
                val extractDir = File(cacheDir, file.nameWithoutExtension)
                extractDir.mkdirs()
                
                // Obtener lista de archivos de imagen
                val imageFiles = zipFile.fileHeaders
                    .filter { !it.isDirectory }
                    .filter { isImageFile(it.fileName) }
                    .sortedBy { it.fileName }
                
                val total = imageFiles.size
                val pages = mutableListOf<ComicPage>()
                
                imageFiles.forEachIndexed { index, header ->
                    emit(ExtractionResult.Progress(index + 1, total))
                    
                    val outputFile = File(extractDir, header.fileName.substringAfterLast("/"))
                    zipFile.extractFile(header, extractDir.absolutePath, outputFile.name)
                    
                    val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    if (bitmap != null) {
                        pages.add(
                            ComicPage(
                                pageNumber = index,
                                imagePath = outputFile.absolutePath,
                                bitmap = null, // No guardar bitmap en memoria
                                width = bitmap.width,
                                height = bitmap.height
                            )
                        )
                        bitmap.recycle()
                    }
                }
                
                emit(ExtractionResult.Success(pages))
                
            } catch (e: Exception) {
                emit(ExtractionResult.Error("Error extrayendo ZIP: ${e.message}", e))
            }
        }
    }
}

/**
 * Extractor para archivos RAR, CBR
 */
class RarExtractor : ComicExtractor {
    
    override fun supports(format: ComicFormat): Boolean {
        return format in listOf(ComicFormat.RAR, ComicFormat.CBR)
    }
    
    override suspend fun extract(file: File, cacheDir: File): Flow<ExtractionResult> = flow {
        withContext(Dispatchers.IO) {
            try {
                val archive = Archive(file)
                val extractDir = File(cacheDir, file.nameWithoutExtension)
                extractDir.mkdirs()
                
                // Filtrar y ordenar archivos de imagen
                val imageHeaders = archive.fileHeaders
                    .filter { !it.isDirectory }
                    .filter { isImageFile(it.fileName) }
                    .sortedBy { it.fileName }
                
                val total = imageHeaders.size
                val pages = mutableListOf<ComicPage>()
                
                imageHeaders.forEachIndexed { index, header ->
                    emit(ExtractionResult.Progress(index + 1, total))
                    
                    val outputFile = File(extractDir, header.fileName.substringAfterLast("/"))
                    outputFile.parentFile?.mkdirs()
                    
                    FileOutputStream(outputFile).use { fos ->
                        archive.extractFile(header, fos)
                    }
                    
                    val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    if (bitmap != null) {
                        pages.add(
                            ComicPage(
                                pageNumber = index,
                                imagePath = outputFile.absolutePath,
                                width = bitmap.width,
                                height = bitmap.height
                            )
                        )
                        bitmap.recycle()
                    }
                }
                
                archive.close()
                emit(ExtractionResult.Success(pages))
                
            } catch (e: Exception) {
                emit(ExtractionResult.Error("Error extrayendo RAR: ${e.message}", e))
            }
        }
    }
}

/**
 * Extractor para archivos PDF
 */
class PdfExtractor(private val context: Context) : ComicExtractor {
    
    init {
        // Inicializar PDFBox
        PDFBoxResourceLoader.init(context)
    }
    
    override fun supports(format: ComicFormat): Boolean {
        return format == ComicFormat.PDF
    }
    
    override suspend fun extract(file: File, cacheDir: File): Flow<ExtractionResult> = flow {
        withContext(Dispatchers.IO) {
            try {
                val extractDir = File(cacheDir, file.nameWithoutExtension)
                extractDir.mkdirs()
                
                // Usar PdfRenderer para Android (más eficiente)
                val parcelFileDescriptor = ParcelFileDescriptor.open(
                    file, 
                    ParcelFileDescriptor.MODE_READ_ONLY
                )
                val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                
                val total = pdfRenderer.pageCount
                val pages = mutableListOf<ComicPage>()
                
                for (index in 0 until total) {
                    emit(ExtractionResult.Progress(index + 1, total))
                    
                    val page = pdfRenderer.openPage(index)
                    
                    // Renderizar a alta resolución para cómics
                    val scale = 2.0f
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    
                    // Guardar como imagen
                    val outputFile = File(extractDir, "page_${index.toString().padStart(4, '0')}.png")
                    FileOutputStream(outputFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    
                    pages.add(
                        ComicPage(
                            pageNumber = index,
                            imagePath = outputFile.absolutePath,
                            width = width,
                            height = height
                        )
                    )
                    
                    page.close()
                    bitmap.recycle()
                }
                
                pdfRenderer.close()
                parcelFileDescriptor.close()
                
                emit(ExtractionResult.Success(pages))
                
            } catch (e: Exception) {
                emit(ExtractionResult.Error("Error extrayendo PDF: ${e.message}", e))
            }
        }
    }
}

/**
 * Extractor para archivos EPUB
 */
class EpubExtractor(private val context: Context) : ComicExtractor {
    
    override fun supports(format: ComicFormat): Boolean {
        return format == ComicFormat.EPUB
    }
    
    override suspend fun extract(file: File, cacheDir: File): Flow<ExtractionResult> = flow {
        withContext(Dispatchers.IO) {
            try {
                val extractDir = File(cacheDir, file.nameWithoutExtension)
                extractDir.mkdirs()
                
                val epubReader = EpubReader()
                val book = FileInputStream(file).use { fis ->
                    epubReader.readEpub(fis)
                }
                
                val pages = mutableListOf<ComicPage>()
                var pageIndex = 0
                
                // Extraer imágenes del EPUB
                val resources = book.resources.all
                val imageResources = resources.filter { resource ->
                    resource.mediaType?.name?.startsWith("image/") == true
                }.sortedBy { it.href }
                
                val total = imageResources.size
                
                imageResources.forEach { resource ->
                    emit(ExtractionResult.Progress(pageIndex + 1, total))
                    
                    val extension = resource.href.substringAfterLast(".", "png")
                    val outputFile = File(extractDir, "page_${pageIndex.toString().padStart(4, '0')}.$extension")
                    
                    FileOutputStream(outputFile).use { fos ->
                        fos.write(resource.data)
                    }
                    
                    val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    if (bitmap != null) {
                        pages.add(
                            ComicPage(
                                pageNumber = pageIndex,
                                imagePath = outputFile.absolutePath,
                                width = bitmap.width,
                                height = bitmap.height
                            )
                        )
                        bitmap.recycle()
                        pageIndex++
                    }
                }
                
                emit(ExtractionResult.Success(pages))
                
            } catch (e: Exception) {
                emit(ExtractionResult.Error("Error extrayendo EPUB: ${e.message}", e))
            }
        }
    }
}

/**
 * Extractor para archivos de imagen individuales o carpetas
 */
class ImageExtractor : ComicExtractor {
    
    override fun supports(format: ComicFormat): Boolean {
        return format == ComicFormat.IMAGE
    }
    
    override suspend fun extract(file: File, cacheDir: File): Flow<ExtractionResult> = flow {
        withContext(Dispatchers.IO) {
            try {
                val pages = mutableListOf<ComicPage>()
                
                if (file.isDirectory) {
                    // Carpeta con imágenes
                    val imageFiles = file.listFiles()
                        ?.filter { isImageFile(it.name) }
                        ?.sortedBy { it.name }
                        ?: emptyList()
                    
                    val total = imageFiles.size
                    
                    imageFiles.forEachIndexed { index, imageFile ->
                        emit(ExtractionResult.Progress(index + 1, total))
                        
                        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        if (bitmap != null) {
                            pages.add(
                                ComicPage(
                                    pageNumber = index,
                                    imagePath = imageFile.absolutePath,
                                    width = bitmap.width,
                                    height = bitmap.height
                                )
                            )
                            bitmap.recycle()
                        }
                    }
                } else {
                    // Imagen individual
                    emit(ExtractionResult.Progress(1, 1))
                    
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        pages.add(
                            ComicPage(
                                pageNumber = 0,
                                imagePath = file.absolutePath,
                                width = bitmap.width,
                                height = bitmap.height
                            )
                        )
                        bitmap.recycle()
                    }
                }
                
                emit(ExtractionResult.Success(pages))
                
            } catch (e: Exception) {
                emit(ExtractionResult.Error("Error cargando imágenes: ${e.message}", e))
            }
        }
    }
}

/**
 * Utilidades compartidas
 */
private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

fun isImageFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast(".").lowercase()
    return extension in imageExtensions
}

/**
 * Caché de páginas extraídas
 */
class PageCache(
    private val context: Context,
    private val maxCacheSize: Long = 100 * 1024 * 1024 // 100MB
) {
    private val cacheDir = File(context.cacheDir, "comic_pages")
    
    init {
        cacheDir.mkdirs()
    }
    
    fun getCacheDir(): File = cacheDir
    
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }
    
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        calculateDirSize(cacheDir)
    }
    
    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }
    
    suspend fun trimCache() = withContext(Dispatchers.IO) {
        val currentSize = getCacheSize()
        if (currentSize > maxCacheSize) {
            // Eliminar archivos más antiguos
            val files = cacheDir.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.lastModified() }
                .toList()
            
            var freedSpace = 0L
            val targetFree = currentSize - (maxCacheSize * 0.8).toLong()
            
            for (file in files) {
                if (freedSpace >= targetFree) break
                freedSpace += file.length()
                file.delete()
            }
        }
    }
}
