package com.basauri.ftmowidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.basauri.ftmowidget.data.FtmoRepository
import com.basauri.ftmowidget.work.RefreshWorker

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Flip the flag first and repaint immediately so the user gets feedback
        // even if WorkManager waits a moment before actually launching the worker.
        FtmoRepository(context).setRefreshing(true)
        FtmoWidget().updateAll(context)
        RefreshWorker.requestImmediate(context)
    }
}
