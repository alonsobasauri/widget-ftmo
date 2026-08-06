package com.basauri.ftmowidget.config

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Shows or hides the app's launcher entry.
 *
 * The entry is an `<activity-alias>` rather than ConfigActivity itself, because
 * ConfigActivity has to stay enabled to answer APPWIDGET_CONFIGURE — disabling
 * it would take the widget's own setup screen down with the icon.
 *
 * Hiding is safe to undo without the icon: placing a widget, or long-pressing an
 * existing one and choosing reconfigure, still opens the config screen. That is
 * the only way back in once the icon is gone, so the toggle lives there.
 */
object LauncherIcon {

    private fun alias(context: Context) =
        ComponentName(context.packageName, "com.basauri.ftmowidget.config.LauncherAlias")

    /**
     * DEFAULT means "whatever the manifest says", which here is enabled — so only
     * an explicit DISABLED counts as hidden.
     */
    fun isVisible(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setVisible(context: Context, visible: Boolean) {
        val state = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        // DONT_KILL_APP keeps the config screen alive long enough to render the
        // switch's new position instead of dying mid-toggle.
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                alias(context),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
