package com.basauri.ftmowidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.basauri.ftmowidget.data.FtmoRepository
import com.basauri.ftmowidget.work.RefreshScheduler
import com.basauri.ftmowidget.work.RefreshWorker
import kotlinx.coroutines.runBlocking

class FtmoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FtmoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.requestImmediate(context)
        rescheduleAlarm(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.requestImmediate(context)
        rescheduleAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshScheduler.cancel(context)
        RefreshWorker.cancelAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> RefreshWorker.requestImmediate(context)
            RefreshScheduler.ACTION_ALARM_REFRESH -> {
                RefreshWorker.requestImmediate(context)
                rescheduleAlarm(context)
            }
        }
    }

    /** Reads the configured interval and arms the next exact alarm. */
    private fun rescheduleAlarm(context: Context) {
        val app = context.applicationContext
        val interval = runBlocking { FtmoRepository(app).refreshIntervalMinutes() }
        RefreshScheduler.schedule(app, interval)
    }

    companion object {
        const val ACTION_REFRESH = "com.basauri.ftmowidget.ACTION_REFRESH"
    }
}
