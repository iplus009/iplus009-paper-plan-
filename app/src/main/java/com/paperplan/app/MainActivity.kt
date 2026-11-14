package com.paperplan.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.paperplan.app.core.ui.theme.PaperBackground
import com.paperplan.app.core.ui.theme.PaperPlanTheme
import com.paperplan.app.core.ui.theme.PaperSurface
import com.paperplan.app.feature.home.HomeScreen
import com.paperplan.app.feature.lab.GpuBanglaLabScreen
import com.paperplan.app.feature.redact.TrueRedactScreen

private enum class PaperPlanTab(
    val label: String,
    val icon: ImageVector
) {
    TOOLS("Tools", Icons.Outlined.Build),
    REDACT("Redact", Icons.Outlined.Security),
    GPU_BANGLA("GPU & Bangla", Icons.Outlined.DocumentScanner)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            PaperPlanTheme {
                PaperPlanRootApp()
            }
        }
    }
}

@Composable
private fun PaperPlanRootApp() {
    var activeTab by rememberSaveable { mutableStateOf(PaperPlanTab.TOOLS) }

    Scaffold(
        containerColor = PaperBackground,
        bottomBar = {
            NavigationBar(
                containerColor = PaperSurface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                PaperPlanTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = {
                            Icon(imageVector = tab.icon, contentDescription = tab.label)
                        },
                        label = {
                            Text(tab.label, maxLines = 1)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (activeTab) {
            PaperPlanTab.TOOLS -> HomeScreen(
                contentPadding = innerPadding,
                onOpenRedact = { activeTab = PaperPlanTab.REDACT },
                onOpenGpuLab = { activeTab = PaperPlanTab.GPU_BANGLA }
            )
            PaperPlanTab.REDACT -> TrueRedactScreen(contentPadding = innerPadding)
            PaperPlanTab.GPU_BANGLA -> GpuBanglaLabScreen(contentPadding = innerPadding)
        }
    }
}
