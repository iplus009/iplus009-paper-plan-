package com.paperplan.app.core.ocr

data class TextRegion(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Recursive XY-Cut reading-order sorter and rotation-aware PDF coordinate mapper.
 * Prevents multi-column book/newspaper lines from interleaving across column gutters.
 */
object ReadingOrderSorter {

    fun sortByXyCut(regions: List<TextRegion>): List<TextRegion> {
        if (regions.size <= 1) return regions
        return recursiveSplit(regions)
    }

    private fun recursiveSplit(regions: List<TextRegion>): List<TextRegion> {
        if (regions.size <= 1) return regions

        val minY = regions.minOf { it.top }
        val maxY = regions.maxOf { it.bottom }
        val minX = regions.minOf { it.left }
        val maxX = regions.maxOf { it.right }

        // First check horizontal whitespace bands (full-width headers/paragraphs above columns)
        val yCut = findLargestGap(
            intervals = regions.map { it.top to it.bottom },
            domainStart = minY,
            domainEnd = maxY,
            minGap = 0.015f
        )
        if (yCut != null) {
            val above = regions.filter { it.bottom <= yCut }
            val below = regions.filter { it.top >= yCut }
            if (above.isNotEmpty() && below.isNotEmpty()) {
                return recursiveSplit(above) + recursiveSplit(below)
            }
        }

        // Next check vertical column gutters within the current horizontal band
        val xCut = findLargestGap(
            intervals = regions.map { it.left to it.right },
            domainStart = minX,
            domainEnd = maxX,
            minGap = 0.03f
        )
        if (xCut != null) {
            val leftCol = regions.filter { it.right <= xCut }
            val rightCol = regions.filter { it.left >= xCut }
            if (leftCol.isNotEmpty() && rightCol.isNotEmpty()) {
                return recursiveSplit(leftCol) + recursiveSplit(rightCol)
            }
        }

        // Base case: same column/band -> sort line-by-line top-to-bottom, then left-to-right
        val medianHeight = regions
            .map { (it.bottom - it.top).coerceAtLeast(0.005f) }
            .sorted()[regions.size / 2]

        return regions.sortedWith { a, b ->
            val aMidY = (a.top + a.bottom) * 0.5f
            val bMidY = (b.top + b.bottom) * 0.5f
            if (kotlin.math.abs(aMidY - bMidY) <= medianHeight * 0.6f) {
                a.left.compareTo(b.left)
            } else {
                a.top.compareTo(b.top)
            }
        }
    }

    private fun findLargestGap(
        intervals: List<Pair<Float, Float>>,
        domainStart: Float,
        domainEnd: Float,
        minGap: Float
    ): Float? {
        if (intervals.size < 2 || domainEnd <= domainStart) return null
        val sorted = intervals.sortedBy { it.first }
        var curEnd = sorted[0].second
        var bestGap = minGap
        var bestMid: Float? = null

        for (i in 1 until sorted.size) {
            val (start, end) = sorted[i]
            val gap = start - curEnd
            if (gap > bestGap) {
                bestGap = gap
                bestMid = curEnd + gap * 0.5f
            }
            if (end > curEnd) curEnd = end
        }
        return bestMid
    }
}

/**
 * Converts normalized visually-upright image coordinates [0..1]^2 (origin at top-left)
 * into unrotated PDF CropBox user-space points, honoring PDF /Rotate (0, 90, 180, 270 CW).
 */
object OcrCoordinateMapper {
    fun imagePointToPdf(
        normX: Float,
        normY: Float,
        cropLeft: Float,
        cropBottom: Float,
        cropWidth: Float,
        cropHeight: Float,
        rotationDegrees: Int
    ): Pair<Float, Float> {
        val u = normX.coerceIn(0f, 1f)
        val v = normY.coerceIn(0f, 1f)
        val rot = ((rotationDegrees % 360) + 360) % 360

        return when (rot) {
            90 -> Pair(
                cropLeft + v * cropWidth,
                cropBottom + u * cropHeight
            )
            180 -> Pair(
                cropLeft + (1f - u) * cropWidth,
                cropBottom + v * cropHeight
            )
            270 -> Pair(
                cropLeft + (1f - v) * cropWidth,
                cropBottom + (1f - u) * cropHeight
            )
            else -> Pair(
                cropLeft + u * cropWidth,
                cropBottom + (1f - v) * cropHeight
            )
        }
    }
}
