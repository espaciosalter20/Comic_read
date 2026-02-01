package com.comicreader.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ComicReaderApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicializar PDFBox para lectura de PDFs
        PDFBoxResourceLoader.init(applicationContext)
    }
}
