package com.paperplan.app.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * On-device PDF operations. All source documents are opened read-only;
 * outputs are written only to a user-selected destination.
 */
class PdfToolkit(private val context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun merge(inputs: List<Uri>, output: OutputStream) {
        require(inputs.size >= 2) { "Select at least two PDF files." }
        PDDocument().use { destination ->
            inputs.forEach { uri ->
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { source ->
                        source.pages.forEach { page -> destination.importPage(page) }
                    }
                } ?: error("Could not open one of the selected PDFs.")
            }
            require(destination.numberOfPages > 0) { "The selected PDFs contain no pages." }
            destination.save(output)
        }
    }

    fun rotateAllPages(input: Uri, output: OutputStream) {
        context.contentResolver.openInputStream(input)?.use { stream ->
            PDDocument.load(stream).use { document ->
                document.pages.forEach { page ->
                    page.rotation = (page.rotation + 90) % 360
                }
                document.save(output)
            }
        } ?: error("Could not open the selected PDF.")
    }

    fun splitIntoZip(input: Uri, output: OutputStream) {
        context.contentResolver.openInputStream(input)?.use { stream ->
            PDDocument.load(stream).use { source ->
                require(source.numberOfPages > 0) { "The selected PDF contains no pages." }
                ZipOutputStream(output).use { zip ->
                    for (index in 0 until source.numberOfPages) {
                        PDDocument().use { onePage ->
                            onePage.importPage(source.getPage(index))
                            zip.putNextEntry(ZipEntry("page-${(index + 1).toString().padStart(3, '0')}.pdf"))
                            onePage.save(zip)
                            zip.closeEntry()
                        }
                    }
                    zip.finish()
                }
            }
        } ?: error("Could not open the selected PDF.")
    }
}
