package com.paperplan.app.core.pdf

import kotlin.math.roundToInt

data class PagePlanOp(
    val sourcePageIndex: Int,
    val extraRotationCw: Int = 0
)

data class PageOrganizerState(
    val ops: List<PagePlanOp>
) {
    fun movePage(fromIndex: Int, toIndex: Int): PageOrganizerState {
        if (fromIndex !in ops.indices || toIndex !in ops.indices || fromIndex == toIndex) return this
        val mutable = ops.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return PageOrganizerState(mutable.toList())
    }

    fun rotatePage(slotIndex: Int, deltaCw: Int = 90): PageOrganizerState {
        if (slotIndex !in ops.indices) return this
        val updated = ops.mapIndexed { idx, op ->
            if (idx == slotIndex) {
                val nextRot = ((op.extraRotationCw + deltaCw) % 360 + 360) % 360
                op.copy(extraRotationCw = nextRot)
            } else {
                op
            }
        }
        return PageOrganizerState(updated)
    }

    fun deletePage(slotIndex: Int): PageOrganizerState {
        if (ops.size <= 1 || slotIndex !in ops.indices) return this
        return PageOrganizerState(ops.filterIndexed { idx, _ -> idx != slotIndex })
    }

    companion object {
        fun initial(pageCount: Int): PageOrganizerState {
            require(pageCount > 0) { "Page count must be positive." }
            return PageOrganizerState((0 until pageCount).map { PagePlanOp(sourcePageIndex = it) })
        }
    }
}

enum class CompressionPreset(
    val label: String,
    val subtitle: String,
    val maxImageDimension: Int,
    val jpegQuality: Float
) {
    SMALL("Small / Email", "Smallest file size (~900px, 55% JPEG)", 900, 0.55f),
    BALANCED("Balanced", "Good screen clarity (~1400px, 72% JPEG)", 1400, 0.72f),
    PRINT("Print Clear", "Sharp detail (~2000px, 86% JPEG)", 2000, 0.86f)
}

data class CompressSummary(
    val originalBytes: Long,
    val compressedBytes: Long,
    val pageCount: Int,
    val savedPercent: Int,
    val usedOriginalFallback: Boolean
) {
    companion object {
        fun fromSizes(
            originalBytes: Long,
            compressedBytes: Long,
            pageCount: Int,
            usedOriginalFallback: Boolean
        ): CompressSummary {
            val saved = if (originalBytes > 0L && compressedBytes < originalBytes) {
                (((originalBytes - compressedBytes).toFloat() / originalBytes.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(0, 99)
            } else {
                0
            }
            return CompressSummary(
                originalBytes = originalBytes,
                compressedBytes = compressedBytes,
                pageCount = pageCount,
                savedPercent = saved,
                usedOriginalFallback = usedOriginalFallback
            )
        }
    }
}
