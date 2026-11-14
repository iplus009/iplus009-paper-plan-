package com.paperplan.app.core.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Isolated temporary workspace manager that cleans up scratch files in finally blocks.
 */
object TempWorkspaceManager {
    private const val WORKSPACE_DIR_NAME = "paperplan_work"
    private const val STALE_THRESHOLD_MS = 6L * 60L * 60L * 1000L

    fun ensureWorkspaceRoot(cacheDir: File): File {
        return File(cacheDir, WORKSPACE_DIR_NAME).apply { mkdirs() }
    }

    inline fun <T> useTempFile(
        cacheDir: File,
        prefix: String,
        suffix: String,
        block: (File) -> T
    ): T {
        val root = ensureWorkspaceRoot(cacheDir)
        val tempFile = File(root, "${prefix}_${UUID.randomUUID()}$suffix")
        return try {
            block(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    inline fun <T> useTempDir(
        cacheDir: File,
        prefix: String,
        block: (File) -> T
    ): T {
        val root = ensureWorkspaceRoot(cacheDir)
        val tempDir = File(root, "${prefix}_${UUID.randomUUID()}").apply { mkdirs() }
        return try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun pruneStaleWorkspaces(cacheDir: File, nowMs: Long = System.currentTimeMillis()): Int {
        val root = File(cacheDir, WORKSPACE_DIR_NAME)
        if (!root.exists()) return 0
        var removed = 0
        root.listFiles()?.forEach { entry ->
            if (nowMs - entry.lastModified() > STALE_THRESHOLD_MS) {
                if (entry.deleteRecursively()) removed++
            }
        }
        return removed
    }
}

/**
 * Verifies generated PDF artifacts have both a valid header and trailer before committing to SAF.
 */
object PdfIntegrityChecker {
    fun hasValidPdfHeaderAndEof(file: File): Boolean {
        if (!file.exists() || file.length() < 32L) return false
        val header = ByteArray(8)
        FileInputStream(file).use { input ->
            if (input.read(header) < 5) return false
        }
        if (!String(header, Charsets.US_ASCII).startsWith("%PDF-")) return false

        val tailSize = minOf(1024L, file.length()).toInt()
        val tail = ByteArray(tailSize)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(file.length() - tailSize)
            raf.readFully(tail)
        }
        return String(tail, Charsets.ISO_8859_1).contains("%%EOF")
    }
}

/**
 * Safely writes completed temp artifacts to user-selected SAF destinations using write-truncate ("wt").
 */
object SafSafeWriter {

    fun copyUriToTempFile(context: Context, sourceUri: Uri, targetFile: File): Long {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            targetFile.outputStream().use { output ->
                val copied = input.copyTo(output)
                output.flush()
                return copied
            }
        } ?: error("Could not read selected file.")
    }

    fun writeTempPdfToUri(
        context: Context,
        stagedPdf: File,
        destUri: Uri,
        sourceUri: Uri? = null
    ): Long {
        require(sourceUri == null || sourceUri != destUri) {
            "Refusing to overwrite the source document directly; pick a separate output file."
        }
        require(PdfIntegrityChecker.hasValidPdfHeaderAndEof(stagedPdf)) {
            "Generated PDF failed integrity verification before save."
        }
        return writeFileToUri(context, stagedPdf, destUri)
    }

    fun writeFileToUri(context: Context, stagedFile: File, destUri: Uri): Long {
        val stream = openTruncatingOutputStream(context, destUri)
        stream.use { output ->
            stagedFile.inputStream().use { input ->
                val bytes = input.copyTo(output)
                output.flush()
                return bytes
            }
        }
    }

    private fun openTruncatingOutputStream(context: Context, uri: Uri) =
        try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            context.contentResolver.openOutputStream(uri, "w")
        } catch (_: UnsupportedOperationException) {
            context.contentResolver.openOutputStream(uri, "w")
        } ?: error("Could not open destination output stream.")
}
