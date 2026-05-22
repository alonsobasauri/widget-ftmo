package com.basauri.ftmowidget.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.basauri.ftmowidget.data.FtmoRepository
import com.basauri.ftmowidget.widget.FtmoWidget
import java.util.concurrent.TimeUnit

class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = FtmoRepository(applicationContext)
        return try {
            repository.refresh()
            FtmoWidget().updateAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            // The repository already persisted the error message; let Glance render it.
            FtmoWidget().updateAll(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "ftmo_widget_periodic_refresh"
        private const val ONE_SHOT_NAME = "ftmo_widget_oneshot_refresh"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

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
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(ONE_SHOT_NAME)
        }
    }
}
