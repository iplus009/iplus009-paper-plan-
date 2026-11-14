package com.paperplan.app.feature.redact

import android.net.Uri
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.pdf.NormalizedRedactionRect
import com.paperplan.app.core.pdf.PdfPreviewPage
import com.paperplan.app.core.pdf.RedactionAuditSummary
import com.paperplan.app.core.pdf.TruePdfRedactor
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.WorkspaceDocActionBar
import com.paperplan.app.core.ui.components.WorkspaceDocSelectorStrip
import com.paperplan.app.core.ui.components.formatFileBytes
import com.paperplan.app.core.ui.theme.PaperDanger
import com.paperplan.app.core.ui.theme.PaperDangerSoft
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun TrueRedactScreen(
    initialDoc: WorkspaceDoc? = null,
    workspaceDocs: List<WorkspaceDoc> = emptyList(),
    onOpenViewer: (WorkspaceDoc) -> Unit = {},
    onWorkspaceChanged: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDocId by remember(initialDoc?.id) { mutableStateOf(initialDoc?.id) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var previewPage by remember { mutableStateOf<PdfPreviewPage?>(null) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var queuedRegions by remember { mutableStateOf<List<NormalizedRedactionRect>>(emptyList()) }

    // Active draft box normalized [0..1] on current page
    var draftLeft by remember { mutableFloatStateOf(0.14f) }
    var draftTop by remember { mutableFloatStateOf(0.24f) }
    var draftRight by remember { mutableFloatStateOf(0.84f) }
    var draftBottom by remember { mutableFloatStateOf(0.36f) }

    var stripMetadata by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Pick any PDF to preview pages, drag over sensitive zones, and burn pixels.")
    }
    var lastAudit by remember { mutableStateOf<RedactionAuditSummary?>(null) }
    var redactedDoc by remember { mutableStateOf<WorkspaceDoc?>(null) }

    fun loadPage(uri: Uri, targetPage: Int) {
        scope.launch {
            busy = true
            statusText = "Rendering page ${targetPage + 1}…"
            val result = runCatching {
                TruePdfRedactor.loadPreviewPage(context, uri, targetPage, maxDimension = 1280)
            }
            result.fold(
                onSuccess = { loaded ->
                    previewPage = loaded
                    currentPageIndex = loaded.pageIndex
                    statusText = "Page ${loaded.pageIndex + 1} of ${loaded.totalPages} ready. Drag on the page to mark a region."
                },
                onFailure = { err ->
                    statusText = "Preview failed: ${err.message ?: "Unknown error"}"
                }
            )
            busy = false
        }
    }

    LaunchedEffect(initialDoc?.id) {
        val doc = initialDoc ?: return@LaunchedEffect
        selectedDocId = doc.id
        val uri = Uri.fromFile(WorkspaceLibrary.getDocFile(context, doc))
        selectedUri = uri
        queuedRegions = emptyList()
        lastAudit = null
        redactedDoc = null
        currentPageIndex = 0
        loadPage(uri, 0)
    }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusText = "Importing PDF into Workspace…"
            runCatching {
                WorkspaceLibrary.importFromUri(
                    context = context,
                    sourceUri = uri,
                    displayName = "RedactSrc-${System.currentTimeMillis() % 10000}.pdf",
                    tag = "Imported"
                )
            }.fold(
                onSuccess = { imported ->
                    onWorkspaceChanged()
                    selectedDocId = imported.id
                    val docUri = Uri.fromFile(WorkspaceLibrary.getDocFile(context, imported))
                    selectedUri = docUri
                    queuedRegions = emptyList()
                    lastAudit = null
                    redactedDoc = null
                    currentPageIndex = 0
                    loadPage(docUri, 0)
                },
                onFailure = { err ->
                    busy = false
                    statusText = "Import failed: ${err.message ?: "Invalid PDF"}"
                }
            )
        }
    }

    fun burnRedactionToWorkspace() {
        val srcUri = selectedUri
        if (srcUri == null) {
            statusText = "Pick a source PDF first."
            return
        }

        val currentDraft = NormalizedRedactionRect(
            pageIndex = currentPageIndex,
            leftFraction = draftLeft,
            topFraction = draftTop,
            rightFraction = draftRight,
            bottomFraction = draftBottom
        ).clamped()

        val allRegions = (queuedRegions + currentDraft).filter { it.isValid() }
        if (allRegions.isEmpty()) {
            statusText = "Draw or add at least one valid redaction area."
            return
        }

        scope.launch {
            busy = true
            lastAudit = null
            redactedDoc = null
            statusText = "Burning redaction mask pixels & auditing text layer…"
            val result = runCatching {
                TruePdfRedactor.redactToWorkspaceAndVerify(
                    context = context,
                    inputUri = srcUri,
                    regions = allRegions,
                    stripMetadata = stripMetadata,
                    displayName = "PaperPlane-Redacted-${System.currentTimeMillis() % 10000}.pdf"
                )
            }
            result.fold(
                onSuccess = { (doc, summary) ->
                    redactedDoc = doc
                    lastAudit = summary
                    onWorkspaceChanged()
                    statusText = "True redaction verified: 0 extractable chars on masked pages."
                },
                onFailure = { err ->
                    statusText = "Redaction blocked: ${err.message ?: "Unknown error"}"
                }
            )
            busy = false
        }
    }

    val pageRegions = queuedRegions.filter { it.pageIndex == currentPageIndex }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
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
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = PaperDangerSoft,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text("True Pixel-Cut Redact", style = MaterialTheme.typography.titleLarge)
                            Text("Real pixel destruction + PDFTextStripper check", style = MaterialTheme.typography.bodySmall, color = PaperMuted)
                        }
                    }
                    StatusBadgePill(label = "Audit Safe", accent = PaperMint)
                }

                Text(statusText, style = MaterialTheme.typography.bodyMedium, color = PaperInfoText)

                WorkspaceDocSelectorStrip(
                    docs = workspaceDocs,
                    selectedDocId = selectedDocId,
                    busy = busy,
                    onSelectDoc = { doc ->
                        selectedDocId = doc.id
                        val docUri = Uri.fromFile(WorkspaceLibrary.getDocFile(context, doc))
                        selectedUri = docUri
                        queuedRegions = emptyList()
                        lastAudit = null
                        redactedDoc = null
                        currentPageIndex = 0
                        loadPage(docUri, 0)
                    },
                    onPickFromDevice = { pickPdf.launch(arrayOf("application/pdf")) },
                    onWorkspaceChanged = onWorkspaceChanged
                )
            }
        }

        val page = previewPage
        if (page != null) {
            item(key = "canvas_${page.pageIndex}_${page.totalPages}") {
                GlassWorkspaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Page ${page.pageIndex + 1} / ${page.totalPages}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    selectedUri?.let { loadPage(it, (page.pageIndex - 1).coerceAtLeast(0)) }
                                },
                                enabled = !busy && page.pageIndex > 0,
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous page")
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedUri?.let {
                                        loadPage(it, (page.pageIndex + 1).coerceAtMost(page.totalPages - 1))
                                    }
                                },
                                enabled = !busy && page.pageIndex < page.totalPages - 1,
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next page")
                            }
                        }
                    }

                    Text(
                        "Drag anywhere on the page below to draw the black redaction zone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted
                    )

                    RedactionDragPreview(
                        page = page,
                        queuedRegions = pageRegions,
                        draftRect = NormalizedRedactionRect(
                            pageIndex = page.pageIndex,
                            leftFraction = draftLeft,
                            topFraction = draftTop,
                            rightFraction = draftRight,
                            bottomFraction = draftBottom
                        ),
                        onDraftUpdated = { updated ->
                            draftLeft = updated.leftFraction
                            draftTop = updated.topFraction
                            draftRight = updated.rightFraction
                            draftBottom = updated.bottomFraction
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val nextBox = NormalizedRedactionRect(
                                    pageIndex = currentPageIndex,
                                    leftFraction = draftLeft,
                                    topFraction = draftTop,
                                    rightFraction = draftRight,
                                    bottomFraction = draftBottom
                                ).clamped()
                                if (nextBox.isValid()) {
                                    queuedRegions = queuedRegions + nextBox
                                    statusText = "Added region on page ${currentPageIndex + 1}. Queued total: ${queuedRegions.size}"
                                } else {
                                    statusText = "Drag a slightly larger area first."
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Queue Box (${queuedRegions.size})")
                        }

                        OutlinedButton(
                            onClick = {
                                queuedRegions = queuedRegions.filterNot { it.pageIndex == currentPageIndex }
                                statusText = "Cleared queued regions on page ${currentPageIndex + 1}."
                            },
                            enabled = !busy && pageRegions.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Clear Page (${pageRegions.size})")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strip Document Metadata", style = MaterialTheme.typography.titleSmall)
                            Text("Removes author, title, keywords & XMP identifying tags", style = MaterialTheme.typography.bodySmall, color = PaperMuted)
                        }
                        Switch(
                            checked = stripMetadata,
                            onCheckedChange = { stripMetadata = it },
                            enabled = !busy
                        )
                    }

                    Button(
                        onClick = { burnRedactionToWorkspace() },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaperDanger,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (busy) "Processing Redaction…" else "Burn Pixels & Verify Redaction")
                    }
                }
            }
        }

        val audit = lastAudit
        val savedRedacted = redactedDoc
        if (audit != null) {
            item(key = "audit_summary") {
                GlassWorkspaceCard(elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Post-Save Security Audit", style = MaterialTheme.typography.titleMedium)
                        StatusBadgePill(label = "0 Chars Leak Verified", accent = PaperMint)
                    }
                    Text(
                        "Total Pages: ${audit.totalPages} • Redacted Pages: ${audit.redactedPageCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Remaining text on redacted pages: ${audit.redactedPagesRemainingTextChars} chars\n" +
                            "Preserved text on untouched pages: ${audit.unredactedPagesRemainingTextChars} chars\n" +
                            "Verified output size: ${formatFileBytes(audit.outputBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted
                    )
                    if (savedRedacted != null) {
                        WorkspaceDocActionBar(
                            doc = savedRedacted,
                            onOpenViewer = onOpenViewer,
                            onStatusMessage = { statusText = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RedactionDragPreview(
    page: PdfPreviewPage,
    queuedRegions: List<NormalizedRedactionRect>,
    draftRect: NormalizedRedactionRect,
    onDraftUpdated: (NormalizedRedactionRect) -> Unit
) {
    val bmp = page.bitmap
    val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
    val density = LocalDensity.current
    var dragOrigin by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0B10))
            .border(1.dp, PaperGlassBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val scale = min(maxW / bmp.width.coerceAtLeast(1), maxH / bmp.height.coerceAtLeast(1))
        val fittedW = (bmp.width * scale).coerceAtLeast(1f)
        val fittedH = (bmp.height * scale).coerceAtLeast(1f)
        val widthDp = with(density) { fittedW.toDp() }
        val heightDp = with(density) { fittedH.toDp() }

        Box(
            modifier = Modifier
                .size(width = widthDp, height = heightDp)
                .pointerInput(page.pageIndex, fittedW, fittedH) {
                    detectDragGestures(
                        onDragStart = { start ->
                            dragOrigin = start
                            val nx = (start.x / fittedW).coerceIn(0f, 1f)
                            val ny = (start.y / fittedH).coerceIn(0f, 1f)
                            onDraftUpdated(
                                NormalizedRedactionRect(
                                    pageIndex = page.pageIndex,
                                    leftFraction = nx,
                                    topFraction = ny,
                                    rightFraction = (nx + 0.02f).coerceAtMost(1f),
                                    bottomFraction = (ny + 0.02f).coerceAtMost(1f)
                                )
                            )
                        },
                        onDrag = { change, _ ->
                            val anchor = dragOrigin ?: return@detectDragGestures
                            change.consume()
                            val ax = (anchor.x / fittedW).coerceIn(0f, 1f)
                            val ay = (anchor.y / fittedH).coerceIn(0f, 1f)
                            val cx = (change.position.x / fittedW).coerceIn(0f, 1f)
                            val cy = (change.position.y / fittedH).coerceIn(0f, 1f)
                            onDraftUpdated(
                                NormalizedRedactionRect(
                                    pageIndex = page.pageIndex,
                                    leftFraction = minOf(ax, cx),
                                    topFraction = minOf(ay, cy),
                                    rightFraction = maxOf(ax, cx),
                                    bottomFraction = maxOf(ay, cy)
                                )
                            )
                        }
                    )
                }
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "PDF page ${page.pageIndex + 1} preview",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (box in queuedRegions) {
                    val c = box.clamped()
                    val tl = Offset(c.leftFraction * size.width, c.topFraction * size.height)
                    val sz = Size(
                        (c.rightFraction - c.leftFraction) * size.width,
                        (c.bottomFraction - c.topFraction) * size.height
                    )
                    drawRect(color = Color.Black.copy(alpha = 0.85f), topLeft = tl, size = sz)
                    drawRect(color = PaperMint, topLeft = tl, size = sz, style = Stroke(width = 3f))
                }

                val d = draftRect.clamped()
                val tl = Offset(d.leftFraction * size.width, d.topFraction * size.height)
                val sz = Size(
                    (d.rightFraction - d.leftFraction) * size.width,
                    (d.bottomFraction - d.topFraction) * size.height
                )
                drawRect(color = Color.Black.copy(alpha = 0.65f), topLeft = tl, size = sz)
                drawRect(color = PaperDangerSoft, topLeft = tl, size = sz, style = Stroke(width = 4f))
            }
        }
    }
}
