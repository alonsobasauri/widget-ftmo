package com.basauri.ftmowidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.basauri.ftmowidget.data.AccountRepository
import com.basauri.ftmowidget.data.WidgetBinding

class FtmoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE, XLARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = AccountRepository(context)
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val state = resolveState(repository, appWidgetId)
        val backgroundAlpha = repository.backgroundAlpha()
        provideContent { WidgetSurface(backgroundAlpha) { Body(state) } }
    }

    @Composable
    private fun Body(state: WidgetState) {
        val size = LocalSize.current
        // The stacked list needs its own layout; the single-account layouts
        // don't degrade into it.
        if (state is WidgetState.Multi) {
            MultiContent(state, size)
            return
        }
        when {
            size.width >= XLARGE.width && size.height >= XLARGE.height -> XLargeContent(state)
            size.width >= LARGE.width && size.height >= LARGE.height -> LargeContent(state)
            size.width >= MEDIUM.width && size.height >= MEDIUM.height -> MediumContent(state)
            else -> SmallContent(state)
        }
    }

    /**
     * Resolves which account(s) this particular placed widget shows.
     *
     * Widgets configured before per-widget binding existed have no stored
     * binding; they fall back to the first account, which is the one they were
     * already showing.
     */
    private suspend fun resolveState(repository: AccountRepository, appWidgetId: Int?): WidgetState {
        val views = repository.views()
        if (views.isEmpty()) return WidgetState.Unconfigured
        val refreshing = repository.cachedRefreshing()
        val binding = appWidgetId?.let { repository.binding(it) }

        if (binding == WidgetBinding.ALL) {
            return WidgetState.Multi(views, refreshing)
        }
        val view = views.firstOrNull { it.ref.id == binding } ?: views.first()
        if (view.snapshot == null && view.error == null) return WidgetState.Loading
        return WidgetState.Single(view, refreshing)
    }

    companion object {
        val SMALL = DpSize(140.dp, 90.dp)
        val MEDIUM = DpSize(220.dp, 140.dp)
        val LARGE = DpSize(260.dp, 240.dp)
        // 4x4 on most launchers maps to roughly 280-320dp square; use a slightly
        // narrower trigger so the layout activates reliably when the user resizes.
        val XLARGE = DpSize(280.dp, 360.dp)
    }
}

@Composable
private fun WidgetSurface(backgroundAlpha: Float, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.Background.copy(alpha = backgroundAlpha))
            .padding(WidgetTheme.cardPadding),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}
