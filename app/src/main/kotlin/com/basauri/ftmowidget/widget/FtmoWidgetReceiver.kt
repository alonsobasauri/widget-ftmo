package com.basauri.ftmowidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.basauri.ftmowidget.work.RefreshWorker

class FtmoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FtmoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.scheduleRecurring(context)
        RefreshWorker.requestImmediate(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.requestImmediate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancelAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            RefreshWorker.requestImmediate(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.basauri.ftmowidget.ACTION_REFRESH"
    }
}
