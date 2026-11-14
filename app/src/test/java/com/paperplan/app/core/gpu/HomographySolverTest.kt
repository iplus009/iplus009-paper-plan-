package com.paperplan.app.core.gpu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HomographySolverTest {

    @Test
    fun identityQuad_mapsUnitSquareExactly() {
        val corners = QuadCorners.FULL_FRAME
        assertTrue(HomographySolver.isConvexValidQuad(corners))

        val invH = HomographySolver.solveInverseHomography(corners)
        assertEquals(9, invH.size)

        val (x0, y0) = HomographySolver.transformPoint(invH, 0f, 0f)
        val (x1, y1) = HomographySolver.transformPoint(invH, 1f, 0f)
        val (x2, y2) = HomographySolver.transformPoint(invH, 1f, 1f)
        val (x3, y3) = HomographySolver.transformPoint(invH, 0f, 1f)
        val (cx, cy) = HomographySolver.transformPoint(invH, 0.5f, 0.5f)

        assertEquals(0f, x0, 1e-4f)
        assertEquals(0f, y0, 1e-4f)
        assertEquals(1f, x1, 1e-4f)
        assertEquals(0f, y1, 1e-4f)
        assertEquals(1f, x2, 1e-4f)
        assertEquals(1f, y2, 1e-4f)
        assertEquals(0f, x3, 1e-4f)
        assertEquals(1f, y3, 1e-4f)
        assertEquals(0.5f, cx, 1e-4f)
        assertEquals(0.5f, cy, 1e-4f)
    }

    @Test
    fun perspectiveTrapezoid_mapsAllFourCornersWithinEpsilon() {
        val trapezoid = QuadCorners(
            topLeft = Point2D(0.18f, 0.12f),
            topRight = Point2D(0.84f, 0.16f),
            bottomRight = Point2D(0.92f, 0.90f),
            bottomLeft = Point2D(0.08f, 0.86f)
        )
        assertTrue(HomographySolver.isConvexValidQuad(trapezoid))

        val invH = HomographySolver.solveInverseHomography(trapezoid)

        val (tlX, tlY) = HomographySolver.transformPoint(invH, 0f, 0f)
        val (trX, trY) = HomographySolver.transformPoint(invH, 1f, 0f)
        val (brX, brY) = HomographySolver.transformPoint(invH, 1f, 1f)
        val (blX, blY) = HomographySolver.transformPoint(invH, 0f, 1f)

        assertEquals(trapezoid.topLeft.x, tlX, 1e-4f)
        assertEquals(trapezoid.topLeft.y, tlY, 1e-4f)
        assertEquals(trapezoid.topRight.x, trX, 1e-4f)
        assertEquals(trapezoid.topRight.y, trY, 1e-4f)
        assertEquals(trapezoid.bottomRight.x, brX, 1e-4f)
        assertEquals(trapezoid.bottomRight.y, brY, 1e-4f)
        assertEquals(trapezoid.bottomLeft.x, blX, 1e-4f)
        assertEquals(trapezoid.bottomLeft.y, blY, 1e-4f)

        val (midX, midY) = HomographySolver.transformPoint(invH, 0.5f, 0.5f)
        assertTrue("Expected center x in (0.3, 0.7), got $midX", midX in 0.3f..0.7f)
        assertTrue("Expected center y in (0.3, 0.7), got $midY", midY in 0.3f..0.7f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun collinearDegenerateQuad_isRejectedCleanly() {
        val degenerate = QuadCorners(
            topLeft = Point2D(0.1f, 0.1f),
            topRight = Point2D(0.5f, 0.5f),
            bottomRight = Point2D(0.9f, 0.9f),
            bottomLeft = Point2D(0.2f, 0.2f)
        )
        assertFalse(HomographySolver.isConvexValidQuad(degenerate))
        HomographySolver.solveInverseHomography(degenerate)
    }

    @Test
    fun nonConvexSelfIntersectingQuad_isRejectedCleanly() {
        val bowtie = QuadCorners(
            topLeft = Point2D(0.1f, 0.1f),
            topRight = Point2D(0.9f, 0.9f),
            bottomRight = Point2D(0.9f, 0.1f),
            bottomLeft = Point2D(0.1f, 0.9f)
        )
        assertFalse(HomographySolver.isConvexValidQuad(bowtie))
    }

    @Test
    fun targetDimensions_preservesAspectAndCapsMaxDimension() {
        val portraitBook = QuadCorners(
            topLeft = Point2D(0.2f, 0.1f),
            topRight = Point2D(0.8f, 0.1f),
            bottomRight = Point2D(0.8f, 0.9f),
            bottomLeft = Point2D(0.2f, 0.9f)
        )
        val (w, h) = HomographySolver.computeTargetDimensions(
            srcWidth = 3000,
            srcHeight = 4000,
            corners = portraitBook,
            maxDimension = 2048
        )
        assertEquals(2048, h)
        // 0.6 * 3000 = 1800, 0.8 * 4000 = 3200 => ratio = 1800/3200 = 0.5625 => w ~= 1152
        assertTrue("Expected aspect ratio preserved near 1152, got $w", abs(w - 1152) <= 2)
    }
}
