package com.paperplan.app.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressMathTest {

    @Test
    fun compressionPresets_haveDistinctScaleAndQualityBounds() {
        assertTrue(CompressionPreset.SMALL.maxImageDimension < CompressionPreset.BALANCED.maxImageDimension)
        assertTrue(CompressionPreset.BALANCED.maxImageDimension < CompressionPreset.PRINT.maxImageDimension)
        assertTrue(CompressionPreset.SMALL.jpegQuality < CompressionPreset.PRINT.jpegQuality)
    }

    @Test
    fun summarizeCompression_detectsSavingsVsInflationCleanly() {
        val reduced = CompressSummary.fromSizes(
            originalBytes = 2_000_000L,
            compressedBytes = 500_000L,
            pageCount = 4,
            usedOriginalFallback = false
        )
        assertEquals(75, reduced.savedPercent)
        assertFalse(reduced.usedOriginalFallback)

        val keptOriginal = CompressSummary.fromSizes(
            originalBytes = 40_000L,
            compressedBytes = 40_000L,
            pageCount = 1,
            usedOriginalFallback = true
        )
        assertEquals(0, keptOriginal.savedPercent)
        assertTrue(keptOriginal.usedOriginalFallback)
    }
}
