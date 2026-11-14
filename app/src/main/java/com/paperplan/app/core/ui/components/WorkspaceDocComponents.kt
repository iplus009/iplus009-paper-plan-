package com.paperplan.app.core.ui.components

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import com.paperplan.app.core.ui.theme.PaperSurface
import com.paperplan.app.core.ui.theme.PaperSurfaceElevated
import com.paperplan.app.core.ui.theme.PaperViolet
import kotlinx.coroutines.launch

@Composable
fun WorkspaceDocSelectorStrip(
    docs: List<WorkspaceDoc>,
    selectedDocId: String?,
    busy: Boolean,
    onSelectDoc: (WorkspaceDoc) -> Unit,
    onPickFromDevice: () -> Unit,
    onWorkspaceChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creatingSample by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (docs.isNotEmpty()) {
            Text(
                text = "Tap a Workspace PDF (${docs.size})",
                style = MaterialTheme.typography.labelLarge,
                color = PaperMuted
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(docs, key = { it.id }) { doc ->
                    val isSelected = doc.id == selectedDocId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) PaperSurfaceElevated else PaperSurface,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) PaperViolet else PaperGlassBorder
                        ),
                        modifier = Modifier
                            .widthIn(min = 148.dp, max = 220.dp)
                            .defaultMinSize(minHeight = 56.dp)
                            .clickable(enabled = !busy) { onSelectDoc(doc) }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = doc.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${doc.pageCount}p • ${formatFileBytes(doc.sizeBytes)} • ${doc.tag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) PaperMint else PaperMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No Workspace PDFs yet. Create a test PDF or pick one from device storage.",
                style = MaterialTheme.typography.bodySmall,
                color = PaperMuted
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickFromDevice,
                enabled = !busy && !creatingSample,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Device PDF", maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        creatingSample = true
                        val created = runCatching { PdfEngine.createSampleVerificationPdf(context) }.getOrNull()
                        creatingSample = false
                        if (created != null) {
                            onWorkspaceChanged()
                            onSelectDoc(created)
                        }
                    }
                },
                enabled = !busy && !creatingSample,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (creatingSample) "Creating…" else "Sample PDF", maxLines = 1)
            }
        }
    }
}

@Composable
fun WorkspaceDocActionBar(
    doc: WorkspaceDoc,
    onOpenViewer: ((WorkspaceDoc) -> Unit)? = null,
    onStatusMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val saveSafLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri == null) {
            onStatusMessage("SAF save cancelled.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching { WorkspaceLibrary.exportToSaf(context, doc, destUri) }.fold(
                onSuccess = { bytes ->
                    onStatusMessage("Saved copy (${formatFileBytes(bytes)}) via Storage Access Framework.")
                },
                onFailure = { err ->
                    onStatusMessage("SAF save failed: ${err.message ?: "Unknown error"}")
                }
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = PaperMint,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = doc.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${doc.pageCount} page(s) • ${formatFileBytes(doc.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperInfoText
                    )
                }
            }
            StatusBadgePill(label = doc.tag, accent = PaperMint)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onOpenViewer != null) {
                Button(
                    onClick = { onOpenViewer(doc) },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperViolet),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("View", maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = { saveSafLauncher.launch(doc.displayName) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(4.dp))
                Text("Save", maxLines = 1)
            }

            OutlinedButton(
                onClick = { shareWorkspaceDoc(context, doc, onStatusMessage) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(4.dp))
                Text("Share", maxLines = 1)
            }
        }
    }
}

fun shareWorkspaceDoc(
    context: Context,
    doc: WorkspaceDoc,
    onStatusMessage: (String) -> Unit = {}
) {
    runCatching {
        val shareIntent = WorkspaceLibrary.buildShareIntent(context, doc)
        val chooser = Intent.createChooser(shareIntent, "Share ${doc.displayName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.onFailure { err ->
        onStatusMessage("Share failed: ${err.message ?: "No PDF share target available"}")
    }
}
