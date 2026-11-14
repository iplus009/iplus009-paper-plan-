package com.paperplan.app.feature.lab

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.gpu.BitmapDecoder
import com.paperplan.app.core.gpu.GlImageProcessor
import com.paperplan.app.core.gpu.GpuProcessResult
import com.paperplan.app.core.gpu.HomographySolver
import com.paperplan.app.core.gpu.Point2D
import com.paperplan.app.core.gpu.QuadCorners
import com.paperplan.app.core.gpu.ScanFilterMode
import com.paperplan.app.core.pdf.BanglaExportAuditResult
import com.paperplan.app.core.pdf.BanglaExportStyle
import com.paperplan.app.core.pdf.BanglaPdfExporter
import com.paperplan.app.core.pdf.CanonicalTextDocument
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.GpuZoomableBitmapPreview
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.formatFileBytes
import com.paperplan.app.core.ui.theme.PaperDangerSoft
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import com.paperplan.app.core.ui.theme.PaperViolet
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

private const val DEFAULT_BANGLA_TEST_TEXT = """# Paper Plane • যুক্তাক্ষর ও বিন্যাস পরীক্ষা

বাংলা ভাষার যুক্তবর্ণ যেমন ক্ষ, জ্ঞ, ত্ত, ষ্ণ, ন্ত্র, হ্ম, স্ম, ক্ত এবং ব্ধ সঠিকভাবে ভাঙন ছাড়াই রেন্ডার হচ্ছে কি না তা এই স্পাইক দিয়ে যাচাই করা হয়।

১. অফিসিয়াল দলিল ও বইয়ের মতো মার্জিন, হেডার এবং পৃষ্ঠা নম্বর অক্ষুণ্ণ রাখা যায়।
২. Mixed script: Paper Plane GPU Shader 2026 & ভেক্টর পিডিএফ আউটপুট পরীক্ষা।"""

