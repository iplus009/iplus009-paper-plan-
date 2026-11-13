package com.paperplan.app.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.ui.theme.PaperMuted

@Composable
fun HomeScreen() {
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("iPlus  /  DOCUMENT TOOLS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text("Paper Plan", style = MaterialTheme.typography.headlineLarge)
                Text("Make your own PDF better than anyone.", color = PaperMuted)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Start with a document", style = MaterialTheme.typography.titleLarge)
                    Text(selectedName, color = PaperMuted, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                            Text(if (selectedPdf == null) "Choose PDF" else "Change PDF")
                        }
                        OutlinedButton(onClick = {
                            selectedPdf = null
                            selectedName = "No PDF selected"
                        }) { Text("Clear") }
                    }
                }
            }

            Text("Quick tools", style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(paperPlanTools) { tool ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(tool.title, style = MaterialTheme.typography.titleMedium)
                            Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = PaperMuted)
                        }
                    }
                }
            }
            Text("An iPlus app", style = MaterialTheme.typography.labelSmall, color = PaperMuted)
        }
    }
}
