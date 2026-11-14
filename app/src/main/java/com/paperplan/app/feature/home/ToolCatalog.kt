package com.paperplan.app.feature.home

import androidx.compose.ui.graphics.Color
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperScanBlue
import com.paperplan.app.core.ui.theme.PaperViolet

enum class HomeToolAction {
    SCAN,
    VIEWER,
    ORGANIZE,
    COMPRESS,
    MERGE,
    ROTATE,
    SPLIT,
    REDACT,
    BANGLA
}

data class HomeActionTool(
    val action: HomeToolAction,
    val title: String,
    val subtitle: String,
    val badge: String,
    val accent: Color
)

val livePaperPlaneTools: List<HomeActionTool> = listOf(
    HomeActionTool(
        action = HomeToolAction.SCAN,
        title = "GPU Camera Scan",
        subtitle = "CameraX + gallery quad warp with 4 OpenGL ES 2.0 filters & multi-page PDF.",
        badge = "GPU Live",
        accent = PaperScanBlue
    ),
    HomeActionTool(
        action = HomeToolAction.VIEWER,
        title = "PDF Viewer",
        subtitle = "Open any PDF with pinch-to-zoom, share, SAF export & quick tool links.",
        badge = "Live",
        accent = PaperViolet
    ),
    HomeActionTool(
        action = HomeToolAction.ORGANIZE,
        title = "Organize Pages",
        subtitle = "Reorder pages, rotate +90° per slot, remove pages & export clean PDF.",
        badge = "Live",
        accent = PaperMint
    ),
    HomeActionTool(
        action = HomeToolAction.COMPRESS,
        title = "Compress PDF",
        subtitle = "Small / Balanced / Print presets with size-reduction verification.",
        badge = "Live",
        accent = PaperScanBlue
    ),
    HomeActionTool(
        action = HomeToolAction.MERGE,
        title = "Merge 2+ PDFs",
        subtitle = "Combine multiple device PDFs seamlessly into your Workspace library.",
        badge = "Live",
        accent = PaperViolet
    ),
    HomeActionTool(
        action = HomeToolAction.ROTATE,
        title = "Rotate PDF +90°",
        subtitle = "Rotate every page clockwise cleanly into a new Workspace PDF.",
        badge = "Live",
        accent = PaperMint
    ),
    HomeActionTool(
        action = HomeToolAction.SPLIT,
        title = "Split to ZIP",
        subtitle = "Extract each page of a PDF into individual PDFs inside a ZIP archive.",
        badge = "Live",
        accent = PaperScanBlue
    ),
    HomeActionTool(
        action = HomeToolAction.REDACT,
        title = "True Pixel-Cut Redact",
        subtitle = "Rasterize & permanently burn selected boxes with zero text leak audit.",
        badge = "Security",
        accent = Color(0xFFEF4444)
    ),
    HomeActionTool(
        action = HomeToolAction.BANGLA,
        title = "Bangla Vector Studio",
        subtitle = "HarfBuzz-shaped Unicode Bengali PDF with 100% searchable vector glyphs.",
        badge = "Vector",
        accent = PaperMint
    )
)
