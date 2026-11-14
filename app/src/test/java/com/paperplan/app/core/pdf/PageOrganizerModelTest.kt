package com.paperplan.app.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PageOrganizerModelTest {

    @Test
    fun reorderPages_movesSelectedPageCleanlyWithinBounds() {
        val initial = PageOrganizerState.initial(pageCount = 4)
        assertEquals(listOf(0, 1, 2, 3), initial.ops.map { it.sourcePageIndex })

        val moved = initial.movePage(fromIndex = 2, toIndex = 0)
        assertEquals(listOf(2, 0, 1, 3), moved.ops.map { it.sourcePageIndex })

        val movedEnd = moved.movePage(fromIndex = 1, toIndex = 3)
        assertEquals(listOf(2, 1, 3, 0), movedEnd.ops.map { it.sourcePageIndex })
    }

    @Test
    fun rotateAndDelete_normalizesRotationAndProtectsLastPage() {
        val state = PageOrganizerState.initial(pageCount = 2)
            .rotatePage(slotIndex = 0, deltaCw = 90)
            .rotatePage(slotIndex = 0, deltaCw = 270)
            .rotatePage(slotIndex = 1, deltaCw = 180)

        assertEquals(0, state.ops[0].extraRotationCw)
        assertEquals(180, state.ops[1].extraRotationCw)

        val afterDelete = state.deletePage(slotIndex = 0)
        assertEquals(1, afterDelete.ops.size)
        assertEquals(1, afterDelete.ops[0].sourcePageIndex)

        // Never delete the final remaining page
        val blockedLastDelete = afterDelete.deletePage(slotIndex = 0)
        assertEquals(1, blockedLastDelete.ops.size)
    }
}
