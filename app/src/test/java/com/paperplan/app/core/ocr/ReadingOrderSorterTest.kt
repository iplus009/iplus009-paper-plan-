package com.paperplan.app.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderSorterTest {

    @Test
    fun twoColumnPageWithTitleHeader_readsHeaderThenLeftColumnThenRightColumn() {
        val blocks = listOf(
            TextRegion("right-1", left = 0.56f, top = 0.25f, right = 0.94f, bottom = 0.30f),
            TextRegion("left-1", left = 0.06f, top = 0.25f, right = 0.44f, bottom = 0.30f),
            TextRegion("title", left = 0.10f, top = 0.05f, right = 0.90f, bottom = 0.12f),
            TextRegion("right-2", left = 0.56f, top = 0.35f, right = 0.94f, bottom = 0.40f),
            TextRegion("left-2", left = 0.06f, top = 0.35f, right = 0.44f, bottom = 0.40f)
        )

        val ordered = ReadingOrderSorter.sortByXyCut(blocks).map { it.id }

        assertEquals(
            listOf("title", "left-1", "left-2", "right-1", "right-2"),
            ordered
        )
    }

    @Test
    fun singleColumnMixedOrder_sortsTopToBottomLeftToRightWithinLine() {
        val blocks = listOf(
            TextRegion("w2", left = 0.32f, top = 0.10f, right = 0.50f, bottom = 0.14f),
            TextRegion("w3", left = 0.10f, top = 0.22f, right = 0.60f, bottom = 0.26f),
            TextRegion("w1", left = 0.10f, top = 0.105f, right = 0.28f, bottom = 0.145f)
        )

        val ordered = ReadingOrderSorter.sortByXyCut(blocks).map { it.id }

        assertEquals(listOf("w1", "w2", "w3"), ordered)
    }

    @Test
    fun rotatedPageCoordinateMapper_maps0And90And180And270Correctly() {
        val p0 = OcrCoordinateMapper.imagePointToPdf(
            normX = 0.25f,
            normY = 0.25f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 100f,
            cropHeight = 200f,
            rotationDegrees = 0
        )
        assertEquals(25f, p0.first, 0.01f)
        assertEquals(150f, p0.second, 0.01f)

        val p90 = OcrCoordinateMapper.imagePointToPdf(
            normX = 0.0f,
            normY = 0.0f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 100f,
            cropHeight = 200f,
            rotationDegrees = 90
        )
        assertEquals(0f, p90.first, 0.01f)
        assertEquals(0f, p90.second, 0.01f)

        val p180 = OcrCoordinateMapper.imagePointToPdf(
            normX = 0.25f,
            normY = 0.25f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 100f,
            cropHeight = 200f,
            rotationDegrees = 180
        )
        assertEquals(75f, p180.first, 0.01f)
        assertEquals(50f, p180.second, 0.01f)

        val p270 = OcrCoordinateMapper.imagePointToPdf(
            normX = 0.0f,
            normY = 0.0f,
            cropLeft = 0f,
            cropBottom = 0f,
            cropWidth = 100f,
            cropHeight = 200f,
            rotationDegrees = 270
        )
        assertEquals(100f, p270.first, 0.01f)
        assertEquals(200f, p270.second, 0.01f)
    }
}
