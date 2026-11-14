package com.paperplan.app.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafPdfValidatorTest {

    @Test
    fun validatePdfFile_rejectsEmptyOrTruncatedFilesAndAcceptsValidPdfFrame() {
        val tempDir = Files.createTempDirectory("pp_val_test").toFile()
        try {
            val emptyFile = File(tempDir, "empty.pdf").apply { createNewFile() }
            assertFalse(PdfIntegrityChecker.hasValidPdfHeaderAndEof(emptyFile))

            val fakeText = File(tempDir, "fake.pdf").apply { writeText("hello world") }
            assertFalse(PdfIntegrityChecker.hasValidPdfHeaderAndEof(fakeText))

            val truncatedPdf = File(tempDir, "trunc.pdf").apply {
                writeText("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n")
            }
            assertFalse(PdfIntegrityChecker.hasValidPdfHeaderAndEof(truncatedPdf))

            val minimalValid = File(tempDir, "valid.pdf").apply {
                writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n")
            }
            assertTrue(PdfIntegrityChecker.hasValidPdfHeaderAndEof(minimalValid))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun useTempFile_guaranteesCleanupEvenWhenOperationThrows() {
        val cacheDir = Files.createTempDirectory("pp_cache_test").toFile()
        var leakedFile: File? = null
        try {
            try {
                TempWorkspaceManager.useTempFile(cacheDir, "spike", ".pdf") { file ->
                    leakedFile = file
                    file.writeText("temp-data")
                    assertTrue(file.exists())
                    error("Simulated crash during PDF processing")
                }
            } catch (_: IllegalStateException) {
                // expected
            }
            assertFalse("Temp file must be deleted on error", leakedFile!!.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
