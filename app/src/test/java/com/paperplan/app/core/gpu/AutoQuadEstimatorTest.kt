package com.paperplan.app.core.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoQuadEstimatorTest {

    @Test
    fun estimateFromLumaGrid_detectsBrightDocumentSheetOnDarkDesk() {
        val w = 80
        val h = 100
        val luma = FloatArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x in 16 until 64 && y in 12 until 88) 0.88f else 0.14f
        }

        val quad = AutoQuadEstimator.estimateFromLumaGrid(luma, w, h)
        assertTrue(HomographySolver.isConvexValidQuad(quad))

        // Left margin ~ 16/80 = 0.20, Top ~ 12/100 = 0.12, Right ~ 0.79, Bottom ~ 0.87
        assertEquals(0.20f, quad.topLeft.x, 0.06f)
        assertEquals(0.12f, quad.topLeft.y, 0.06f)
        assertEquals(0.79f, quad.bottomRight.x, 0.06f)
        assertEquals(0.87f, quad.bottomRight.y, 0.06f)
    }

    @Test
    fun estimateFromLumaGrid_uniformLowContrastFallsBackToDefaultPreset() {
        val luma = FloatArray(80 * 100) { 0.50f }
        val quad = AutoQuadEstimator.estimateFromLumaGrid(luma, 80, 100)
        assertEquals(QuadCorners.DEFAULT_TILT_PRESET, quad)
    }
}
