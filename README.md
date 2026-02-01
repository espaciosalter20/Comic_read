# 📚 Comic Reader para Android

Una aplicación de lectura de cómics para Android con **detección automática de paneles** para lectura viñeta a viñeta.

## ✨ Características

### Formatos Soportados
- **CBR** - Comic Book RAR
- **CBZ** - Comic Book ZIP
- **PDF** - Portable Document Format
- **EPUB** - Electronic Publication
- **ZIP** - Archivos comprimidos
- **RAR** - Archivos RAR
- **JPG/PNG** - Imágenes individuales o carpetas

### Modos de Lectura
1. **Página completa** - Vista tradicional de página por página
2. **Panel a panel** - Lectura automática viñeta por viñeta con zoom
3. **Continuo** - Scroll vertical de todas las páginas
4. **Webtoon** - Optimizado para cómics verticales coreanos

### Detección Automática de Paneles
El algoritmo de detección de paneles utiliza:
- Conversión a escala de grises
- Detección de bordes (operador Sobel)
- Identificación de "gutters" (espacios entre paneles)
- Creación de rejilla de paneles
- Ordenamiento según dirección de lectura

### Direcciones de Lectura
- **Occidental** (izquierda → derecha, arriba → abajo)
- **Manga** (derecha → izquierda, arriba → abajo)

## 🏗️ Arquitectura

```
app/
├── src/main/java/com/comicreader/app/
│   ├── core/
│   │   ├── detection/
│   │   │   └── PanelDetector.kt      # Algoritmos de detección de paneles
│   │   └── extractors/
│   │       └── Extractors.kt          # Extractores para cada formato
│   ├── data/
│   │   └── models/
│   │       └── Models.kt              # Modelos de datos
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── LibraryScreen.kt       # Pantalla de biblioteca
│   │   │   └── ReaderScreen.kt        # Pantalla de lectura
│   │   ├── viewmodels/
│   │   │   └── ComicReaderViewModel.kt
│   │   └── theme/
│   │       └── Theme.kt
│   ├── MainActivity.kt
│   └── ComicReaderApplication.kt
└── src/main/res/
    ├── values/
    │   ├── strings.xml
    │   ├── colors.xml
    │   └── themes.xml
    └── xml/
        ├── file_paths.xml
        ├── backup_rules.xml
        └── data_extraction_rules.xml
```

## 🔧 Tecnologías Utilizadas

- **Kotlin** - Lenguaje principal
- **Jetpack Compose** - UI moderna declarativa
- **Coroutines & Flow** - Programación asíncrona
- **Coil** - Carga de imágenes
- **Room** - Base de datos local
- **Navigation Compose** - Navegación
- **Zip4j** - Extracción de ZIP/CBZ
- **JUnrar** - Extracción de RAR/CBR
- **PDFBox-Android** - Lectura de PDFs
- **EPUBLib** - Lectura de EPUBs

## 📱 Requisitos

- Android 7.0 (API 24) o superior
- Aproximadamente 50MB de espacio

## 🚀 Instalación

1. Clona el repositorio
2. Abre en Android Studio
3. Sincroniza las dependencias de Gradle
4. Ejecuta en un dispositivo o emulador

```bash
git clone <repo-url>
cd comic-reader
./gradlew assembleDebug
```

## 🎮 Uso

### Controles de Lectura

**Modo Página:**
- Deslizar izquierda/derecha para cambiar página
- Doble toque para zoom
- Pellizcar para zoom manual

**Modo Panel:**
- Toque en zona izquierda (30%) → Panel anterior
- Toque en zona derecha (30%) → Panel siguiente
- Toque en centro (40%) → Mostrar/ocultar controles
- Deslizar horizontal → Cambiar panel
- Doble toque → Volver a modo página

### Ajustes
- Modo de lectura
- Dirección de lectura (occidental/manga)
- Detección automática de paneles

## 🧮 Algoritmo de Detección de Paneles

```kotlin
// Proceso simplificado:
1. Convertir imagen a escala de grises
2. Aplicar detección de bordes (Sobel)
3. Buscar líneas horizontales (gutters entre filas)
4. Buscar líneas verticales (gutters entre columnas)
5. Crear paneles desde la intersección de líneas
6. Filtrar paneles por tamaño mínimo/máximo
7. Fusionar paneles superpuestos
8. Ordenar según dirección de lectura
```

### Parámetros Configurables

```kotlin
data class PanelDetectionConfig(
    val edgeThreshold: Int = 50,        // Umbral de bordes
    val minGutterRatio: Float = 0.6f,   // Ratio mínimo de gutter
    val minPanelSize: Int = 100,        // Tamaño mínimo (px)
    val minPanelAreaRatio: Float = 0.02f, // 2% del área
    val maxPanelAreaRatio: Float = 0.95f, // 95% del área
    val marginPixels: Int = 20,         // Margen a ignorar
    val gutterPadding: Float = 5f,      // Padding interno
    val mergeOverlapThreshold: Float = 0.3f // Umbral de fusión
)
```

## 📄 Licencia

MIT License

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Por favor:
1. Fork el repositorio
2. Crea una rama para tu feature
3. Haz commit de tus cambios
4. Abre un Pull Request

## 📝 Notas de Desarrollo

### Mejoras Futuras
- [ ] Integración con OpenCV para detección más precisa
- [ ] Machine Learning para paneles irregulares
- [ ] Soporte para texto/OCR
- [ ] Sincronización en la nube
- [ ] Biblioteca con metadatos de ComicVine
- [ ] Modo nocturno automático
- [ ] Gestos personalizables
