package com.basauri.ftmowidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import com.basauri.ftmowidget.data.FtmoRepository

class FtmoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = FtmoRepository(context)
        val state = resolveState(repository)
        provideContent { WidgetSurface { Body(state) } }
    }

    @Composable
    private fun Body(state: WidgetState) {
        val size = LocalSize.current
        when {
            size.width >= LARGE.width && size.height >= LARGE.height -> LargeContent(state)
            size.width >= MEDIUM.width && size.height >= MEDIUM.height -> MediumContent(state)
            else -> SmallContent(state)
        }
    }

    private suspend fun resolveState(repository: FtmoRepository): WidgetState {
        repository.currentIdentity() ?: return WidgetState.Unconfigured
        val cached = repository.cachedSnapshot()
        val error = repository.cachedError()
        return when {
            error != null && cached == null -> WidgetState.Error(error, null)
            error != null -> WidgetState.Error(error, cached)
            cached != null -> WidgetState.Ready(cached)
            else -> WidgetState.Loading
        }
    }

    companion object {
        val SMALL = DpSize(140.dp, 90.dp)
        val MEDIUM = DpSize(220.dp, 140.dp)
        val LARGE = DpSize(260.dp, 240.dp)
    }
}

@Composable
private fun WidgetSurface(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetTheme.Background)
            .padding(WidgetTheme.cardPadding),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}
