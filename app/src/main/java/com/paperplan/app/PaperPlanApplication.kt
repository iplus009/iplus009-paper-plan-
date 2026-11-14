package com.paperplan.app

import android.app.Application
import com.paperplan.app.core.storage.TempWorkspaceManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single application-wide initialization point for PDFBox resources and stale temp cleanup.
 * Keeps onCreate non-blocking on the main thread.
 */
class PaperPlanApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        appScope.launch {
            TempWorkspaceManager.pruneStaleWorkspaces(cacheDir)
        }
    }
}
