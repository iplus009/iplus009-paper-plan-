package com.paperplan.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.paperplan.app.core.storage.SafSafeWriter
import com.paperplan.app.core.storage.TempWorkspaceManager
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class BlockRole { HEADING, BODY }

data class DocumentBlock(
    val role: BlockRole,
    val text: String
)

data class CanonicalTextDocument(
    val title: String,
    val blocks: List<DocumentBlock>
) {
    companion object {
        fun fromPlainMarkdown(title: String, markdown: String): CanonicalTextDocument {
            val blocks = markdown
                .replace("\r\n", "\n")
                .split(Regex("\n+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    if (line.startsWith("#")) {
                        DocumentBlock(BlockRole.HEADING, line.trimStart('#').trim())
                    } else {
                        DocumentBlock(BlockRole.BODY, line)
                    }
                }
            return CanonicalTextDocument(title = title.ifBlank { "Paper Plane Document" }, blocks = blocks)
        }
    }
}

enum class BanglaExportStyle(val label: String) {
    BOOK_STYLE("Original Book Style"),
    CLEAN_TEXT_STYLE("Clean Text Style")
}

data class BanglaExportAuditResult(
    val previewBitmap: Bitmap,
    val pageCount: Int,
    val outputBytes: Long,
    val extractedText: String,
    val hasBengaliCharacters: Boolean
)

/**
 * Renders Bengali + mixed-script PDF documents through Android's native Minikin/HarfBuzz
 * text shaper (`StaticLayout` -> `android.graphics.pdf.PdfDocument`), keeping vowel signs
 * and conjunct ligatures shaped cleanly without PdfBox Indic breakdown.
 */
object BanglaPdfExporter {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    suspend fun exportAndAudit(
        context: Context,
        document: CanonicalTextDocument,
        style: BanglaExportStyle,
        outputUri: Uri
    ): BanglaExportAuditResult = withContext(Dispatchers.IO) {
        TempWorkspaceManager.useTempDir(context.cacheDir, "bangla_pdf") { workDir ->
            val stagedOut = File(workDir, "bangla_export.pdf")
            val pdfDoc = PdfDocument()

            val isBook = style == BanglaExportStyle.BOOK_STYLE
            val marginH = if (isBook) 56 else 44
            val marginTop = if (isBook) 64 else 48
            val marginBottom = if (isBook) 60 else 48
            val contentWidth = A4_WIDTH - marginH * 2
            val pageBottomLimit = A4_HEIGHT - marginBottom

            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = if (isBook) 12.5f else 13.5f
                typeface = if (isBook) Typeface.SERIF else Typeface.SANS_SERIF
            }
            val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = if (isBook) 18f else 17f
                typeface = Typeface.create(
                    if (isBook) Typeface.SERIF else Typeface.SANS_SERIF,
                    Typeface.BOLD
                )
            }
            val chromePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 9.5f
                typeface = Typeface.SANS_SERIF
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var cursorY = startPageChrome(page.canvas, document.title, isBook, marginH, marginTop, chromePaint)

            fun nextPage() {
                drawFooter(page.canvas, pageNumber, A4_WIDTH, A4_HEIGHT, chromePaint)
                pdfDoc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNumber).create()
                page = pdfDoc.startPage(pageInfo)
                cursorY = startPageChrome(page.canvas, document.title, isBook, marginH, marginTop, chromePaint)
            }

            try {
                for (block in document.blocks) {
                    val paint = if (block.role == BlockRole.HEADING) headingPaint else bodyPaint
                    val lineMult = if (isBook) 1.38f else 1.52f
                    val alignment = if (isBook && block.role == BlockRole.HEADING) {
                        Layout.Alignment.ALIGN_CENTER
                    } else {
                        Layout.Alignment.ALIGN_NORMAL
                    }

                    val layout = StaticLayout.Builder
                        .obtain(block.text, 0, block.text.length, paint, contentWidth)
                        .setAlignment(alignment)
                        .setLineSpacing(0f, lineMult)
                        .setIncludePad(false)
                        .build()

                    for (lineIdx in 0 until layout.lineCount) {
                        val lineTop = layout.getLineTop(lineIdx)
                        val lineBottom = layout.getLineBottom(lineIdx)
                        val lineH = (lineBottom - lineTop).toFloat()

                        if (cursorY + lineH > pageBottomLimit) {
                            nextPage()
                        }

                        val canvas = page.canvas
                        canvas.save()
                        canvas.translate(marginH.toFloat(), cursorY - lineTop.toFloat())
                        canvas.clipRect(0, lineTop, contentWidth, lineBottom)
                        layout.draw(canvas)
                        canvas.restore()
                        cursorY += lineH
                    }
                    cursorY += if (block.role == BlockRole.HEADING) 14f else 10f
                }

                drawFooter(page.canvas, pageNumber, A4_WIDTH, A4_HEIGHT, chromePaint)
                pdfDoc.finishPage(page)

                stagedOut.outputStream().use { out ->
                    pdfDoc.writeTo(out)
                    out.flush()
                }
            } finally {
                pdfDoc.close()
            }

            val outBytes = SafSafeWriter.writeTempPdfToUri(context, stagedOut, outputUri)

            // Preview and audit straight from staged file for deterministic checks
            val preview = TruePdfRedactor.renderSinglePageFromFile(stagedOut, pageIndex = 0, maxDimension = 1200)
            val extracted = PdfEngine.pdfMutex.withLock {
                val mem = MemoryUsageSetting.setupTempFileOnly().setTempDir(workDir)
                PDDocument.load(stagedOut, mem).use { checkDoc ->
                    PDFTextStripper().getText(checkDoc).trim()
                }
            }

            val hasBengali = extracted.any { it in '\u0980'..'\u09FF' }
            BanglaExportAuditResult(
                previewBitmap = preview.bitmap,
                pageCount = pageNumber,
                outputBytes = outBytes,
                extractedText = extracted,
                hasBengaliCharacters = hasBengali
            )
        }
    }

    private fun startPageChrome(
        canvas: Canvas,
        title: String,
        isBook: Boolean,
        marginH: Int,
        marginTop: Int,
        chromePaint: Paint
    ): Float {
        canvas.drawColor(Color.WHITE)
        if (isBook) {
            chromePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(title.take(64), marginH.toFloat(), (marginTop - 22).toFloat(), chromePaint)
            canvas.drawLine(
                marginH.toFloat(),
                (marginTop - 14).toFloat(),
                (A4_WIDTH - marginH).toFloat(),
                (marginTop - 14).toFloat(),
                chromePaint
            )
        }
        return marginTop.toFloat()
    }

    private fun drawFooter(
        canvas: Canvas,
        pageNumber: Int,
        pageWidth: Int,
        pageHeight: Int,
        chromePaint: Paint
    ) {
        chromePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("পৃষ্ঠা $pageNumber • Paper Plane by iPlus", pageWidth / 2f, (pageHeight - 24).toFloat(), chromePaint)
    }
}
