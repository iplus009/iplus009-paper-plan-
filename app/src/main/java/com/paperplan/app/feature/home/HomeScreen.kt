package com.paperplan.app.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.paperplan.app.R
import com.paperplan.app.core.pdf.PdfToolkit
import com.paperplan.app.core.ui.theme.PaperMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PdfAction { MERGE, ROTATE, SPLIT }

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<PdfAction?>(null) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var status by remember { mutableStateOf("Choose a tool to get started.") }
    var busy by remember { mutableStateOf(false) }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { output ->
        val action = pendingAction
        if (output != null && action != null) {
            scope.launch {
                busy = true
                status = "Processing PDF…"
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(output)?.use { stream ->
                            val toolkit = PdfToolkit(context)
                            when (action) {
                                PdfAction.MERGE -> toolkit.merge(pendingUris, stream)
                                PdfAction.ROTATE -> toolkit.rotateAllPages(pendingUris.single(), stream)
                                PdfAction.SPLIT -> error("Select ZIP output for split.")
                            }
                        } ?: error("Could not create the output file.")
                    }
                }
                status = result.fold({ "Done. Your new PDF has been saved." }, { "Failed: ${it.message ?: "Unknown error"}" })
                busy = false
                pendingAction = null
                pendingUris = emptyList()
            }
        } else if (output == null) {
            status = "Save cancelled. Your original files were not changed."
        }
    }

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { output ->
        val action = pendingAction
        if (output != null && action == PdfAction.SPLIT && pendingUris.isNotEmpty()) {
            scope.launch {
                busy = true
                status = "Splitting pages…"
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(output)?.use { stream ->
                            PdfToolkit(context).splitIntoZip(pendingUris.single(), stream)
                        } ?: error("Could not create the ZIP file.")
                    }
                }
                status = result.fold({ "Done. Each page is saved as a separate PDF in the ZIP." }, { "Failed: ${it.message ?: "Unknown error"}" })
                busy = false
                pendingAction = null
                pendingUris = emptyList()
            }
        } else if (output == null) {
            status = "Save cancelled. Your original file was not changed."
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_paper_plan_foreground),
                        contentDescription = "Paper Plan logo",
                        modifier = Modifier.size(48.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("iPlus presents", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("Paper Plan", style = MaterialTheme.typography.headlineLarge)
                        Text("Make your own PDF better than anyone.", color = PaperMuted)
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PDF Toolkit", style = MaterialTheme.typography.titleLarge)
                        Text("These tools process files on your device. Originals are kept unchanged.", color = PaperMuted)
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        if (busy) Text("Please keep Paper Plan open until processing finishes.", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item { Text("Working tools", style = MaterialTheme.typography.titleLarge) }
            item {
                ToolCard(
                    title = "Merge PDFs",
                    description = "Combine two or more PDF files into one document.",
                    enabled = !busy,
                    action = { pickMany.launch(arrayOf("application/pdf")) },
                    button = "Select PDFs"
                )
            }
            item {
                ToolCard(
                    title = "Rotate PDF",
                    description = "Rotate every page 90° clockwise and save a new PDF.",
                    enabled = !busy,
                    action = { pickRotate.launch(arrayOf("application/pdf")) },
                    button = "Choose PDF"
                )
            }
            item {
                ToolCard(
                    title = "Split PDF",
                    description = "Export every page as a separate PDF inside a ZIP file.",
                    enabled = !busy,
                    action = { pickSplit.launch(arrayOf("application/pdf")) },
                    button = "Choose PDF"
                )
            }
            item {
                Text(
                    "More tools will appear here only after their processing and export flows are implemented and tested.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    description: String,
    enabled: Boolean,
    action: () -> Unit,
    button: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = PaperMuted)
            Button(onClick = action, enabled = enabled) { Text(button) }
        }
    }
}
