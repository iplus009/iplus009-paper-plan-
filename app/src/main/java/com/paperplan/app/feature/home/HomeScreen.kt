package com.paperplan.app.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paperplan.app.BuildConfig
import com.paperplan.app.R
import com.paperplan.app.core.pdf.PdfEngine
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.formatFileBytes
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import com.paperplan.app.core.ui.theme.PaperViolet
import kotlinx.coroutines.launch

private enum class PdfAction { MERGE, ROTATE, SPLIT }

@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenRedact: () -> Unit = {},
    onOpenGpuLab: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingAction by remember { mutableStateOf<PdfAction?>(null) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var status by remember { mutableStateOf("Ready. Originals stay untouched until SAF write succeeds.") }
    var busy by remember { mutableStateOf(false) }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { output ->
        val action = pendingAction
        if (output == null) {
            status = "Save cancelled. Your original files were not modified."
            pendingAction = null
            pendingUris = emptyList()
            return@rememberLauncherForActivityResult
        }
        if (action == null || pendingUris.isEmpty()) return@rememberLauncherForActivityResult

        val sources = pendingUris
        scope.launch {
            busy = true
            status = "Processing PDF using disk-backed stream…"
            val result = runCatching {
                when (action) {
                    PdfAction.MERGE -> PdfEngine.merge(context, sources, output)
                    PdfAction.ROTATE -> PdfEngine.rotateAllPages(context, sources.single(), output)
                    PdfAction.SPLIT -> error("Select ZIP output for split.")
                }
            }
            status = result.fold(
                onSuccess = { s ->
                    "Done: ${s.pageCount} page(s) verified (${formatFileBytes(s.outputBytes)})."
                },
                onFailure = { err ->
                    "Failed: ${err.message ?: "Unknown error"}"
                }
            )
            busy = false
            pendingAction = null
            pendingUris = emptyList()
        }
    }

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { output ->
        val sources = pendingUris
        if (output == null) {
            status = "Save cancelled. Your original file was not modified."
            pendingAction = null
            pendingUris = emptyList()
            return@rememberLauncherForActivityResult
        }
        if (pendingAction != PdfAction.SPLIT || sources.isEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            busy = true
            status = "Splitting PDF pages into verified ZIP…"
            val result = runCatching {
                PdfEngine.splitIntoZip(context, sources.single(), output)
            }
            status = result.fold(
                onSuccess = { s ->
                    "Done: ${s.pageCount} page PDFs packed into ZIP (${formatFileBytes(s.outputBytes)})."
                },
                onFailure = { err ->
                    "Failed: ${err.message ?: "Unknown error"}"
                }
            )
            busy = false
            pendingAction = null
            pendingUris = emptyList()
        }
    }

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size >= 2) {
            pendingUris = uris
            pendingAction = PdfAction.MERGE
            savePdf.launch("PaperPlan-Merged.pdf")
        } else {
            status = "Select at least two PDF files to merge."
        }
    }

    val pickRotate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUris = listOf(uri)
            pendingAction = PdfAction.ROTATE
            savePdf.launch("PaperPlan-Rotated.pdf")
        }
    }

    val pickSplit = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUris = listOf(uri)
            pendingAction = PdfAction.SPLIT
            saveZip.launch("PaperPlan-Split-Pages.zip")
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
        item(key = "brand_header") {
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
                    Image(
                        painter = painterResource(R.drawable.ic_paper_plan_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(48.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.brand_name),
                            style = MaterialTheme.typography.labelMedium,
                            color = PaperMint
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.app_brand_line),
                            style = MaterialTheme.typography.bodySmall,
                            color = PaperMuted
                        )
                    }
                }
                StatusBadgePill(label = "v${BuildConfig.VERSION_NAME}", accent = PaperViolet)
            }
        }

        item(key = "engine_status") {
            GlassWorkspaceCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GPU & TempFile PDF Core", style = MaterialTheme.typography.titleMedium)
                    StatusBadgePill(label = "Phase 0 Spike", accent = PaperMint)
                }
                Text(
                    "Disk-backed TempFile memory mode prevents heap OOM crashes on large PDFs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperMuted
                )
                Text(status, style = MaterialTheme.typography.bodyMedium, color = PaperInfoText)
                if (busy) {
                    Text(
                        "Processing on disk… keep Paper Plan in foreground.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMint
                    )
                }
            }
        }

        item(key = "quick_labs") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenRedact,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("True Redact", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenGpuLab,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("GPU & Bangla", maxLines = 1)
                }
            }
        }

        item(key = "section_live_pdf") {
            Text("Live On-Device PDF Tools", style = MaterialTheme.typography.titleMedium)
        }

        item(key = "tool_merge") {
            PdfToolActionCard(
                icon = Icons.Outlined.Merge,
                title = "Merge PDFs",
                description = "Combine 2+ PDFs stream-safely and verify %PDF- / %%EOF before finalizing.",
                buttonLabel = "Select 2+ PDFs",
                enabled = !busy,
                onClick = { pickMany.launch(arrayOf("application/pdf")) }
            )
        }

        item(key = "tool_rotate") {
            PdfToolActionCard(
                icon = Icons.Outlined.RotateRight,
                title = "Rotate PDF Pages",
                description = "Rotate every page 90° clockwise into a fresh file.",
                buttonLabel = "Choose PDF",
                enabled = !busy,
                onClick = { pickRotate.launch(arrayOf("application/pdf")) }
            )
        }

        item(key = "tool_split") {
            PdfToolActionCard(
                icon = Icons.Outlined.CallSplit,
                title = "Split to Verified ZIP",
                description = "Export each page as its own standalone verified PDF inside a ZIP.",
                buttonLabel = "Choose PDF",
                enabled = !busy,
                onClick = { pickSplit.launch(arrayOf("application/pdf")) }
            )
        }

        item(key = "section_roadmap") {
            Text("Phase Roadmap & Status", style = MaterialTheme.typography.titleMedium)
        }

        items(
            items = paperPlanTools,
            key = { it.id }
        ) { item ->
            val accent = when (item.status) {
                ToolStatus.LIVE -> PaperMint
                ToolStatus.SPIKE_LAB -> PaperViolet
                ToolStatus.PLANNED -> PaperMuted
            }
            GlassWorkspaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = PaperMuted)
                    }
                    StatusBadgePill(label = item.status.badgeText, accent = accent)
                }
            }
        }
    }
}

@Composable
private fun PdfToolActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    GlassWorkspaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PaperViolet, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = PaperMuted)
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Text(buttonLabel)
        }
    }
}
