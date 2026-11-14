package com.paperplan.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.paperplan.app.core.storage.SafSafeWriter
import com.paperplan.app.core.storage.TempWorkspaceManager
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PdfSaveSummary(
    val outputBytes: Long,
    val pageCount: Int
)

/**
 * Single thread-safe PDFBox execution gateway using disk-backed MemoryUsageSetting
 * so multi-hundred page files never exhaust the Android JVM heap.
 */
object PdfEngine {

    val pdfMutex = Mutex()

    fun tempFileMemorySetting(cacheDir: File): MemoryUsageSetting {
        val workRoot = TempWorkspaceManager.ensureWorkspaceRoot(cacheDir)
        return MemoryUsageSetting.setupTempFileOnly().setTempDir(workRoot)
    }

    suspend fun merge(
        context: Context,
        inputs: List<Uri>,
        outputUri: Uri
    ): PdfSaveSummary = withContext(Dispatchers.IO) {
        require(inputs.size >= 2) { "Select at least two PDF files." }
        require(inputs.none { it == outputUri }) {
            "Output file must be separate from source PDFs."
        }

        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "merge") { workDir ->
                val stagedOut = File(workDir, "merged.pdf")
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                var pageCount = 0

                PDDocument(mem).use { destination ->
                    inputs.forEachIndexed { idx, uri ->
                        val srcCopy = File(workDir, "src_$idx.pdf")
                        SafSafeWriter.copyUriToTempFile(context, uri, srcCopy)
                        PDDocument.load(srcCopy, mem).use { source ->
                            source.pages.forEach { page -> destination.importPage(page) }
                        }
                        srcCopy.delete()
                    }
                    require(destination.numberOfPages > 0) { "The selected PDFs contain no pages." }
                    pageCount = destination.numberOfPages
                    destination.save(stagedOut)
                }

                val writtenBytes = SafSafeWriter.writeTempPdfToUri(context, stagedOut, outputUri)
                PdfSaveSummary(outputBytes = writtenBytes, pageCount = pageCount)
            }
        }
    }

    suspend fun rotateAllPages(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        clockwiseDegrees: Int = 90
    ): PdfSaveSummary = withContext(Dispatchers.IO) {
        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "rotate") { workDir ->
                val srcCopy = File(workDir, "input.pdf")
                val stagedOut = File(workDir, "rotated.pdf")
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                SafSafeWriter.copyUriToTempFile(context, inputUri, srcCopy)

                var pageCount = 0
                PDDocument.load(srcCopy, mem).use { document ->
                    require(document.numberOfPages > 0) { "Selected PDF has no pages." }
                    document.pages.forEach { page ->
                        page.rotation = ((page.rotation + clockwiseDegrees) % 360 + 360) % 360
                    }
                    pageCount = document.numberOfPages
                    document.save(stagedOut)
                }

                val writtenBytes = SafSafeWriter.writeTempPdfToUri(context, stagedOut, outputUri, inputUri)
                PdfSaveSummary(outputBytes = writtenBytes, pageCount = pageCount)
            }
        }
    }

    suspend fun splitIntoZip(
        context: Context,
        inputUri: Uri,
        outputUri: Uri
    ): PdfSaveSummary = withContext(Dispatchers.IO) {
        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "split") { workDir ->
                val srcCopy = File(workDir, "input.pdf")
                val stagedZip = File(workDir, "split.zip")
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                SafSafeWriter.copyUriToTempFile(context, inputUri, srcCopy)

                var pageCount = 0
                PDDocument.load(srcCopy, mem).use { source ->
                    require(source.numberOfPages > 0) { "The selected PDF contains no pages." }
                    pageCount = source.numberOfPages
                    ZipOutputStream(stagedZip.outputStream()).use { zip ->
                        for (index in 0 until source.numberOfPages) {
                            PDDocument(mem).use { onePage ->
                                onePage.importPage(source.getPage(index))
                                zip.putNextEntry(ZipEntry("page-${(index + 1).toString().padStart(3, '0')}.pdf"))
                                onePage.save(zip)
                                zip.closeEntry()
                            }
                        }
                        zip.finish()
                    }
                }

                val writtenBytes = SafSafeWriter.writeFileToUri(context, stagedZip, outputUri)
                PdfSaveSummary(outputBytes = writtenBytes, pageCount = pageCount)
            }
        }
    }

    suspend fun saveBitmapAsPdf(
        context: Context,
        bitmap: Bitmap,
        outputUri: Uri,
        useLossless: Boolean = false
    ): PdfSaveSummary = withContext(Dispatchers.IO) {
        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "scan_pdf") { workDir ->
                val stagedOut = File(workDir, "scanned.pdf")
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)

                PDDocument(mem).use { doc ->
                    val a4Width = PDRectangle.A4.width
                    val aspect = bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
                    val pageRect = PDRectangle(a4Width, a4Width * aspect)
                    val page = PDPage(pageRect)
                    doc.addPage(page)

                    val pdImage = if (useLossless) {
                        LosslessFactory.createFromImage(doc, bitmap)
                    } else {
                        JPEGFactory.createFromImage(doc, bitmap, 0.90f)
                    }

                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImage, 0f, 0f, pageRect.width, pageRect.height)
                    }
                    doc.save(stagedOut)
                }

                val writtenBytes = SafSafeWriter.writeTempPdfToUri(context, stagedOut, outputUri)
                PdfSaveSummary(outputBytes = writtenBytes, pageCount = 1)
            }
        }
    }

    suspend fun inspectPdfTextPerPage(
        context: Context,
        uri: Uri,
        pageIndices: List<Int>? = null
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "inspect") { workDir ->
                val copy = File(workDir, "inspect.pdf")
                SafSafeWriter.copyUriToTempFile(context, uri, copy)
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                val result = linkedMapOf<Int, String>()
                PDDocument.load(copy, mem).use { doc ->
                    val targets = pageIndices ?: (0 until doc.numberOfPages).toList()
                    val stripper = PDFTextStripper()
                    for (idx in targets) {
                        if (idx in 0 until doc.numberOfPages) {
                            stripper.startPage = idx + 1
                            stripper.endPage = idx + 1
                            result[idx] = stripper.getText(doc).trim()
                        }
                    }
                }
                result
            }
        }
    }
}
