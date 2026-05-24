package com.basauri.ftmowidget.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * Thin wrapper around the system DownloadManager. The actual APK download is performed and
 * tracked by the system; this class only enqueues, polls status, and launches the package
 * installer once the download completes.
 *
 * Files land in the public Downloads directory so the user can find them in their Files app
 * if anything goes wrong.
 */
class UpdateDownloader(private val context: Context) {

    private val dm: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(url: String, apkName: String, title: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(apkName)
            .setMimeType(MIME_APK)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        return dm.enqueue(request)
    }

    fun query(id: Long): DownloadStatus {
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return DownloadStatus.Unknown
        cursor.use { c ->
            if (!c.moveToFirst()) return DownloadStatus.Unknown
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Successful(
                    uri = dm.getUriForDownloadedFile(id) ?: c.localUriOrNull()
                )
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    DownloadStatus.Failed(reason = reason)
                }
                DownloadManager.STATUS_PAUSED -> DownloadStatus.Pending
                DownloadManager.STATUS_PENDING -> DownloadStatus.Pending
                DownloadManager.STATUS_RUNNING -> {
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    DownloadStatus.Running(done, total)
                }
                else -> DownloadStatus.Unknown
            }
        }
    }

    fun launchInstaller(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    /**
     * Registers a one-shot receiver for the given [downloadId]. The caller is responsible for
     * calling [Context.unregisterReceiver] on the returned instance once they're done (the
     * receiver does not auto-unregister).
     */
    fun registerCompletionReceiver(
        downloadId: Long,
        onComplete: (DownloadStatus) -> Unit,
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                onComplete(query(downloadId))
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_EXPORTED,
        )
        return receiver
    }

    private fun android.database.Cursor.localUriOrNull(): Uri? {
        val idx = getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        if (idx < 0) return null
        val raw = getString(idx) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    companion object {
        const val MIME_APK = "application/vnd.android.package-archive"
    }
}

sealed interface DownloadStatus {
    data object Unknown : DownloadStatus
    data object Pending : DownloadStatus
    data class Running(val bytesDone: Long, val bytesTotal: Long) : DownloadStatus
    data class Successful(val uri: Uri?) : DownloadStatus
    data class Failed(val reason: Int) : DownloadStatus
}