@Composable
fun GpuBanglaLabScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- GPU Scanner State ---
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quadCorners by remember { mutableStateOf(QuadCorners.DEFAULT_TILT_PRESET) }
    var selectedMode by remember { mutableStateOf(ScanFilterMode.ADAPTIVE_BW) }
    var gpuResult by remember { mutableStateOf<GpuProcessResult?>(null) }
    var gpuStatus by remember {
        mutableStateOf("Pick a photo of a page/receipt to run OpenGL ES 2.0 homography & shadow removal.")
    }
    var gpuBusy by remember { mutableStateOf(false) }

    // --- Bangla Vector PDF State ---
    var docTitle by remember { mutableStateOf("Paper Plane • বাংলা ও যুক্তাক্ষর স্পাইক") }
    var docMarkdown by remember { mutableStateOf(DEFAULT_BANGLA_TEST_TEXT) }
    var exportStyle by remember { mutableStateOf(BanglaExportStyle.BOOK_STYLE) }
    var banglaResult by remember { mutableStateOf<BanglaExportAuditResult?>(null) }
    var banglaStatus by remember {
        mutableStateOf("Test Minikin/HarfBuzz Bangla conjunct shaping into real searchable PDF.")
    }
    var banglaBusy by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                gpuBusy = true
                gpuResult = null
                gpuStatus = "Decoding EXIF-normalized image…"
                runCatching {
                    BitmapDecoder.loadExifNormalizedBitmap(context, uri, maxDimension = 2048)
                }.fold(
                    onSuccess = { bmp ->
                        sourceBitmap = bmp
                        gpuStatus = "Loaded ${bmp.width}x${bmp.height}. Drag corner handles or choose a GPU shader."
                    },
                    onFailure = { err ->
                        gpuStatus = "Decode failed: ${err.message ?: "Unknown error"}"
                    }
                )
                gpuBusy = false
            }
        }
    }

    val saveGpuPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri ->
        val processed = gpuResult
        if (outputUri == null) {
            gpuStatus = "PDF save cancelled."
            return@rememberLauncherForActivityResult
        }
        if (processed == null) {
            gpuStatus = "Run GPU filter before saving PDF."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            gpuBusy = true
            gpuStatus = "Saving GPU-processed scan as PDF…"
            runCatching {
                PdfEngine.saveBitmapAsPdf(
                    context = context,
                    bitmap = processed.bitmap,
                    outputUri = outputUri,
                    useLossless = processed.mode == ScanFilterMode.ADAPTIVE_BW
                )
            }.fold(
                onSuccess = { summary ->
                    gpuStatus = "Saved PDF (${formatFileBytes(summary.outputBytes)}, ${summary.pageCount} page)."
                },
                onFailure = { err ->
                    gpuStatus = "PDF save failed: ${err.message ?: "Unknown error"}"
                }
            )
            gpuBusy = false
        }
    }

    val exportBanglaPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { outputUri ->
        if (outputUri == null) {
            banglaStatus = "Export cancelled."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            banglaBusy = true
            banglaStatus = "Shaping Bengali text via native HarfBuzz & verifying PDF text…"
            runCatching {
                val canonical = CanonicalTextDocument.fromPlainMarkdown(docTitle, docMarkdown)
                BanglaPdfExporter.exportAndAudit(
                    context = context,
                    document = canonical,
                    style = exportStyle,
                    outputUri = outputUri
                )
            }.fold(
                onSuccess = { audit ->
                    banglaResult = audit
                    banglaStatus = "Exported ${audit.pageCount} page(s) • ${formatFileBytes(audit.outputBytes)} • Unicode audit: " +
                        if (audit.hasBengaliCharacters) "VERIFIED" else "NO BENGALI INPUT"
                },
                onFailure = { err ->
                    banglaStatus = "Export failed: ${err.message ?: "Unknown error"}"
                }
            )
            banglaBusy = false
        }
    }

    val isQuadValid = HomographySolver.isConvexValidQuad(quadCorners)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. OpenGL ES 2.0 Scanner Card
        item(key = "gpu_scanner_card") {
            GlassWorkspaceCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DocumentScanner,
                            contentDescription = null,
                            tint = PaperMint,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text("GPU ES 2.0 Scanner Spike", style = MaterialTheme.typography.titleLarge)
                            Text("4-Corner Homography + Bright-Biased Shadow Clean", style = MaterialTheme.typography.bodySmall, color = PaperMuted)
                        }
                    }
                    StatusBadgePill(label = "GLES 2.0", accent = PaperMint)
                }

                Text(gpuStatus, style = MaterialTheme.typography.bodyMedium, color = PaperInfoText)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { pickImage.launch(arrayOf("image/*")) },
                        enabled = !gpuBusy,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (sourceBitmap == null) "Pick Photo" else "Change Photo")
                    }

                    OutlinedButton(
                        onClick = {
                            quadCorners = if (quadCorners == QuadCorners.FULL_FRAME) {
                                QuadCorners.DEFAULT_TILT_PRESET
                            } else {
                                QuadCorners.FULL_FRAME
                            }
                        },
                        enabled = !gpuBusy,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(if (quadCorners == QuadCorners.FULL_FRAME) "Desk Tilt Quad" else "Full Frame Quad")
                    }
                }

                val src = sourceBitmap
                if (src != null) {
                    QuadCornerEditor(
                        bitmap = src,
                        corners = quadCorners,
                        isConvexValid = isQuadValid,
                        onCornersChange = { quadCorners = it }
                    )

                    Text("Select GPU Fragment Shader Mode", style = MaterialTheme.typography.titleSmall)
                    val modes = ScanFilterMode.entries
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { mode ->
                                    FilterChip(
                                        selected = selectedMode == mode,
                                        onClick = { selectedMode = mode },
                                        label = { Text(mode.label, maxLines = 1) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                gpuBusy = true
                                gpuStatus = "Executing GLSL ${selectedMode.label} shader on GPU…"
                                runCatching {
                                    GlImageProcessor.processOnGpu(
                                        source = src,
                                        corners = quadCorners,
                                        mode = selectedMode,
                                        maxDimension = 2048
                                    )
                                }.fold(
                                    onSuccess = { out ->
                                        gpuResult = out
                                        gpuStatus = "GPU complete in ${out.elapsedMs} ms (${out.outputWidth}x${out.outputHeight}) via ${out.gpuRenderer}."
                                    },
                                    onFailure = { err ->
                                        gpuStatus = "GPU pass failed: ${err.message ?: "Unknown error"}"
                                    }
                                )
                                gpuBusy = false
                            }
                        },
                        enabled = !gpuBusy && isQuadValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (!isQuadValid) "Fix Self-Intersecting Quad" else "Run GPU Shader")
                    }
                }

                val processed = gpuResult
                if (processed != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadgePill(label = "${processed.elapsedMs} ms GPU", accent = PaperMint)
                        StatusBadgePill(label = "${processed.outputWidth}x${processed.outputHeight}", accent = PaperViolet)
                    }
                    Text(
                        "GL Renderer: ${processed.gpuRenderer} (${processed.gpuVendor})",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted
                    )
                    GpuZoomableBitmapPreview(
                        bitmap = processed.bitmap,
                        contentDescription = "GPU processed scan preview"
                    )
                    OutlinedButton(
                        onClick = { saveGpuPdf.launch("PaperPlane-GPU-Scan.pdf") },
                        enabled = !gpuBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Save GPU Scan to PDF")
                    }
                }
            }
        }

        // 2. Bangla Vector Shaping PDF Studio Card
        item(key = "bangla_pdf_card") {
            GlassWorkspaceCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = PaperViolet,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text("Bangla Vector PDF Spike", style = MaterialTheme.typography.titleLarge)
                            Text("Android HarfBuzz / Minikin StaticLayout Engine", style = MaterialTheme.typography.bodySmall, color = PaperMuted)
                        }
                    }
                    StatusBadgePill(label = "Vector Text", accent = PaperViolet)
                }

                Text(banglaStatus, style = MaterialTheme.typography.bodyMedium, color = PaperInfoText)

                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Book / Document Header") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = docMarkdown,
                    onValueChange = { docMarkdown = it },
                    label = { Text("Bangla & Mixed-Script Text (# Heading supported)") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BanglaExportStyle.entries.forEach { styleOption ->
                        FilterChip(
                            selected = exportStyle == styleOption,
                            onClick = { exportStyle = styleOption },
                            label = { Text(styleOption.label, maxLines = 1) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                Button(
                    onClick = { exportBanglaPdf.launch("PaperPlane-Bangla-Shaped.pdf") },
                    enabled = !banglaBusy && docMarkdown.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (banglaBusy) "Generating Vector PDF…" else "Export & Audit Bangla PDF")
                }

                val bAudit = banglaResult
                if (bAudit != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadgePill(label = "${bAudit.pageCount} Page(s)", accent = PaperViolet)
                        StatusBadgePill(label = formatFileBytes(bAudit.outputBytes), accent = PaperMint)
                    }
                    GpuZoomableBitmapPreview(
                        bitmap = bAudit.previewBitmap,
                        contentDescription = "Rendered Bangla PDF page 1"
                    )
                    Text("PDFTextStripper Searchable Text Audit:", style = MaterialTheme.typography.titleSmall)
                    Text(
                        bAudit.extractedText.ifBlank { "(No text extracted)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0B10))
                            .border(1.dp, PaperGlassBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadCornerEditor(
    bitmap: Bitmap,
    corners: QuadCorners,
    isConvexValid: Boolean,
    onCornersChange: (QuadCorners) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val latestCorners by rememberUpdatedState(corners)
    val latestOnCornersChange by rememberUpdatedState(onCornersChange)
    var activeHandle by remember { mutableIntStateOf(-1) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0B10))
            .border(1.dp, PaperGlassBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val scale = min(maxW / bitmap.width.coerceAtLeast(1), maxH / bitmap.height.coerceAtLeast(1))
        val fittedW = (bitmap.width * scale).coerceAtLeast(1f)
        val fittedH = (bitmap.height * scale).coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .size(
                    width = with(density) { fittedW.toDp() },
                    height = with(density) { fittedH.toDp() }
                )
                .pointerInput(bitmap, fittedW, fittedH) {
                    detectDragGestures(
                        onDragStart = { touch ->
                            val nx = (touch.x / fittedW).coerceIn(0f, 1f)
                            val ny = (touch.y / fittedH).coerceIn(0f, 1f)
                            val pts = latestCorners.asList()
                            var bestIdx = 0
                            var bestDist = Float.MAX_VALUE
                            pts.forEachIndexed { i, p ->
                                val d = hypot(p.x - nx, p.y - ny)
                                if (d < bestDist) {
                                    bestDist = d
                                    bestIdx = i
                                }
                            }
                            activeHandle = bestIdx
                        },
                        onDragEnd = { activeHandle = -1 },
                        onDragCancel = { activeHandle = -1 },
                        onDrag = { change, _ ->
                            val idx = activeHandle
                            if (idx !in 0..3) return@detectDragGestures
                            change.consume()
                            val nx = (change.position.x / fittedW).coerceIn(0f, 1f)
                            val ny = (change.position.y / fittedH).coerceIn(0f, 1f)
                            val pt = Point2D(nx, ny)
                            val base = latestCorners
                            val next = when (idx) {
                                0 -> base.copy(topLeft = pt)
                                1 -> base.copy(topRight = pt)
                                2 -> base.copy(bottomRight = pt)
                                else -> base.copy(bottomLeft = pt)
                            }
                            latestOnCornersChange(next)
                        }
                    )
                }
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Source document photo",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeColor = if (isConvexValid) PaperMint else PaperDangerSoft
                val tl = Offset(corners.topLeft.x * size.width, corners.topLeft.y * size.height)
                val tr = Offset(corners.topRight.x * size.width, corners.topRight.y * size.height)
                val br = Offset(corners.bottomRight.x * size.width, corners.bottomRight.y * size.height)
                val bl = Offset(corners.bottomLeft.x * size.width, corners.bottomLeft.y * size.height)

                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }
                drawPath(path = path, color = strokeColor.copy(alpha = 0.12f))
                drawPath(path = path, color = strokeColor, style = Stroke(width = 4f))

                listOf(tl, tr, br, bl).forEach { corner ->
                    drawCircle(color = strokeColor, radius = 16f, center = corner)
                    drawCircle(color = Color(0xFF101116), radius = 7f, center = corner)
                }
            }
        }
    }
}
