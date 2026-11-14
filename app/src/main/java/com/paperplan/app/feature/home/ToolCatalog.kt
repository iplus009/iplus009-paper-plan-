package com.paperplan.app.feature.home

enum class ToolCategory(val label: String) {
    PDF("PDF Core"),
    SECURITY("Privacy & Security"),
    SCAN("GPU Scanner"),
    BANGALI("Bangla Studio"),
    OCR("OCR & Translation")
}

enum class ToolStatus(val badgeText: String) {
    LIVE("Live"),
    SPIKE_LAB("Phase 0 Lab"),
    PLANNED("Planned")
}

data class ToolItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ToolCategory,
    val status: ToolStatus
)

val paperPlanTools = listOf(
    ToolItem(
        id = "merge",
        title = "Merge PDFs",
        subtitle = "Combine 2+ PDFs stream-safely via TempFile storage (no OOM).",
        category = ToolCategory.PDF,
        status = ToolStatus.LIVE
    ),
    ToolItem(
        id = "rotate",
        title = "Rotate PDF",
        subtitle = "Rotate all pages 90° clockwise and save via atomic SAF check.",
        category = ToolCategory.PDF,
        status = ToolStatus.LIVE
    ),
    ToolItem(
        id = "split",
        title = "Split to ZIP",
        subtitle = "Export each page as its own verified PDF inside a ZIP archive.",
        category = ToolCategory.PDF,
        status = ToolStatus.LIVE
    ),
    ToolItem(
        id = "true_redact",
        title = "True Pixel-Cut Redact",
        subtitle = "Destroy underlying text/metadata in masked zones + post-save audit.",
        category = ToolCategory.SECURITY,
        status = ToolStatus.LIVE
    ),
    ToolItem(
        id = "gpu_scan",
        title = "GPU ES 2.0 Scanner",
        subtitle = "Perspective homography warp, shadow clean & adaptive B&W on GPU.",
        category = ToolCategory.SCAN,
        status = ToolStatus.SPIKE_LAB
    ),
    ToolItem(
        id = "bangla_pdf",
        title = "Bangla Vector PDF",
        subtitle = "Native HarfBuzz conjunct shaping: Book Style & Clean Text Style.",
        category = ToolCategory.BANGALI,
        status = ToolStatus.SPIKE_LAB
    ),
    ToolItem(
        id = "ocr_translate",
        title = "Offline Bangla/EN OCR + Translate",
        subtitle = "XY-Cut column sort, Tesseract fast + ML Kit offline (Milestone 2–3).",
        category = ToolCategory.OCR,
        status = ToolStatus.PLANNED
    ),
    ToolItem(
        id = "batch_sign",
        title = "Reorder, Compress & Sign",
        subtitle = "Visual page drag-sort, smart compression & signatures (Milestone 1).",
        category = ToolCategory.PDF,
        status = ToolStatus.PLANNED
    )
)
