package com.basauri.ftmowidget.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.basauri.ftmowidget.data.FtmoRepository
import com.basauri.ftmowidget.work.RefreshScheduler
import kotlinx.coroutines.runBlocking

/**
 * Exact alarms don't survive a reboot or an app update, so re-arm the refresh alarm
 * on those events — but only if at least one widget is actually placed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val app = context.applicationContext
                val ids = AppWidgetManager.getInstance(app)
                    .getAppWidgetIds(ComponentName(app, FtmoWidgetReceiver::class.java))
                if (ids.isEmpty()) return
                val interval = runBlocking { FtmoRepository(app).refreshIntervalMinutes() }
                RefreshScheduler.schedule(app, interval)
            }
        }
    }
}
