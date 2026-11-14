package com.paperplan.app.core.gpu

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight luminance-contrast heuristic that detects bright paper boundaries against darker desks,
 * falling back cleanly to DEFAULT_TILT_PRESET when contrast is low.
 */
object AutoQuadEstimator {

    fun estimateFromBitmap(bitmap: Bitmap): QuadCorners {
        val sampleW = 80
        val sampleH = 100
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        try {
            val pixels = IntArray(sampleW * sampleH)
            scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
            val luma = FloatArray(pixels.size) { i ->
                val c = pixels[i]
                (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
            }
            return estimateFromLumaGrid(luma, sampleW, sampleH)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    fun estimateFromLumaGrid(luma: FloatArray, width: Int, height: Int): QuadCorners {
        if (width < 8 || height < 8 || luma.size != width * height) {
            return QuadCorners.DEFAULT_TILT_PRESET
        }

        var minL = 1f
        var maxL = 0f
        var sumL = 0.0
        for (v in luma) {
            if (v < minL) minL = v
            if (v > maxL) maxL = v
            sumL += v
        }
        val contrast = maxL - minL
        if (contrast < 0.20f) return QuadCorners.DEFAULT_TILT_PRESET

        val meanL = (sumL / luma.size).toFloat()
        val threshold = max(meanL + contrast * 0.12f, minL + contrast * 0.55f)

        // Track left/right bright extents per row, ignoring isolated noise
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        var brightCount = 0

        var topLeftX = width
        var topRightX = -1
        var botLeftX = width
        var botRightX = -1

        for (y in 2 until height - 2) {
            var rowMin = width
            var rowMax = -1
            var rowHits = 0
            for (x in 2 until width - 2) {
                if (luma[y * width + x] >= threshold) {
                    rowHits++
                    if (x < rowMin) rowMin = x
                    if (x > rowMax) rowMax = x
                }
            }
            if (rowHits >= width / 6) {
                brightCount += rowHits
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                minX = min(minX, rowMin)
                maxX = max(maxX, rowMax)
                if (y <= height / 3) {
                    topLeftX = min(topLeftX, rowMin)
                    topRightX = max(topRightX, rowMax)
                }
                if (y >= (height * 2) / 3) {
                    botLeftX = min(botLeftX, rowMin)
                    botRightX = max(botRightX, rowMax)
                }
            }
        }

        if (brightCount < (width * height) / 10 || maxX <= minX || maxY <= minY) {
            return QuadCorners.DEFAULT_TILT_PRESET
        }

        val spanW = (maxX - minX).toFloat() / width
        val spanH = (maxY - minY).toFloat() / height
        if (spanW < 0.25f || spanH < 0.25f) return QuadCorners.DEFAULT_TILT_PRESET

        val tlX = ((if (topLeftX < width) topLeftX else minX).toFloat() / width).coerceIn(0.02f, 0.90f)
        val trX = ((if (topRightX >= 0) topRightX else maxX).toFloat() / width).coerceIn(0.10f, 0.98f)
        val blX = ((if (botLeftX < width) botLeftX else minX).toFloat() / width).coerceIn(0.02f, 0.90f)
        val brX = ((if (botRightX >= 0) botRightX else maxX).toFloat() / width).coerceIn(0.10f, 0.98f)
        val topY = (minY.toFloat() / height).coerceIn(0.02f, 0.85f)
        val botY = (maxY.toFloat() / height).coerceIn(0.15f, 0.98f)

        val candidate = QuadCorners(
            topLeft = Point2D(tlX, topY),
            topRight = Point2D(trX, topY),
            bottomRight = Point2D(brX, botY),
            bottomLeft = Point2D(blX, botY)
        )
        return if (HomographySolver.isConvexValidQuad(candidate)) {
            candidate
        } else {
            QuadCorners.DEFAULT_TILT_PRESET
        }
    }
}
