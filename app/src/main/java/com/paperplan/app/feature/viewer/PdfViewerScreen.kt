package com.paperplan.app.feature.viewer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.pdf.PdfPageRender
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.GpuZoomableBitmapPreview
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.WorkspaceDocActionBar
import com.paperplan.app.core.ui.components.WorkspaceDocSelectorStrip
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import com.paperplan.app.core.ui.theme.PaperScanBlue
import com.paperplan.app.core.ui.theme.PaperViolet
import kotlinx.coroutines.launch

@Composable
fun PdfViewerScreen(
    initialDoc: WorkspaceDoc?,
    workspaceDocs: List<WorkspaceDoc>,
    onBack: () -> Unit,
    onWorkspaceChanged: () -> Unit,
    onOpenOrganize: (WorkspaceDoc) -> Unit,
    onOpenCompress: (WorkspaceDoc) -> Unit,
    onOpenRedact: (WorkspaceDoc) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeDoc by remember(initialDoc?.id) {
        mutableStateOf(initialDoc ?: workspaceDocs.firstOrNull())
    }
    var pageIndex by remember(activeDoc?.id) { mutableIntStateOf(0) }
    var renderedPage by remember { mutableStateOf<PdfPageRender?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Pick or open a PDF to inspect pages with GPU zoom.") }

    val pickExternalPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusText = "Importing PDF into Paper Plane Workspace…"
            runCatching {
                WorkspaceLibrary.importFromUri(
                    context = context,
                    sourceUri = pickedUri,
                    displayName = "Imported-${System.currentTimeMillis() % 10000}.pdf",
                    tag = "Imported"
                )
            }.fold(
                onSuccess = { imported ->
                    onWorkspaceChanged()
                    activeDoc = imported
                    pageIndex = 0
                    statusText = "Opened ${imported.displayName}."
                },
                onFailure = { err ->
                    statusText = "Import failed: ${err.message ?: "Invalid PDF"}"
                }
            )
            busy = false
        }
    }

    LaunchedEffect(activeDoc?.id, pageIndex) {
        val doc = activeDoc
        if (doc == null) {
            renderedPage = null
            return@LaunchedEffect
        }
        val fileUri = Uri.fromFile(WorkspaceLibrary.getDocFile(context, doc))
        busy = true
        runCatching {
            PdfEngine.renderPageBitmap(context, fileUri, pageIndex, maxDimension = 1600)
        }.fold(
            onSuccess = { render ->
                renderedPage = render
                pageIndex = render.pageIndex
                statusText = "Viewing page ${render.pageIndex + 1} of ${render.totalPages} (pinch or drag to zoom)."
            },
            onFailure = { err ->
                renderedPage = null
                statusText = "Could not render page: ${err.message ?: "Corrupt PDF"}"
            }
        )
        busy = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "viewer_header") {
            GlassWorkspaceCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Tools")
                        }
                        Column {
                            Text(
                                text = activeDoc?.displayName ?: "PDF Viewer",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = renderedPage?.let { "Page ${it.pageIndex + 1} of ${it.totalPages}" }
                                    ?: "GPU Pinch-to-Zoom Reader",
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperMuted
                            )
                        }
                    }
                    StatusBadgePill(label = "GPU Reader", accent = PaperScanBlue)
                }

                Text(statusText, style = MaterialTheme.typography.bodySmall, color = PaperInfoText)

                WorkspaceDocSelectorStrip(
                    docs = workspaceDocs,
                    selectedDocId = activeDoc?.id,
                    busy = busy,
                    onSelectDoc = {
                        activeDoc = it
                        pageIndex = 0
                    },
                    onPickFromDevice = { pickExternalPdf.launch(arrayOf("application/pdf")) },
                    onWorkspaceChanged = onWorkspaceChanged
                )
            }
        }

        val currentRender = renderedPage
        val doc = activeDoc
        if (currentRender != null && doc != null) {
            item(key = "viewer_stage_${doc.id}_${currentRender.pageIndex}") {
                GlassWorkspaceCard {
                    GpuZoomableBitmapPreview(
                        bitmap = currentRender.bitmap,
                        contentDescription = "PDF page ${currentRender.pageIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { pageIndex = (currentRender.pageIndex - 1).coerceAtLeast(0) },
                            enabled = !busy && currentRender.pageIndex > 0,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("Prev")
                        }

                        Text(
                            text = "Page ${currentRender.pageIndex + 1} / ${currentRender.totalPages}",
                            style = MaterialTheme.typography.titleSmall
                        )

                        OutlinedButton(
                            onClick = {
                                pageIndex = (currentRender.pageIndex + 1)
                                    .coerceAtMost(currentRender.totalPages - 1)
                            },
                            enabled = !busy && currentRender.pageIndex < currentRender.totalPages - 1,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Text("Next")
                            Spacer(Modifier.size(4.dp))
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }

                    if (currentRender.totalPages > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items((0 until currentRender.totalPages).toList(), key = { it }) { idx ->
                                val selected = idx == currentRender.pageIndex
                                Button(
                                    onClick = { pageIndex = idx },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) PaperViolet else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Text("${idx + 1}")
                                }
                            }
                        }
                    }
                }
            }

            item(key = "viewer_actions_${doc.id}") {
                GlassWorkspaceCard(elevated = true) {
                    WorkspaceDocActionBar(
                        doc = doc,
                        onStatusMessage = { statusText = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenOrganize(doc) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Organize", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onOpenCompress(doc) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Compress", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onOpenRedact(doc) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Redact", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
