package com.paperplan.app.feature.home

data class ToolItem(
    val title: String,
    val subtitle: String,
    val category: ToolCategory
)

enum class ToolCategory {
    PDF, SCAN, OCR, TRANSLATION, DESIGN, CONVERT, FILES
}

val paperPlanTools = listOf(
    ToolItem("Merge PDF", "Combine documents", ToolCategory.PDF),
    ToolItem("Split PDF", "Separate pages", ToolCategory.PDF),
    ToolItem("Compress PDF", "Reduce file size", ToolCategory.PDF),
    ToolItem("Organize Pages", "Move, rotate and remove", ToolCategory.PDF),
    ToolItem("Camera Scan", "Capture one or many pages", ToolCategory.SCAN),
    ToolItem("OCR Studio", "Recognize document text", ToolCategory.OCR),
    ToolItem("Translate PDF", "Online document translation", ToolCategory.TRANSLATION),
    ToolItem("PDF Designer", "Create a document layout", ToolCategory.DESIGN),
    ToolItem("Convert", "PDF, image and Office formats", ToolCategory.CONVERT),
    ToolItem("Protect PDF", "Manage document security", ToolCategory.PDF),
    ToolItem("Compare PDF", "Review document differences", ToolCategory.PDF),
    ToolItem("File Library", "Browse recent documents", ToolCategory.FILES)
)
