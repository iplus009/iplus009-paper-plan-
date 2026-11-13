package com.paperplan.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperPlanTheme {
                PaperPlanHome()
            }
        }
    }
}

@Composable
private fun PaperPlanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB8A5FF),
            secondary = Color(0xFF82D9C8),
            background = Color(0xFF101116),
            surface = Color(0xFF1B1D25)
        ),
        content = content
    )
}

private data class ToolItem(val title: String, val subtitle: String)

@Composable
private fun PaperPlanHome() {
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("No PDF selected") }
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedPdf = uri
            selectedName = uri.lastPathSegment ?: "PDF selected"
        }
    }

    val tools = listOf(
        ToolItem("Merge PDF", "Combine documents"),
        ToolItem("Split PDF", "Separate pages"),
        ToolItem("Compress", "Reduce file size"),
        ToolItem("Camera Scan", "Scan pages to PDF"),
        ToolItem("OCR Studio", "Make scans searchable"),
        ToolItem("Translate PDF", "Online translation"),
        ToolItem("PDF Designer", "Design from scratch"),
        ToolItem("Convert", "PDF and office files"),
        ToolItem("Organize Pages", "Move, rotate and delete"),
        ToolItem("Protect PDF", "Password and security"),
        ToolItem("Compare PDF", "Find document changes"),
        ToolItem("More Tools", "Explore the toolkit")
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PAPER PLAN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Make your own PDF\nbetter than anyone.", style = MaterialTheme.typography.headlineMedium)
                Text("Your all-in-one PDF workspace", color = Color(0xFFAAAAB5))
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Start with a document", style = MaterialTheme.typography.titleLarge)
                    Text(selectedName, color = Color(0xFFB7B8C2), maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                            Text(if (selectedPdf == null) "Choose PDF" else "Change PDF")
                        }
                        OutlinedButton(onClick = { selectedPdf = null; selectedName = "No PDF selected" }) {
                            Text("Clear")
                        }
                    }
                }
            }

            Text("Tools", style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tools) { tool ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(tool.title, style = MaterialTheme.typography.titleMedium)
                            Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAB5))
                        }
                    }
                }
            }
            Text("Early development build • Core tools are being integrated", style = MaterialTheme.typography.bodySmall, color = Color(0xFF888995))
        }
    }
}
