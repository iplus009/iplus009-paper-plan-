package com.paperplan.app.feature.bangla

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.pdf.BanglaExportAuditResult
import com.paperplan.app.core.pdf.BanglaExportStyle
import com.paperplan.app.core.pdf.BanglaPdfExporter
import com.paperplan.app.core.pdf.BlockRole
import com.paperplan.app.core.pdf.CanonicalTextDocument
import com.paperplan.app.core.pdf.DocumentBlock
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.ui.components.GlassWorkspaceCard
import com.paperplan.app.core.ui.components.GpuZoomableBitmapPreview
import com.paperplan.app.core.ui.components.StatusBadgePill
import com.paperplan.app.core.ui.components.WorkspaceDocActionBar
import com.paperplan.app.core.ui.components.formatFileBytes
import com.paperplan.app.core.ui.theme.PaperInfoText
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperMuted
import kotlinx.coroutines.launch

private const val DEFAULT_BANGLA_TITLE = "কাগজের জাহাজ — বাংলা ও ইংরেজি নথি"
private const val DEFAULT_BANGLA_BODY =
    "বাংলা যুক্তাক্ষর যাচাই: বিজ্ঞান, প্রযুক্তি, কৃষ্ণচূড়া, স্বাক্ষর ও রাষ্ট্রভাষা।\n\n" +
        "এই পিডিএফটি অ্যান্ড্রয়েড হারফবাজ ভেক্টর শেপিং ব্যবহার করে তৈরি, তাই প্রতিটি বাংলা শব্দ সিলেক্ট, কপি এবং সার্চ করা যায়।\n\n" +
        "English paragraph support works seamlessly alongside Bengali lines inside Paper Plane by iPlus."

@Composable
fun BanglaStudioScreen(
    onOpenViewer: (WorkspaceDoc) -> Unit,
    onWorkspaceChanged: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var titleText by remember { mutableStateOf(DEFAULT_BANGLA_TITLE) }
    var bodyText by remember { mutableStateOf(DEFAULT_BANGLA_BODY) }
    var selectedStyle by remember { mutableStateOf(BanglaExportStyle.BOOK_STYLE) }
    var exportedDoc by remember { mutableStateOf<WorkspaceDoc?>(null) }
    var auditResult by remember { mutableStateOf<BanglaExportAuditResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Type or edit Unicode Bengali + English text and export a searchable vector PDF.")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "bangla_header") {
            GlassWorkspaceCard(elevated = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = PaperMint,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text("Bangla Vector Studio", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "HarfBuzz conjunctive shaping • zero broken glyphs",
                                style = MaterialTheme.typography.bodySmall,
                                color = PaperMuted
                            )
                        }
                    }
                    StatusBadgePill(label = "Vector PDF", accent = PaperMint)
                }

                Text(statusText, style = MaterialTheme.typography.bodySmall, color = PaperInfoText)
            }
        }

        item(key = "bangla_editor") {
            GlassWorkspaceCard {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Document Title (Bangla / English)") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { bodyText = it },
                    label = { Text("Body Content (Paragraphs)") },
                    minLines = 5,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Export Layout Style", style = MaterialTheme.typography.labelLarge, color = PaperMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BanglaExportStyle.entries.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            enabled = !busy,
                            label = { Text(style.label, maxLines = 1) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val blocks = bodyText
                                .split('\n')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .mapIndexed { idx, line ->
                                    val role = if (idx == 0 && titleText.isBlank()) BlockRole.HEADING else BlockRole.BODY
                                    DocumentBlock(role = role, text = line)
                                }
                            if (titleText.isBlank() && blocks.isEmpty()) {
                                statusText = "Enter a title or body text before exporting."
                                return@Button
                            }
                            scope.launch {
                                busy = true
                                statusText = "Rendering shaped vector PDF & verifying searchable text…"
                                val canonical = CanonicalTextDocument(
                                    title = titleText.trim().ifEmpty { "Bangla Document" },
                                    blocks = blocks.ifEmpty {
                                        listOf(DocumentBlock(BlockRole.BODY, titleText.trim()))
                                    }
                                )
                                runCatching {
                                    BanglaPdfExporter.exportToWorkspaceAndAudit(
                                        context = context,
                                        document = canonical,
                                        style = selectedStyle,
                                        displayName = "PaperPlane-Bangla-${System.currentTimeMillis() % 10000}.pdf"
                                    )
                                }.fold(
                                    onSuccess = { (doc, audit) ->
                                        exportedDoc = doc
                                        auditResult = audit
                                        onWorkspaceChanged()
                                        statusText = if (audit.hasBengaliCharacters) {
                                            "Exported ${audit.pageCount}-page vector Bangla PDF (searchable Unicode verified)."
                                        } else {
                                            "Exported ${audit.pageCount}-page vector PDF (${formatFileBytes(audit.outputBytes)})."
                                        }
                                    },
                                    onFailure = { err ->
                                        statusText = "Bangla export failed: ${err.message ?: "Unknown error"}"
                                    }
                                )
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1.4f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (busy) "Exporting…" else "Export Vector PDF", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            titleText = DEFAULT_BANGLA_TITLE
                            bodyText = DEFAULT_BANGLA_BODY
                            statusText = "Sample Bangla + English content restored."
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .weight(0.8f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Reset", maxLines = 1)
                    }
                }
            }
        }

        val audit = auditResult
        val doc = exportedDoc
        if (audit != null && doc != null) {
            item(key = "bangla_result_${doc.id}") {
                GlassWorkspaceCard(elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PDFTextStripper Audit", style = MaterialTheme.typography.titleMedium)
                        StatusBadgePill(
                            label = if (audit.hasBengaliCharacters) "Bangla Verified" else "Text Verified",
                            accent = PaperMint
                        )
                    }

                    GpuZoomableBitmapPreview(
                        bitmap = audit.previewBitmap,
                        contentDescription = "Rendered vector Bangla PDF first page",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    )

                    Text(
                        text = "Extracted searchable sample: ${audit.extractedText.take(180).replace('\n', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PaperMuted
                    )

                    WorkspaceDocActionBar(
                        doc = doc,
                        onOpenViewer = onOpenViewer,
                        onStatusMessage = { statusText = it }
                    )
                }
            }
        }
    }
}
