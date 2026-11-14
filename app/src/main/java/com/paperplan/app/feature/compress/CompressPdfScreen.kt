package com.paperplan.app.feature.compress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.pdf.CompressionPreset
import com.paperplan.app.core.pdf.PdfCompressionReport
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.WorkspaceDocActionBar
import com.paperplan.app.core.ui.components.WorkspaceDocSelectorStrip
import com.paperplan.app.core.ui.components.formatFileBytes
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import com.paperplan.app.core.ui.theme.PaperScanBlue
import com.paperplan.app.core.ui.theme.PaperSurface
import com.paperplan.app.core.ui.theme.PaperSurfaceElevated
import com.paperplan.app.core.ui.theme.PaperViolet
import kotlinx.coroutines.launch

@Composable
fun CompressPdfScreen(
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
    var selectedPreset by remember { mutableStateOf(CompressionPreset.BALANCED) }
    var stripMetadata by remember { mutableStateOf(true) }
    var report by remember { mutableStateOf<PdfCompressionReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Pick any PDF and choose a target quality preset to reduce file size safely.")
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
                    displayName = "CompressSrc-${System.currentTimeMillis() % 10000}.pdf",
                    tag = "Imported"
                )
            }.fold(
                onSuccess = { imported ->
                    onWorkspaceChanged()
                    activeDoc = imported
                    report = null
                    statusText = "Selected ${imported.displayName} (${formatFileBytes(imported.sizeBytes)})."
                },
                onFailure = { err ->
                    statusText = "Import failed: ${err.message ?: "Invalid PDF"}"
                }
            )
            busy = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "compress_header") {
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
                            Text("Compress PDF", style = MaterialTheme.typography.titleLarge)
                            Text(
                                activeDoc?.displayName ?: "Size optimizer with fallback guard",
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    StatusBadgePill(label = "Optimizer", accent = PaperScanBlue)
                }

                Text(statusText, style = MaterialTheme.typography.bodySmall, color = PaperInfoText)

                WorkspaceDocSelectorStrip(
                    docs = workspaceDocs,
                    selectedDocId = activeDoc?.id,
                    busy = busy,
                    onSelectDoc = {
                        activeDoc = it
                        report = null
                        statusText = "Ready to compress ${it.displayName} (${formatFileBytes(it.sizeBytes)})."
                    },
                    onPickFromDevice = { pickPdf.launch(arrayOf("application/pdf")) },
                    onWorkspaceChanged = onWorkspaceChanged
                )
            }
        }

        item(key = "compress_presets") {
            GlassWorkspaceCard {
                Text("Compression Preset", style = MaterialTheme.typography.titleMedium)

                CompressionPreset.entries.forEach { preset ->
                    val selected = preset == selectedPreset
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) PaperSurfaceElevated else PaperSurface,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) PaperViolet else PaperGlassBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clickable(enabled = !busy) { selectedPreset = preset }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { selectedPreset = preset },
                                enabled = !busy
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PaperMuted
                                )
                            }
                        }
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
                        Text("Strip Metadata Tags", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Removes author, producer & XMP metadata overhead",
                            style = MaterialTheme.typography.bodySmall,
                            color = PaperMuted
                        )
                    }
                    Switch(
                        checked = stripMetadata,
                        onCheckedChange = { stripMetadata = it },
                        enabled = !busy
                    )
                }

                val doc = activeDoc
                Button(
                    onClick = {
                        if (doc == null) return@Button
                        scope.launch {
                            busy = true
                            report = null
                            statusText = "Compressing ${doc.displayName} with ${selectedPreset.label}…"
                            val uri = Uri.fromFile(WorkspaceLibrary.getDocFile(context, doc))
                            val outName = "Compressed-${doc.displayName.removeSuffix(".pdf")}.pdf"
                            runCatching {
                                PdfEngine.compressPdfToWorkspace(
                                    context = context,
                                    inputUri = uri,
                                    preset = selectedPreset,
                                    stripMetadata = stripMetadata,
                                    displayName = outName
                                )
                            }.fold(
                                onSuccess = { res ->
                                    report = res
                                    onWorkspaceChanged()
                                    statusText = if (res.summary.usedOriginalFallback) {
                                        "Original PDF was already smaller than compressed output — kept lossless original."
                                    } else {
                                        "Reduced by ${res.summary.savedPercent}% (${formatFileBytes(res.summary.originalBytes)} -> ${formatFileBytes(res.summary.compressedBytes)})."
                                    }
                                },
                                onFailure = { err ->
                                    statusText = "Compression failed: ${err.message ?: "Unknown error"}"
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
                    Icon(Icons.Outlined.Compress, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (busy) "Compressing…" else "Compress PDF Now")
                }
            }
        }

        val currentReport = report
        if (currentReport != null) {
            item(key = "compress_report_${currentReport.doc.id}") {
                val summary = currentReport.summary
                GlassWorkspaceCard(elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PaperMint)
                            Text("Compression Summary", style = MaterialTheme.typography.titleMedium)
                        }
                        StatusBadgePill(
                            label = if (summary.usedOriginalFallback) "Original Kept" else "-${summary.savedPercent}%",
                            accent = PaperMint
                        )
                    }

                    Text(
                        text = "Original: ${formatFileBytes(summary.originalBytes)}  •  Output: ${formatFileBytes(summary.compressedBytes)}\nPages preserved: ${summary.pageCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaperInfoText
                    )

                    WorkspaceDocActionBar(
                        doc = currentReport.doc,
                        onOpenViewer = onOpenViewer,
                        onStatusMessage = { statusText = it }
                    )
                }
            }
        }
    }
}
