package com.paperplan.app.core.storage

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class WorkspaceDoc(
    val id: String,
    val displayName: String,
    val fileName: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val createdAtMs: Long,
    val tag: String
)

/**
 * Manages persistent PDF documents stored inside app-private filesDir/documents/
 * so users can immediately inspect, organize, compress, redact, or share outputs
 * without losing track of files inside SAF.
 */
object WorkspaceLibrary {

    private const val PREFS_NAME = "paperplane_workspace_v1"
    private const val KEY_DOCS_JSON = "docs_json"
    private const val DOCS_FOLDER = "documents"
    private const val MAX_RECENT_DOCS = 25

    fun docsDir(context: Context): File =
        File(context.filesDir, DOCS_FOLDER).apply { mkdirs() }

    fun getDocFile(context: Context, doc: WorkspaceDoc): File =
        File(docsDir(context), doc.fileName)

    fun getContentUri(context: Context, doc: WorkspaceDoc): Uri {
        val file = getDocFile(context, doc)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun listDocs(context: Context): List<WorkspaceDoc> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DOCS_JSON, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val dir = docsDir(context)
        val items = mutableListOf<WorkspaceDoc>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val fileName = obj.optString("fileName")
            val file = File(dir, fileName)
            if (fileName.isNotBlank() && file.exists() && file.length() > 0L) {
                items += WorkspaceDoc(
                    id = obj.optString("id"),
                    displayName = obj.optString("displayName"),
                    fileName = fileName,
                    pageCount = obj.optInt("pageCount", 1),
                    sizeBytes = file.length(),
                    createdAtMs = obj.optLong("createdAtMs", file.lastModified()),
                    tag = obj.optString("tag", "PDF")
                )
            }
        }
        return items.sortedByDescending { it.createdAtMs }
    }

    suspend fun saveStagedPdf(
        context: Context,
        stagedPdf: File,
        displayName: String,
        tag: String,
        pageCount: Int? = null
    ): WorkspaceDoc = withContext(Dispatchers.IO) {
        require(PdfIntegrityChecker.hasValidPdfHeaderAndEof(stagedPdf)) {
            "Cannot save invalid PDF to workspace."
        }
        val id = UUID.randomUUID().toString().take(10)
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Document.pdf" }
        val finalName = if (safeName.endsWith(".pdf", ignoreCase = true)) safeName else "$safeName.pdf"
        val fileName = "${id}_$finalName"
        val target = File(docsDir(context), fileName)
        stagedPdf.copyTo(target, overwrite = true)

        val resolvedPages = pageCount ?: countPdfPages(target)
        val doc = WorkspaceDoc(
            id = id,
            displayName = displayName.ifBlank { finalName },
            fileName = fileName,
            pageCount = resolvedPages,
            sizeBytes = target.length(),
            createdAtMs = System.currentTimeMillis(),
            tag = tag
        )
        upsertDoc(context, doc)
        doc
    }

    suspend fun importFromUri(
        context: Context,
        sourceUri: Uri,
        displayName: String,
        tag: String = "Imported"
    ): WorkspaceDoc = withContext(Dispatchers.IO) {
        TempWorkspaceManager.useTempDir(context.cacheDir, "import_ws") { workDir ->
            val tmp = File(workDir, "import.pdf")
            SafSafeWriter.copyUriToTempFile(context, sourceUri, tmp)
            saveStagedPdf(context, tmp, displayName, tag)
        }
    }

    suspend fun exportToSaf(
        context: Context,
        doc: WorkspaceDoc,
        destUri: Uri
    ): Long = withContext(Dispatchers.IO) {
        val file = getDocFile(context, doc)
        SafSafeWriter.writeTempPdfToUri(context, file, destUri)
    }

    fun deleteDoc(context: Context, docId: String) {
        val current = listDocs(context)
        val target = current.firstOrNull { it.id == docId }
        if (target != null) {
            getDocFile(context, target).delete()
        }
        persistList(context, current.filterNot { it.id == docId })
    }

    fun buildShareIntent(context: Context, doc: WorkspaceDoc): Intent {
        val uri = getContentUri(context, doc)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${doc.displayName}")
    }

    private fun countPdfPages(file: File): Int {
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.pageCount.coerceAtLeast(1)
            }
        }
    }

    private fun upsertDoc(context: Context, doc: WorkspaceDoc) {
        val updated = (listOf(doc) + listDocs(context).filterNot { it.id == doc.id })
            .take(MAX_RECENT_DOCS)
        persistList(context, updated)
    }

    private fun persistList(context: Context, docs: List<WorkspaceDoc>) {
        val arr = JSONArray()
        for (item in docs) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("displayName", item.displayName)
                    put("fileName", item.fileName)
                    put("pageCount", item.pageCount)
                    put("sizeBytes", item.sizeBytes)
                    put("createdAtMs", item.createdAtMs)
                    put("tag", item.tag)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOCS_JSON, arr.toString())
            .apply()
    }
}
