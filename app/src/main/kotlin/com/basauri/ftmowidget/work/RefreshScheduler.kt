package com.basauri.ftmowidget.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.basauri.ftmowidget.widget.FtmoWidgetReceiver

/**
 * Drives the recurring refresh with exact alarms. WorkManager can't do sub-15-minute
 * periodic work and defers in Doze, so timing is handled by AlarmManager
 * (setExactAndAllowWhileIdle) while the actual network fetch still runs in
 * RefreshWorker. Each alarm is one-shot and re-armed when it fires (see
 * FtmoWidgetReceiver), so the interval can be any value the user picks.
 */
object RefreshScheduler {
    const val ACTION_ALARM_REFRESH = "com.basauri.ftmowidget.ALARM_REFRESH"
    private const val REQUEST_CODE = 7001

    /** Arms the next refresh [intervalMinutes] from now, replacing any pending alarm. */
    fun schedule(context: Context, intervalMinutes: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val minutes = intervalMinutes.coerceIn(1, 60)
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val pi = pendingIntent(context)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Exact-alarm permission denied: fall back to the inexact variant,
                // which Android may batch but still fires while idle.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FtmoWidgetReceiver::class.java).setAction(ACTION_ALARM_REFRESH)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
