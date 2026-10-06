package com.tileshell.feature.start

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tileshell.core.data.AutoExportNaming
import com.tileshell.core.data.AutoExportPrefs
import com.tileshell.core.data.AutoExportState
import java.util.concurrent.TimeUnit

/** Writes dated backup files into the folder the user picked (Storage Access Framework). */
internal object AutoExportWriter {
    private const val MIME = "application/json"

    private fun parentDoc(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** The folder's own name, for the settings row; null when it can't be read. */
    fun folderName(context: Context, tree: Uri): String? = runCatching {
        context.contentResolver.query(
            parentDoc(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** True while the app still holds write access to [tree]. */
    fun hasAccess(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    fun write(context: Context, tree: Uri, name: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val doc = DocumentsContract.createDocument(resolver, parentDoc(tree), MIME, name)
            ?: error("couldn't create the file")
        (resolver.openOutputStream(doc, "wt") ?: error("couldn't open the file")).use { it.write(bytes) }
    }

    /** Deletes older auto-exports so only the newest [AutoExportState.KEEP] remain. */
    fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val files = mutableMapOf<String, String>() // display name -> document id
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) files[c.getString(1)] = c.getString(0)
        }
        AutoExportNaming.namesToDelete(files.keys.toList()).forEach { name ->
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, files.getValue(name)))
            }
        }
    }
}

/** One scheduled (or "export now") auto-export run. Never retries in a loop: the next period tries again. */
class AutoExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val state = AutoExportPrefs.current(app)
        val uriText = state.folderUri
        if (!state.enabled || uriText == null) return Result.success()
        val tree = Uri.parse(uriText)
        val now = System.currentTimeMillis()
        val error = runCatching {
            if (!AutoExportWriter.hasAccess(app, tree)) error("folder access lost — choose the folder again")
            val json = BackupExporter.buildJson(app)
            AutoExportWriter.write(app, tree, AutoExportNaming.fileName(now), json.encodeToByteArray())
            AutoExportWriter.prune(app, tree)
        }.exceptionOrNull()?.let { it.message?.take(80) ?: "couldn't save" }
        AutoExportPrefs.update(app) { it.copy(lastRunAt = now, lastError = error) }
        return Result.success()
    }
}

object AutoExportScheduler {
    private const val PERIODIC = "auto_export"
    private const val NOW = "auto_export_now"

    /** Re-applies the saved choice: schedules the periodic run while it is on, cancels it otherwise. */
    fun sync(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        val state = AutoExportPrefs.current(app)
        if (!state.ready) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoExportWorker>(state.intervalDays.toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            // The first file is written right away by runNow when the user turns it on;
            // without this delay the periodic job would run immediately as well.
            .setInitialDelay(state.intervalDays.toLong(), TimeUnit.DAYS)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoExportWorker>().build(),
        )
    }
}
