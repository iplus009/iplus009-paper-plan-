package com.paperplan.app.core.gpu

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class Point2D(val x: Float, val y: Float)

data class QuadCorners(
    val topLeft: Point2D,
    val topRight: Point2D,
    val bottomRight: Point2D,
    val bottomLeft: Point2D
) {
    fun asList(): List<Point2D> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        val FULL_FRAME = QuadCorners(
            topLeft = Point2D(0f, 0f),
            topRight = Point2D(1f, 0f),
            bottomRight = Point2D(1f, 1f),
            bottomLeft = Point2D(0f, 1f)
        )

        val DEFAULT_TILT_PRESET = QuadCorners(
            topLeft = Point2D(0.08f, 0.08f),
            topRight = Point2D(0.92f, 0.08f),
            bottomRight = Point2D(0.95f, 0.94f),
            bottomLeft = Point2D(0.05f, 0.94f)
        )
    }
}

/**
 * Pure Kotlin 3x3 projective homography solver producing a column-major matrix for GLSL mat3.
 * Maps destination normalized UV [0..1]^2 -> source normalized texture UV.
 */
object HomographySolver {

    fun isConvexValidQuad(corners: QuadCorners): Boolean {
        val pts = corners.asList()
        if (pts.any { it.x !in -0.25f..1.25f || it.y !in -0.25f..1.25f }) return false

        var sign = 0
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-4f) return false
            val currentSign = if (cross > 0f) 1 else -1
            if (sign == 0) sign = currentSign else if (sign != currentSign) return false
        }
        return true
    }

    /**
     * Solves for column-major 3x3 inverse homography H mapping destination (u,v) -> source (s,t).
     */
    fun solveInverseHomography(corners: QuadCorners): FloatArray {
        require(isConvexValidQuad(corners)) {
            "Quad corners must form a non-degenerate convex quadrilateral."
        }

        val dst = arrayOf(
            Point2D(0f, 0f),
            Point2D(1f, 0f),
            Point2D(1f, 1f),
            Point2D(0f, 1f)
        )
        val src = corners.asList()

        // Solve A * h = b where h = [h0, h1, h2, h3, h4, h5, h6, h7]^T and h8 = 1
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val u = dst[i].x.toDouble()
            val v = dst[i].y.toDouble()
            val x = src[i].x.toDouble()
            val y = src[i].y.toDouble()

            val rowX = i * 2
            a[rowX][0] = u
            a[rowX][1] = v
            a[rowX][2] = 1.0
            a[rowX][6] = -x * u
            a[rowX][7] = -x * v
            a[rowX][8] = x

            val rowY = i * 2 + 1
            a[rowY][3] = u
            a[rowY][4] = v
            a[rowY][5] = 1.0
            a[rowY][6] = -y * u
            a[rowY][7] = -y * v
            a[rowY][8] = y
        }

        val h = solveLinearSystem8(a)
        // Column-major order for OpenGL mat3(uInvH): col0=(h0,h3,h6), col1=(h1,h4,h7), col2=(h2,h5,1)
        return floatArrayOf(
            h[0].toFloat(), h[3].toFloat(), h[6].toFloat(),
            h[1].toFloat(), h[4].toFloat(), h[7].toFloat(),
            h[2].toFloat(), h[5].toFloat(), 1.0f
        )
    }

    fun transformPoint(colMajorMat3: FloatArray, u: Float, v: Float): Pair<Float, Float> {
        require(colMajorMat3.size == 9)
        val s = colMajorMat3[0] * u + colMajorMat3[3] * v + colMajorMat3[6]
        val t = colMajorMat3[1] * u + colMajorMat3[4] * v + colMajorMat3[7]
        val w = colMajorMat3[2] * u + colMajorMat3[5] * v + colMajorMat3[8]
        require(abs(w) > 1e-6f) { "Singular projective denominator w=$w" }
        return Pair(s / w, t / w)
    }

    fun computeTargetDimensions(
        srcWidth: Int,
        srcHeight: Int,
        corners: QuadCorners,
        maxDimension: Int = 2048
    ): Pair<Int, Int> {
        require(srcWidth > 0 && srcHeight > 0)
        val topW = hypot(
            (corners.topRight.x - corners.topLeft.x) * srcWidth,
            (corners.topRight.y - corners.topLeft.y) * srcHeight
        )
        val bottomW = hypot(
            (corners.bottomRight.x - corners.bottomLeft.x) * srcWidth,
            (corners.bottomRight.y - corners.bottomLeft.y) * srcHeight
        )
        val leftH = hypot(
            (corners.bottomLeft.x - corners.topLeft.x) * srcWidth,
            (corners.bottomLeft.y - corners.topLeft.y) * srcHeight
        )
        val rightH = hypot(
            (corners.bottomRight.x - corners.topRight.x) * srcWidth,
            (corners.bottomRight.y - corners.topRight.y) * srcHeight
        )

        val avgW = max(64f, (topW + bottomW) * 0.5f)
        val avgH = max(64f, (leftH + rightH) * 0.5f)
        val scale = minOf(1f, maxDimension / max(avgW, avgH))
        val targetW = (avgW * scale).roundToInt().coerceAtLeast(64)
        val targetH = (avgH * scale).roundToInt().coerceAtLeast(64)
        return Pair(targetW, targetH)
    }

    private fun solveLinearSystem8(m: Array<DoubleArray>): DoubleArray {
        val n = 8
        for (col in 0 until n) {
            var pivot = col
            var maxVal = abs(m[col][col])
            for (r in col + 1 until n) {
                val candidate = abs(m[r][col])
                if (candidate > maxVal) {
                    maxVal = candidate
                    pivot = r
                }
            }
            require(maxVal > 1e-9) { "Ill-conditioned or degenerate quadrilateral." }
            if (pivot != col) {
                val tmp = m[col]
                m[col] = m[pivot]
                m[pivot] = tmp
            }

            val diag = m[col][col]
            for (c in col..n) m[col][c] /= diag

            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r][col]
                if (abs(factor) < 1e-12) continue
                for (c in col..n) {
                    m[r][c] -= factor * m[col][c]
                }
            }
        }
        return DoubleArray(n) { i -> m[i][n] }
    }
}
