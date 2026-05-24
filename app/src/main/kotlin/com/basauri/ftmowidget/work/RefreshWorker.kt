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
import java.util.concurrent.TimeUnit

class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = FtmoRepository(applicationContext)
        val isChainTick = tags.contains(CHAIN_TAG)
        repository.setRefreshing(true)
        var retrying = false
        val result = try {
            repository.refresh()
            Result.success()
        } catch (t: Throwable) {
            // Chain ticks don't retry: the next tick is only minutes away, so a
            // backoff would just blur the cadence. One-shot (tap) refreshes retry once.
            if (!isChainTick && runAttemptCount < 2) {
                retrying = true
                Result.retry()
            } else {
                Result.success()
            }
        } finally {
            repository.setRefreshing(false)
            FtmoWidget().updateAll(applicationContext)
        }
        // WorkManager has no sub-15-minute periodic work, so the recurring refresh is a
        // self-rescheduling chain: each tick enqueues the next. Skip when this run is
        // being retried or was cancelled (e.g. the last widget was removed).
        if (isChainTick && !retrying && !isStopped) {
            enqueueChainTick(applicationContext, delayMinutes = REFRESH_INTERVAL_MINUTES)
        }
        return result
    }

    companion object {
        private const val CHAIN_NAME = "ftmo_widget_refresh_chain"
        private const val CHAIN_TAG = "ftmo_widget_chain_tick"
        private const val ONE_SHOT_NAME = "ftmo_widget_oneshot_refresh"
        private const val LEGACY_PERIODIC_NAME = "ftmo_widget_periodic_refresh"
        private const val REFRESH_INTERVAL_MINUTES = 5L

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        private fun enqueueChainTick(context: Context, delayMinutes: Long) {
            val builder = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(networkConstraints())
                .addTag(CHAIN_TAG)
            if (delayMinutes > 0) builder.setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            WorkManager.getInstance(context).enqueueUniqueWork(
                CHAIN_NAME,
                ExistingWorkPolicy.REPLACE,
                builder.build(),
            )
        }

        /** Starts the recurring refresh chain (first tick runs immediately). */
        fun scheduleRecurring(context: Context) {
            val wm = WorkManager.getInstance(context)
            // Drop the 15-minute periodic worker from older versions so an upgraded
            // install doesn't run both schedules.
            wm.cancelUniqueWork(LEGACY_PERIODIC_NAME)
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(networkConstraints())
                .addTag(CHAIN_TAG)
                .build()
            // KEEP so re-entry (config save, widget re-add) doesn't restart a healthy
            // chain, but still revives it if no tick is pending.
            wm.enqueueUniqueWork(CHAIN_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun requestImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(CHAIN_NAME)
            wm.cancelUniqueWork(ONE_SHOT_NAME)
            wm.cancelUniqueWork(LEGACY_PERIODIC_NAME)
        }
    }
}
