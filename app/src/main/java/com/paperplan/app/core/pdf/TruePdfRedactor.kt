package com.paperplan.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.paperplan.app.core.storage.SafSafeWriter
import com.paperplan.app.core.storage.TempWorkspaceManager
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Top-left normalized coordinates in visually displayed page space (0f..1f).
 */
data class NormalizedRedactionRect(
    val pageIndex: Int,
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float
) {
    fun clamped(): NormalizedRedactionRect {
        val l = leftFraction.coerceIn(0f, 1f)
        val r = rightFraction.coerceIn(0f, 1f)
        val t = topFraction.coerceIn(0f, 1f)
        val b = bottomFraction.coerceIn(0f, 1f)
        return copy(
            leftFraction = minOf(l, r),
            rightFraction = maxOf(l, r),
            topFraction = minOf(t, b),
            bottomFraction = maxOf(t, b)
        )
    }

    fun isValid(): Boolean {
        val c = clamped()
        return c.pageIndex >= 0 &&
            (c.rightFraction - c.leftFraction) >= 0.01f &&
            (c.bottomFraction - c.topFraction) >= 0.01f
    }
}

data class RedactionAuditSummary(
    val totalPages: Int,
    val redactedPageCount: Int,
    val redactedPagesRemainingTextChars: Int,
    val unredactedPagesRemainingTextChars: Int,
    val outputBytes: Long
)

data class PdfPreviewPage(
    val bitmap: Bitmap,
    val pageIndex: Int,
    val totalPages: Int
)

/**
 * Real pixel-cut PDF redactor.
 * Unlike AppendMode overlay drawers, redacted pages are re-rendered to bitmap with opaque
 * masked pixels and replaced as clean image-only pages with annotations & identifying metadata stripped.
 * Unredacted pages keep full vector/text fidelity.
 */
object TruePdfRedactor {

    suspend fun loadPreviewPage(
        context: Context,
        inputUri: Uri,
        pageIndex: Int,
        maxDimension: Int = 1280
    ): PdfPreviewPage = withContext(Dispatchers.IO) {
        TempWorkspaceManager.useTempDir(context.cacheDir, "preview") { workDir ->
            val srcCopy = File(workDir, "preview.pdf")
            SafSafeWriter.copyUriToTempFile(context, inputUri, srcCopy)
            renderSinglePageFromFile(srcCopy, pageIndex, maxDimension)
        }
    }

