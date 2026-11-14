package com.paperplan.app.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionAndBengaliModelTest {

    @Test
    fun normalizedRedactionRect_clampsBoundsAndRejectsDegenerateBoxes() {
        val valid = NormalizedRedactionRect(
            pageIndex = 0,
            leftFraction = -0.2f,
            topFraction = 0.25f,
            rightFraction = 1.4f,
            bottomFraction = 0.60f
        ).clamped()

        assertTrue(valid.isValid())
        assertEquals(0f, valid.leftFraction, 1e-5f)
        assertEquals(1f, valid.rightFraction, 1e-5f)

        val tooTiny = NormalizedRedactionRect(
            pageIndex = 0,
            leftFraction = 0.5f,
            topFraction = 0.5f,
            rightFraction = 0.501f,
            bottomFraction = 0.501f
        )
        assertFalse(tooTiny.isValid())
    }

    @Test
    fun canonicalBanglaSample_preservesConjunctsAndParsesHeadingAndParagraphBlocks() {
        val raw = """
            # আমার সোনার বাংলা
            ক্ষ, জ্ঞ, ত্ত, ন্ত্র, ষ্ণ ও প্রিয় স্বাধীনতা ২০২৬।

            Mixed English & Bengali 100% searchable layout check.
        """.trimIndent()

        val doc = CanonicalTextDocument.fromPlainMarkdown(
            title = "বাংলা পরীক্ষা",
            markdown = raw
        )

        assertEquals(3, doc.blocks.size)
        assertEquals(BlockRole.HEADING, doc.blocks[0].role)
        assertEquals("আমার সোনার বাংলা", doc.blocks[0].text)
        assertEquals(BlockRole.BODY, doc.blocks[1].role)
        assertTrue(doc.blocks[1].text.contains("ক্ষ"))
        assertTrue(doc.blocks[1].text.contains("জ্ঞ"))
        assertTrue(doc.blocks[1].text.contains("ন্ত্রে".dropLast(1))) // ন্ত্র
        assertEquals(BlockRole.BODY, doc.blocks[2].role)
    }
}
