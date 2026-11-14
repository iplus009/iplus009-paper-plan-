package com.paperplan.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.paperplan.app.core.storage.SafSafeWriter
import com.paperplan.app.core.storage.TempWorkspaceManager
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
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
import kotlin.math.max
import kotlin.math.roundToInt

data class PdfSaveSummary(
    val outputBytes: Long,
    val pageCount: Int
)

data class PdfPageThumbnail(
    val pageIndex: Int,
    val bitmap: Bitmap
)

data class PdfPageRender(
    val bitmap: Bitmap,
    val pageIndex: Int,
    val totalPages: Int
)

data class PdfCompressionReport(
    val doc: WorkspaceDoc,
    val summary: CompressSummary
)

/**
 * Single thread-safe PDFBox + PdfRenderer gateway using disk-backed MemoryUsageSetting
 * so multi-hundred page documents do not exhaust the Android JVM heap.
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
        val doc = mergeToWorkspace(context, inputs, "PaperPlane-Merged.pdf")
        val bytes = WorkspaceLibrary.exportToSaf(context, doc, outputUri)
        PdfSaveSummary(outputBytes = bytes, pageCount = doc.pageCount)
    }

    suspend fun mergeToWorkspace(
        context: Context,
        inputs: List<Uri>,
        displayName: String = "PaperPlane-Merged.pdf"
    ): WorkspaceDoc = withContext(Dispatchers.IO) {
        require(inputs.size >= 2) { "Select at least two PDF files." }
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

                WorkspaceLibrary.saveStagedPdf(
                    context = context,
                    stagedPdf = stagedOut,
                    displayName = displayName,
                    tag = "Merged",
                    pageCount = pageCount
                )
            }
        }
    }

    suspend fun rotateAllPages(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        clockwiseDegrees: Int = 90
    ): PdfSaveSummary = withContext(Dispatchers.IO) {
        require(inputUri != outputUri) { "Output file must be separate from source PDF." }
        val doc = rotateAllPagesToWorkspace(context, inputUri, clockwiseDegrees, "PaperPlane-Rotated.pdf")
        val bytes = WorkspaceLibrary.exportToSaf(context, doc, outputUri)
        PdfSaveSummary(outputBytes = bytes, pageCount = doc.pageCount)
    }

    suspend fun rotateAllPagesToWorkspace(
        context: Context,
        inputUri: Uri,
        clockwiseDegrees: Int = 90,
        displayName: String = "PaperPlane-Rotated.pdf"
    ): WorkspaceDoc = withContext(Dispatchers.IO) {
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

                WorkspaceLibrary.saveStagedPdf(
                    context = context,
                    stagedPdf = stagedOut,
                    displayName = displayName,
                    tag = "Rotated",
                    pageCount = pageCount
                )
            }
        }
    }

    suspend fun organizePagesToWorkspace(
        context: Context,
        inputUri: Uri,
        operations: List<PagePlanOp>,
        displayName: String = "PaperPlane-Organized.pdf"
    ): WorkspaceDoc = withContext(Dispatchers.IO) {
        require(operations.isNotEmpty()) { "Keep at least one page in the organized PDF." }
        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "organize") { workDir ->
                val srcCopy = File(workDir, "input.pdf")
                val stagedOut = File(workDir, "organized.pdf")
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                SafSafeWriter.copyUriToTempFile(context, inputUri, srcCopy)

                PDDocument.load(srcCopy, mem).use { source ->
                    require(source.numberOfPages > 0) { "Selected PDF has no pages." }
                    PDDocument(mem).use { dest ->
                        for (op in operations) {
                            require(op.sourcePageIndex in 0 until source.numberOfPages) {
                                "Invalid page index ${op.sourcePageIndex}"
                            }
                            val imported = dest.importPage(source.getPage(op.sourcePageIndex))
                            if (op.extraRotationCw != 0) {
                                imported.rotation = ((imported.rotation + op.extraRotationCw) % 360 + 360) % 360
                            }
                        }
                        dest.save(stagedOut)
                    }
                }

                WorkspaceLibrary.saveStagedPdf(
                    context = context,
                    stagedPdf = stagedOut,
                    displayName = displayName,
                    tag = "Organized",
                    pageCount = operations.size
                )
            }
        }
    }

    suspend fun compressPdfToWorkspace(
        context: Context,
        inputUri: Uri,
        preset: CompressionPreset,
        stripMetadata: Boolean = true,
        displayName: String = "PaperPlane-Compressed.pdf"
    ): PdfCompressionReport = withContext(Dispatchers.IO) {
        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "compress") { workDir ->
                val srcCopy = File(workDir, "input.pdf")
                val rasterOut = File(workDir, "compressed_raster.pdf")
                val compactOut = File(workDir, "compressed_meta.pdf")
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                SafSafeWriter.copyUriToTempFile(context, inputUri, srcCopy)
                val originalBytes = srcCopy.length()

                var pageCount = 0
                ParcelFileDescriptor.open(srcCopy, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        require(pageCount > 0) { "Selected PDF has no pages." }

                        PDDocument(mem).use { destDoc ->
                            for (idx in 0 until pageCount) {
                                val bmp = renderBoundedBitmap(renderer, idx, preset.maxImageDimension)
                                try {
                                    val aspect = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
                                    val outRect = PDRectangle(PDRectangle.A4.width, PDRectangle.A4.width * aspect)
                                    val newPage = PDPage(outRect)
                                    destDoc.addPage(newPage)
                                    val img = JPEGFactory.createFromImage(destDoc, bmp, preset.jpegQuality)
                                    PDPageContentStream(destDoc, newPage).use { cs ->
                                        cs.drawImage(img, 0f, 0f, outRect.width, outRect.height)
                                    }
                                } finally {
                                    bmp.recycle()
                                }
                            }
                            if (stripMetadata) clearDocMetadata(destDoc)
                            destDoc.save(rasterOut)
                        }
                    }
                }

                // Choose smallest verified output without ever inflating small vector PDFs
                val chosenFile: File
                val usedFallback: Boolean
                if (rasterOut.length() in 32L until originalBytes) {
                    chosenFile = rasterOut
                    usedFallback = false
                } else {
                    PDDocument.load(srcCopy, mem).use { srcDoc ->
                        if (stripMetadata) clearDocMetadata(srcDoc)
                        srcDoc.save(compactOut)
                    }
                    if (compactOut.length() in 32L until originalBytes) {
                        chosenFile = compactOut
                        usedFallback = false
                    } else {
                        chosenFile = srcCopy
                        usedFallback = true
                    }
                }

                val doc = WorkspaceLibrary.saveStagedPdf(
                    context = context,
                    stagedPdf = chosenFile,
                    displayName = displayName,
                    tag = "Compressed",
                    pageCount = pageCount
                )
                val summary = CompressSummary.fromSizes(
                    originalBytes = originalBytes,
                    compressedBytes = doc.sizeBytes,
                    pageCount = pageCount,
                    usedOriginalFallback = usedFallback
                )
                PdfCompressionReport(doc = doc, summary = summary)
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
        val doc = saveBitmapsToWorkspace(
            context = context,
            pages = listOf(bitmap to useLossless),
            displayName = "PaperPlane-Scan.pdf"
        )
        val bytes = WorkspaceLibrary.exportToSaf(context, doc, outputUri)
        PdfSaveSummary(outputBytes = bytes, pageCount = doc.pageCount)
    }

    suspend fun saveBitmapsToWorkspace(
        context: Context,
        pages: List<Pair<Bitmap, Boolean>>,
        displayName: String = "PaperPlane-GPU-Scan.pdf"
    ): WorkspaceDoc = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "Add at least one scanned page before exporting." }
        pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "batch_scan") { workDir ->
                val stagedOut = File(workDir, "scan_batch.pdf")
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)

                PDDocument(mem).use { doc ->
                    val a4Width = PDRectangle.A4.width
                    for ((bitmap, useLossless) in pages) {
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
                    }
                    doc.save(stagedOut)
                }

                WorkspaceLibrary.saveStagedPdf(
                    context = context,
                    stagedPdf = stagedOut,
                    displayName = displayName,
                    tag = "GPU Scan",
                    pageCount = pages.size
                )
            }
        }
    }

    suspend fun renderPageBitmap(
        context: Context,
        inputUri: Uri,
        pageIndex: Int,
        maxDimension: Int = 1500
    ): PdfPageRender = withContext(Dispatchers.IO) {
        TempWorkspaceManager.useTempDir(context.cacheDir, "render_page") { workDir ->
            val src = File(workDir, "view.pdf")
            SafSafeWriter.copyUriToTempFile(context, inputUri, src)
            ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount
                    require(count > 0) { "Selected PDF has no pages." }
                    val safeIdx = pageIndex.coerceIn(0, count - 1)
                    val bmp = renderBoundedBitmap(renderer, safeIdx, maxDimension)
                    PdfPageRender(bitmap = bmp, pageIndex = safeIdx, totalPages = count)
                }
            }
        }
    }

    suspend fun loadPageThumbnails(
        context: Context,
        inputUri: Uri,
        maxDimension: Int = 260,
        maxPages: Int = 48
    ): Pair<Int, List<PdfPageThumbnail>> = withContext(Dispatchers.IO) {
        TempWorkspaceManager.useTempDir(context.cacheDir, "thumbs") { workDir ->
            val src = File(workDir, "thumbs.pdf")
            SafSafeWriter.copyUriToTempFile(context, inputUri, src)
            ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    require(total > 0) { "Selected PDF has no pages." }
                    val limit = minOf(total, maxPages)
                    val list = ArrayList<PdfPageThumbnail>(limit)
                    for (idx in 0 until limit) {
                        val bmp = renderBoundedBitmap(renderer, idx, maxDimension)
                        list += PdfPageThumbnail(pageIndex = idx, bitmap = bmp)
                    }
                    Pair(total, list)
                }
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

    /**
     * Generates a clean 3-page bilingual verification PDF inside Workspace so users on empty devices
     * can immediately try Viewer, Organize, Compress, Rotate, Split, and True Redact.
     */
    suspend fun createSampleVerificationPdf(context: Context): WorkspaceDoc = withContext(Dispatchers.IO) {
        TempWorkspaceManager.useTempDir(context.cacheDir, "sample_gen") { workDir ->
            val outFile = File(workDir, "PaperPlane-Sample-3Pages.pdf")
            val pdfDoc = PdfDocument()
            try {
                val pages = listOf(
                    "Paper Plane • Page 1 (Confidential Test)" to
                        "Document Reference: PP-2026-001\n\nCONFIDENTIAL TOKEN: SECRET-KEY-98421-TEST\nDraw a redaction box over this middle section in the Redact tab, export, and confirm PDFTextStripper verifies 0 characters remain.",
                    "Paper Plane • Page 2 (বাংলা যুক্তাক্ষর)" to
                        "বাংলা যুক্তবর্ণ যাচাই: ক্ষ, জ্ঞ, ত্ত, ষ্ণ, ন্ত্র, হ্ম, স্ম, ক্ত এবং ব্ধ।\n\nঅফিসিয়াল নথি, বই ও মিশ্র টেক্সট (Mixed English 2026) Android HarfBuzz দিয়ে ভেক্টর টেক্সট আকারে সংরক্ষিত হয়েছে।",
                    "Paper Plane • Page 3 (Organizer & Rotate)" to
                        "Use Organize Pages to reorder Page 3 to the top, rotate clockwise, or delete unnecessary pages.\n\nPaper Plane by iPlus — On-device GPU & TempFile PDF Suite."
                )

                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 18f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
                val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.DKGRAY
                    textSize = 13f
                    typeface = Typeface.SANS_SERIF
                }
                val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(37, 99, 235)
                    strokeWidth = 2f
                }

                pages.forEachIndexed { idx, (heading, body) ->
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, idx + 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    drawTextBlock(canvas, heading, titlePaint, 52f, 64f, 491)
                    canvas.drawLine(52f, 102f, 543f, 102f, accentPaint)
                    drawTextBlock(canvas, body, bodyPaint, 52f, 128f, 491)
                    canvas.drawText("Page ${idx + 1} / 3 • Paper Plane Sample", 52f, 804f, bodyPaint)
                    pdfDoc.finishPage(page)
                }

                outFile.outputStream().use { out ->
                    pdfDoc.writeTo(out)
                    out.flush()
                }
            } finally {
                pdfDoc.close()
            }

            WorkspaceLibrary.saveStagedPdf(
                context = context,
                stagedPdf = outFile,
                displayName = "PaperPlane-Sample-3Pages.pdf",
                tag = "Sample",
                pageCount = 3
            )
        }
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        left: Float,
        top: Float,
        width: Int
    ) {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setLineSpacing(0f, 1.4f)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun clearDocMetadata(doc: PDDocument) {
        val info = doc.documentInformation
        info.author = null
        info.title = null
        info.subject = null
        info.keywords = null
        info.creator = "Paper Plane"
        info.producer = "Paper Plane Safe Compressor"
    }

    private fun renderBoundedBitmap(
        renderer: PdfRenderer,
        pageIndex: Int,
        maxDimension: Int
    ): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val srcMax = max(page.width, page.height).coerceAtLeast(1)
            val scale = (maxDimension.toFloat() / srcMax.toFloat()).coerceIn(0.25f, 3.0f)
            val w = (page.width * scale).roundToInt().coerceAtLeast(1)
            val h = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }
}