    internal fun renderSinglePageFromFile(
        pdfFile: File,
        pageIndex: Int,
        maxDimension: Int
    ): PdfPreviewPage {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                require(count > 0) { "Selected PDF has no pages." }
                val safeIdx = pageIndex.coerceIn(0, count - 1)
                val bmp = renderPageForRedaction(renderer, safeIdx, maxDimension)
                return PdfPreviewPage(bitmap = bmp, pageIndex = safeIdx, totalPages = count)
            }
        }
    }

    suspend fun redactAndVerify(
        context: Context,
        inputUri: Uri,
        outputUri: Uri,
        regions: List<NormalizedRedactionRect>,
        stripMetadata: Boolean = true
    ): RedactionAuditSummary = withContext(Dispatchers.IO) {
        val validRegions = regions.map { it.clamped() }.filter { it.isValid() }
        require(validRegions.isNotEmpty()) { "Select at least one valid redaction area." }
        require(inputUri != outputUri) { "Pick a new output file so the original remains intact." }

        val byPage = validRegions.groupBy { it.pageIndex }

        PdfEngine.pdfMutex.withLock {
            TempWorkspaceManager.useTempDir(context.cacheDir, "redact") { workDir ->
                val srcFile = File(workDir, "source.pdf")
                val stagedOut = File(workDir, "redacted.pdf")
                SafSafeWriter.copyUriToTempFile(context, inputUri, srcFile)

                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                var totalPages = 0
                val redactedIndices = mutableSetOf<Int>()

                ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        PDDocument.load(srcFile, mem).use { srcDoc ->
                            PDDocument(mem).use { destDoc ->
                                totalPages = srcDoc.numberOfPages
                                require(totalPages > 0) { "Source PDF has no pages." }

                                val maskPaint = Paint().apply {
                                    color = Color.BLACK
                                    style = Paint.Style.FILL
                                }

                                for (pageIdx in 0 until totalPages) {
                                    val boxes = byPage[pageIdx]
                                    if (boxes.isNullOrEmpty()) {
                                        destDoc.importPage(srcDoc.getPage(pageIdx))
                                    } else {
                                        redactedIndices.add(pageIdx)
                                        val rendered = renderPageForRedaction(renderer, pageIdx, maxDim = 2200)
                                        try {
                                            val canvas = Canvas(rendered)
                                            for (box in boxes) {
                                                canvas.drawRect(
                                                    box.leftFraction * rendered.width,
                                                    box.topFraction * rendered.height,
                                                    box.rightFraction * rendered.width,
                                                    box.bottomFraction * rendered.height,
                                                    maskPaint
                                                )
                                            }
                                            val aspect = rendered.height.toFloat() / rendered.width.toFloat().coerceAtLeast(1f)
                                            val refBox = srcDoc.getPage(pageIdx).cropBox
                                            val visualW = if ((srcDoc.getPage(pageIdx).rotation % 180) != 0) refBox.height else refBox.width
                                            val outBox = PDRectangle(visualW, visualW * aspect)
                                            val cleanPage = PDPage(outBox)
                                            destDoc.addPage(cleanPage)

                                            val img = JPEGFactory.createFromImage(destDoc, rendered, 0.92f)
                                            PDPageContentStream(destDoc, cleanPage).use { cs ->
                                                cs.drawImage(img, 0f, 0f, outBox.width, outBox.height)
                                            }
                                        } finally {
                                            rendered.recycle()
                                        }
                                    }
                                }

                                if (stripMetadata) {
                                    val info = destDoc.documentInformation
                                    info.author = null
                                    info.title = null
                                    info.subject = null
                                    info.keywords = null
                                    info.creator = "Paper Plan"
                                    info.producer = "Paper Plan Safe Redactor"
                                }
                                destDoc.save(stagedOut)
                            }
                        }
                    }
                }

                // Mandatory post-save text extraction verification against the staged artifact
                var redactedChars = 0
                var unredactedChars = 0
                PDDocument.load(stagedOut, mem).use { verifyDoc ->
                    val stripper = PDFTextStripper()
                    for (idx in 0 until verifyDoc.numberOfPages) {
                        stripper.startPage = idx + 1
                        stripper.endPage = idx + 1
                        val textLen = stripper.getText(verifyDoc).trim().length
                        if (idx in redactedIndices) redactedChars += textLen else unredactedChars += textLen
                    }
                }
                check(redactedChars == 0) {
                    "Redaction verification failed: $redactedChars extractable characters remained on redacted page."
                }

                val outBytes = SafSafeWriter.writeTempPdfToUri(context, stagedOut, outputUri, inputUri)
                RedactionAuditSummary(
                    totalPages = totalPages,
                    redactedPageCount = redactedIndices.size,
                    redactedPagesRemainingTextChars = redactedChars,
                    unredactedPagesRemainingTextChars = unredactedChars,
                    outputBytes = outBytes
                )
            }
        }
    }

    private fun renderPageForRedaction(
        renderer: PdfRenderer,
        pageIndex: Int,
        maxDim: Int
    ): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val srcMax = max(page.width, page.height).coerceAtLeast(1)
            val scale = (maxDim.toFloat() / srcMax.toFloat()).coerceIn(1f, 3.5f)
            val w = (page.width * scale).roundToInt().coerceAtLeast(1)
            val h = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            return bmp
        }
    }
}
