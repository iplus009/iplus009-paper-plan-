package com.paperplan.app.feature.organizer

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.pdf.PageOrganizerState
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.WorkspaceDocActionBar
import com.paperplan.app.core.ui.components.WorkspaceDocSelectorStrip
import com.paperplan.app.core.ui.theme.PaperDanger
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import kotlinx.coroutines.launch

@Composable
fun PageOrganizerScreen(
    initialDoc: WorkspaceDoc?,
    workspaceDocs: List<WorkspaceDoc>,
    onBack: () -> Unit,
    onOpenViewer: (WorkspaceDoc) -> Unit,
    onWorkspaceChanged: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeDoc by remember(initialDoc?.id) {
        mutableStateOf(initialDoc ?: workspaceDocs.firstOrNull())
    }
    var organizerState by remember { mutableStateOf<PageOrganizerState?>(null) }
    var thumbnails by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var exportedDoc by remember { mutableStateOf<WorkspaceDoc?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Select a PDF to reorder, rotate +90° per page, or delete unwanted pages.")
    }

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusText = "Importing PDF into Workspace…"
            runCatching {
                WorkspaceLibrary.importFromUri(
                    context = context,
                    sourceUri = pickedUri,
                    displayName = "Source-${System.currentTimeMillis() % 10000}.pdf",
                    tag = "Imported"
                )
            }.fold(
                onSuccess = { imported ->
                    onWorkspaceChanged()
                    activeDoc = imported
                    exportedDoc = null
                },
                onFailure = { err ->
                    statusText = "Could not import PDF: ${err.message ?: "Invalid file"}"
                }
            )
            busy = false
        }
    }

    LaunchedEffect(activeDoc?.id) {
        val doc = activeDoc
        if (doc == null) {
            organizerState = null
            thumbnails = emptyMap()
            return@LaunchedEffect
        }
        busy = true
        exportedDoc = null
        statusText = "Loading thumbnails for ${doc.displayName}…"
        val uri = Uri.fromFile(WorkspaceLibrary.getDocFile(context, doc))
        runCatching {
            PdfEngine.loadPageThumbnails(context, uri, maxDimension = 260, maxPages = 48)
        }.fold(
            onSuccess = { (totalPages, list) ->
                if (totalPages > 0) {
                    organizerState = PageOrganizerState.initial(totalPages)
                    thumbnails = list.associate { it.pageIndex to it.bitmap }
                    val thumbNote = if (totalPages > list.size) {
                        " (showing first ${list.size} thumbnails)"
                    } else {
                        ""
                    }
                    statusText = "Loaded $totalPages page(s)$thumbNote. Arrange order or rotations below."
                } else {
                    organizerState = null
                    thumbnails = emptyMap()
                    statusText = "PDF has zero renderable pages."
                }
            },
            onFailure = { err ->
                organizerState = null
                thumbnails = emptyMap()
                statusText = "Thumbnail load failed: ${err.message ?: "Unknown error"}"
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
        item(key = "organizer_header") {
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
                            Text("Organize Pages", style = MaterialTheme.typography.titleLarge)
                            Text(
                                activeDoc?.displayName ?: "Reorder, rotate & remove pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    StatusBadgePill(label = "Visual", accent = PaperMint)
                }

                Text(statusText, style = MaterialTheme.typography.bodySmall, color = PaperInfoText)

                WorkspaceDocSelectorStrip(
                    docs = workspaceDocs,
                    selectedDocId = activeDoc?.id,
                    busy = busy,
                    onSelectDoc = { activeDoc = it },
                    onPickFromDevice = { pickPdf.launch(arrayOf("application/pdf")) },
                    onWorkspaceChanged = onWorkspaceChanged
                )
            }
        }

        val currentOrg = organizerState
        if (currentOrg != null) {
            itemsIndexed(
                items = currentOrg.ops,
                key = { _, op -> op.sourcePageIndex }
            ) { slotIndex, op ->
                val thumb = thumbnails[op.sourcePageIndex]
                GlassWorkspaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 76.dp, height = 98.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0A0B10))
                                .border(1.dp, PaperGlassBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumb != null) {
                                val imageBitmap = remember(thumb) { thumb.asImageBitmap() }
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Page ${op.sourcePageIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .graphicsLayer {
                                            rotationZ = op.extraRotationCw.toFloat()
                                        }
                                )
                            } else {
                                Text(
                                    text = "P.${op.sourcePageIndex + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PaperMuted
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Slot ${slotIndex + 1}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Orig Page ${op.sourcePageIndex + 1} • +${op.extraRotationCw}°",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PaperMuted
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedIconButton(
                                    onClick = {
                                        organizerState = currentOrg.movePage(slotIndex, slotIndex - 1)
                                    },
                                    enabled = !busy && slotIndex > 0,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move page up")
                                }

                                OutlinedIconButton(
                                    onClick = {
                                        organizerState = currentOrg.movePage(slotIndex, slotIndex + 1)
                                    },
                                    enabled = !busy && slotIndex < currentOrg.ops.lastIndex,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move page down")
                                }

                                OutlinedIconButton(
                                    onClick = {
                                        organizerState = currentOrg.rotatePage(slotIndex, 90)
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate +90 degrees")
                                }

                                OutlinedIconButton(
                                    onClick = {
                                        organizerState = currentOrg.deletePage(slotIndex)
                                    },
                                    enabled = !busy && currentOrg.ops.size > 1,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete page",
                                        tint = if (currentOrg.ops.size > 1) PaperDanger else PaperMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "organizer_export") {
                val doc = activeDoc
                GlassWorkspaceCard(elevated = true) {
                    Button(
                        onClick = {
                            if (doc == null) return@Button
                            scope.launch {
                                busy = true
                                statusText = "Building organized PDF with ${currentOrg.ops.size} page(s)…"
                                val uri = Uri.fromFile(WorkspaceLibrary.getDocFile(context, doc))
                                val outName = "Organized-${doc.displayName.removeSuffix(".pdf")}.pdf"
                                runCatching {
                                    PdfEngine.organizePagesToWorkspace(
                                        context = context,
                                        inputUri = uri,
                                        operations = currentOrg.ops,
                                        displayName = outName
                                    )
                                }.fold(
                                    onSuccess = { saved ->
                                        exportedDoc = saved
                                        onWorkspaceChanged()
                                        statusText = "Saved organized PDF (${saved.pageCount} pages)."
                                    },
                                    onFailure = { err ->
                                        statusText = "Export failed: ${err.message ?: "Unknown error"}"
                                    }
                                )
                                busy = false
                            }
                        },
                        enabled = !busy && doc != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.ViewAgenda, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (busy) "Saving Organized PDF…" else "Export Organized PDF (${currentOrg.ops.size}p)"
                        )
                    }
                }
            }
        }

        val outDoc = exportedDoc
        if (outDoc != null) {
            item(key = "organizer_output_${outDoc.id}") {
                GlassWorkspaceCard(elevated = true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PaperMint)
                        Text("Organized PDF Ready", style = MaterialTheme.typography.titleMedium)
                    }
                    WorkspaceDocActionBar(
                        doc = outDoc,
                        onOpenViewer = onOpenViewer,
                        onStatusMessage = { statusText = it }
                    )
                }
            }
        }
    }
}
