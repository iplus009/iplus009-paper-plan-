package com.paperplan.app.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperplan.app.R
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.WorkspaceDocActionBar
import com.paperplan.app.core.ui.components.formatFileBytes
import com.paperplan.app.core.ui.components.shareWorkspaceDoc
import com.paperplan.app.core.ui.theme.PaperDanger
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import com.paperplan.app.core.ui.theme.PaperSurfaceElevated
import com.paperplan.app.core.ui.theme.PaperViolet
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    workspaceDocs: List<WorkspaceDoc>,
    onWorkspaceChanged: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenRedact: () -> Unit,
    onOpenBangla: () -> Unit,
    onOpenViewer: (WorkspaceDoc?) -> Unit,
    onOpenOrganize: (WorkspaceDoc?) -> Unit,
    onOpenCompress: (WorkspaceDoc?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var lastUtilityDoc by remember { mutableStateOf<WorkspaceDoc?>(null) }
    var pendingSplitSourceUri by remember { mutableStateOf<Uri?>(null) }
    var statusText by remember {
        mutableStateOf("Every tool below is live on-device. Create a Sample PDF or tap any tool to begin.")
    }

    val importPdfLauncher = rememberLauncherForActivityResult(
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
                onSuccess = { doc ->
                    lastUtilityDoc = doc
                    onWorkspaceChanged()
                    statusText = "Imported ${doc.displayName} (${doc.pageCount} page(s))."
                },
                onFailure = { err ->
                    statusText = "Import failed: ${err.message ?: "Invalid PDF"}"
                }
            )
            busy = false
        }
    }

    val mergePickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { inputs ->
        if (inputs.size < 2) {
            statusText = "Select at least 2 PDF files to merge."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            statusText = "Merging ${inputs.size} PDFs cleanly…"
            runCatching {
                PdfEngine.mergeToWorkspace(
                    context = context,
                    inputs = inputs,
                    displayName = "PaperPlane-Merged-${System.currentTimeMillis() % 10000}.pdf"
                )
            }.fold(
                onSuccess = { doc ->
                    lastUtilityDoc = doc
                    onWorkspaceChanged()
                    statusText = "Merged ${inputs.size} PDFs -> ${doc.pageCount} pages (${formatFileBytes(doc.sizeBytes)})."
                },
                onFailure = { err ->
                    statusText = "Merge failed: ${err.message ?: "Unknown error"}"
                }
            )
            busy = false
        }
    }

    val rotatePickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusText = "Rotating all pages +90° CW…"
            runCatching {
                PdfEngine.rotateAllPagesToWorkspace(
                    context = context,
                    inputUri = sourceUri,
                    clockwiseDegrees = 90,
                    displayName = "PaperPlane-Rotated-${System.currentTimeMillis() % 10000}.pdf"
                )
            }.fold(
                onSuccess = { doc ->
                    lastUtilityDoc = doc
                    onWorkspaceChanged()
                    statusText = "Rotated ${doc.pageCount} page(s) into ${doc.displayName}."
                },
                onFailure = { err ->
                    statusText = "Rotation failed: ${err.message ?: "Unknown error"}"
                }
            )
            busy = false
        }
    }

    val splitSaveZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { zipUri ->
        val src = pendingSplitSourceUri
        pendingSplitSourceUri = null
        if (zipUri == null || src == null) {
            statusText = "Split cancelled."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            statusText = "Splitting each page into individual PDFs inside ZIP…"
            runCatching {
                PdfEngine.splitIntoZip(context = context, inputUri = src, outputUri = zipUri)
            }.fold(
                onSuccess = { summary ->
                    statusText = "Split ${summary.pageCount} page(s) into ZIP (${formatFileBytes(summary.outputBytes)})."
                },
                onFailure = { err ->
                    statusText = "Split failed: ${err.message ?: "Unknown error"}"
                }
            )
            busy = false
        }
    }

    val splitPickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri == null) return@rememberLauncherForActivityResult
        pendingSplitSourceUri = sourceUri
        splitSaveZipLauncher.launch("PaperPlane-SplitPages.zip")
    }

    fun triggerAction(action: HomeToolAction) {
        when (action) {
            HomeToolAction.SCAN -> onOpenScan()
            HomeToolAction.VIEWER -> onOpenViewer(workspaceDocs.firstOrNull())
            HomeToolAction.ORGANIZE -> onOpenOrganize(workspaceDocs.firstOrNull())
            HomeToolAction.COMPRESS -> onOpenCompress(workspaceDocs.firstOrNull())
            HomeToolAction.MERGE -> mergePickLauncher.launch(arrayOf("application/pdf"))
            HomeToolAction.ROTATE -> rotatePickLauncher.launch(arrayOf("application/pdf"))
            HomeToolAction.SPLIT -> splitPickLauncher.launch(arrayOf("application/pdf"))
            HomeToolAction.REDACT -> onOpenRedact()
            HomeToolAction.BANGLA -> onOpenBangla()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "home_brand_hero") {
            GlassWorkspaceCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0B003A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_paper_plane_logo),
                                contentDescription = stringResource(id = R.string.app_name),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(id = R.string.app_brand_line),
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperMuted
                            )
                        }
                    }
                    StatusBadgePill(label = "v0.2.1 Live", accent = PaperMint)
                }

                Text(statusText, style = MaterialTheme.typography.bodySmall, color = PaperInfoText)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                statusText = "Generating 3-page bilingual sample PDF in Workspace…"
                                runCatching { PdfEngine.createSampleVerificationPdf(context) }.fold(
                                    onSuccess = { doc ->
                                        lastUtilityDoc = doc
                                        onWorkspaceChanged()
                                        statusText = "Created ${doc.displayName} (${doc.pageCount} pages). Tap View or any tool."
                                    },
                                    onFailure = { err ->
                                        statusText = "Sample creation failed: ${err.message ?: "Unknown error"}"
                                    }
                                )
                                busy = false
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = PaperViolet),
                        modifier = Modifier
                            .weight(1.2f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Sample 3p PDF", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                        enabled = !busy,
                        modifier = Modifier
                            .weight(0.9f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Import", maxLines = 1)
                    }
                }
            }
        }

        val highlightDoc = lastUtilityDoc
        if (highlightDoc != null) {
            item(key = "home_latest_${highlightDoc.id}") {
                GlassWorkspaceCard(elevated = true) {
                    Text("Ready in Workspace", style = MaterialTheme.typography.labelLarge, color = PaperMint)
                    WorkspaceDocActionBar(
                        doc = highlightDoc,
                        onOpenViewer = { onOpenViewer(it) },
                        onStatusMessage = { statusText = it }
                    )
                }
            }
        }

        item(key = "home_tools_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live PDF & GPU Tools", style = MaterialTheme.typography.titleMedium)
                Text("${livePaperPlaneTools.size} Active", style = MaterialTheme.typography.labelMedium, color = PaperMint)
            }
        }

        items(
            items = livePaperPlaneTools.chunked(2),
            key = { pair -> pair.joinToString("_") { it.action.name } }
        ) { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTools.forEach { tool ->
                    HomeToolGridCard(
                        tool = tool,
                        enabled = !busy,
                        onClick = { triggerAction(tool.action) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowTools.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        item(key = "home_workspace_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Workspace Library", style = MaterialTheme.typography.titleMedium)
                Text("${workspaceDocs.size} PDF(s)", style = MaterialTheme.typography.labelMedium, color = PaperMuted)
            }
        }

        if (workspaceDocs.isEmpty()) {
            item(key = "home_workspace_empty") {
                GlassWorkspaceCard {
                    Text(
                        text = "Your Paper Plane Workspace is empty. Tap \"Sample 3p PDF\" above, scan a document on the Scan tab, or import any PDF from storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted
                    )
                }
            }
        } else {
            items(workspaceDocs, key = { it.id }) { doc ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PaperSurfaceElevated,
                    border = BorderStroke(1.dp, PaperGlassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { onOpenViewer(doc) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = null,
                            tint = PaperMint,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${doc.pageCount}p • ${formatFileBytes(doc.sizeBytes)} • ${doc.tag}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperMuted,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { onOpenViewer(doc) },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Visibility, contentDescription = "Open in PDF Viewer")
                        }
                        IconButton(
                            onClick = { shareWorkspaceDoc(context, doc) { statusText = it } },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share PDF")
                        }
                        IconButton(
                            onClick = {
                                WorkspaceLibrary.deleteDoc(context, doc.id)
                                if (lastUtilityDoc?.id == doc.id) lastUtilityDoc = null
                                onWorkspaceChanged()
                                statusText = "Removed ${doc.displayName} from Workspace."
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete from Workspace",
                                tint = PaperDanger
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeToolGridCard(
    tool: HomeActionTool,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PaperSurfaceElevated,
        border = BorderStroke(1.dp, PaperGlassBorder),
        modifier = modifier
            .defaultMinSize(minHeight = 132.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tool.action.icon(),
                    contentDescription = null,
                    tint = tool.accent,
                    modifier = Modifier.size(24.dp)
                )
                StatusBadgePill(label = tool.badge, accent = tool.accent)
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PaperMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun HomeToolAction.icon(): ImageVector = when (this) {
    HomeToolAction.SCAN -> Icons.Outlined.DocumentScanner
    HomeToolAction.VIEWER -> Icons.Outlined.Visibility
    HomeToolAction.ORGANIZE -> Icons.Outlined.ViewAgenda
    HomeToolAction.COMPRESS -> Icons.Outlined.Compress
    HomeToolAction.MERGE -> Icons.Outlined.LibraryAdd
    HomeToolAction.ROTATE -> Icons.Outlined.RotateRight
    HomeToolAction.SPLIT -> Icons.Outlined.ContentCut
    HomeToolAction.REDACT -> Icons.Outlined.Security
    HomeToolAction.BANGLA -> Icons.Outlined.Translate
}
