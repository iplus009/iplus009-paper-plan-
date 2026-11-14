package com.paperplan.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.paperplan.app.core.storage.WorkspaceDoc
import com.paperplan.app.core.storage.WorkspaceLibrary
import com.paperplan.app.core.ui.theme.PaperBackground
import com.paperplan.app.core.ui.theme.PaperPlanTheme
import com.paperplan.app.core.ui.theme.PaperSurface
import com.paperplan.app.feature.bangla.BanglaStudioScreen
import com.paperplan.app.feature.compress.CompressPdfScreen
import com.paperplan.app.feature.home.HomeScreen
import com.paperplan.app.feature.organizer.PageOrganizerScreen
import com.paperplan.app.feature.redact.TrueRedactScreen
import com.paperplan.app.feature.scanner.ScannerScreen
import com.paperplan.app.feature.viewer.PdfViewerScreen

private enum class PrimaryTab(
    val label: String,
    val icon: ImageVector
) {
    TOOLS("Tools", Icons.Outlined.Build),
    SCAN("Scan", Icons.Outlined.DocumentScanner),
    REDACT("Redact", Icons.Outlined.Security),
    BANGLA("Bangla", Icons.Outlined.Translate)
}

private sealed interface ToolsSubScreen {
    data class Viewer(val doc: WorkspaceDoc?) : ToolsSubScreen
    data class Organize(val doc: WorkspaceDoc?) : ToolsSubScreen
    data class Compress(val doc: WorkspaceDoc?) : ToolsSubScreen
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
    val context = LocalContext.current
    var activeTab by rememberSaveable { mutableStateOf(PrimaryTab.TOOLS) }
    var subScreen by remember { mutableStateOf<ToolsSubScreen?>(null) }
    var redactSeedDoc by remember { mutableStateOf<WorkspaceDoc?>(null) }
    var workspaceDocs by remember { mutableStateOf(WorkspaceLibrary.listDocs(context)) }

    fun refreshWorkspace() {
        workspaceDocs = WorkspaceLibrary.listDocs(context)
    }

    fun openViewer(doc: WorkspaceDoc?) {
        refreshWorkspace()
        activeTab = PrimaryTab.TOOLS
        subScreen = ToolsSubScreen.Viewer(doc)
    }

    fun openOrganize(doc: WorkspaceDoc?) {
        refreshWorkspace()
        activeTab = PrimaryTab.TOOLS
        subScreen = ToolsSubScreen.Organize(doc)
    }

    fun openCompress(doc: WorkspaceDoc?) {
        refreshWorkspace()
        activeTab = PrimaryTab.TOOLS
        subScreen = ToolsSubScreen.Compress(doc)
    }

    fun openRedact(doc: WorkspaceDoc? = null) {
        refreshWorkspace()
        redactSeedDoc = doc
        subScreen = null
        activeTab = PrimaryTab.REDACT
    }

    BackHandler(enabled = activeTab == PrimaryTab.TOOLS && subScreen != null) {
        subScreen = null
    }

    Scaffold(
        containerColor = PaperBackground,
        bottomBar = {
            NavigationBar(
                containerColor = PaperSurface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                PrimaryTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = {
                            if (tab == PrimaryTab.TOOLS) {
                                subScreen = null
                            }
                            activeTab = tab
                        },
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
            PrimaryTab.TOOLS -> when (val sub = subScreen) {
                is ToolsSubScreen.Viewer -> PdfViewerScreen(
                    initialDoc = sub.doc,
                    workspaceDocs = workspaceDocs,
                    onBack = { subScreen = null },
                    onWorkspaceChanged = ::refreshWorkspace,
                    onOpenOrganize = ::openOrganize,
                    onOpenCompress = ::openCompress,
                    onOpenRedact = ::openRedact,
                    contentPadding = innerPadding
                )
                is ToolsSubScreen.Organize -> PageOrganizerScreen(
                    initialDoc = sub.doc,
                    workspaceDocs = workspaceDocs,
                    onBack = { subScreen = null },
                    onOpenViewer = ::openViewer,
                    onWorkspaceChanged = ::refreshWorkspace,
                    contentPadding = innerPadding
                )
                is ToolsSubScreen.Compress -> CompressPdfScreen(
                    initialDoc = sub.doc,
                    workspaceDocs = workspaceDocs,
                    onBack = { subScreen = null },
                    onOpenViewer = ::openViewer,
                    onWorkspaceChanged = ::refreshWorkspace,
                    contentPadding = innerPadding
                )
                null -> HomeScreen(
                    workspaceDocs = workspaceDocs,
                    onWorkspaceChanged = ::refreshWorkspace,
                    onOpenScan = { activeTab = PrimaryTab.SCAN },
                    onOpenRedact = { openRedact(workspaceDocs.firstOrNull()) },
                    onOpenBangla = { activeTab = PrimaryTab.BANGLA },
                    onOpenViewer = ::openViewer,
                    onOpenOrganize = ::openOrganize,
                    onOpenCompress = ::openCompress,
                    contentPadding = innerPadding
                )
            }

            PrimaryTab.SCAN -> ScannerScreen(
                onOpenViewer = ::openViewer,
                onWorkspaceChanged = ::refreshWorkspace,
                contentPadding = innerPadding
            )

            PrimaryTab.REDACT -> TrueRedactScreen(
                initialDoc = redactSeedDoc,
                workspaceDocs = workspaceDocs,
                onOpenViewer = ::openViewer,
                onWorkspaceChanged = ::refreshWorkspace,
                contentPadding = innerPadding
            )

            PrimaryTab.BANGLA -> BanglaStudioScreen(
                onOpenViewer = ::openViewer,
                onWorkspaceChanged = ::refreshWorkspace,
                contentPadding = innerPadding
            )
        }
    }
}
