package com.basauri.ftmowidget.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.basauri.ftmowidget.data.FtmoRepository
import com.basauri.ftmowidget.widget.FtmoWidget

/**
 * Performs a single fetch + widget re-render. Recurring timing is owned by
 * RefreshScheduler (exact alarms); this worker just does the network call with a
 * connectivity constraint so it waits for a connection rather than failing blind.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = FtmoRepository(applicationContext)
        repository.setRefreshing(true)
        return try {
            repository.refresh()
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        } finally {
            repository.setRefreshing(false)
            FtmoWidget().updateAll(applicationContext)
        }
    }

    companion object {
        private const val ONE_SHOT_NAME = "ftmo_widget_oneshot_refresh"
        // Names used by superseded scheduling strategies; cancelled on cleanup so an
        // upgraded install doesn't keep an old periodic worker or self-rescheduling
        // chain running alongside the alarm-driven refresh.
        private val LEGACY_NAMES = listOf(
            "ftmo_widget_periodic_refresh",
            "ftmo_widget_refresh_chain",
        )

        fun requestImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(ONE_SHOT_NAME)
            LEGACY_NAMES.forEach { wm.cancelUniqueWork(it) }
        }
    }
}
