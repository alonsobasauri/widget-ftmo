package com.basauri.ftmowidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.data.Format

@Composable
fun SmallContent(state: WidgetState) {
    when (state) {
        WidgetState.Unconfigured -> UnconfiguredCard()
        WidgetState.Loading -> LoadingCard()
        is WidgetState.Error -> if (state.cached != null) {
            SmallContentBody(state.cached, staleNote = state.message)
        } else ErrorCard(state.message)
        is WidgetState.Ready -> SmallContentBody(state.snapshot, staleNote = null)
    }
}

@Composable
private fun SmallContentBody(snapshot: com.basauri.ftmowidget.data.WidgetSnapshot, staleNote: String?) {
    val context = LocalContext.current
    val metrix = snapshot.metrix
    val equity = metrix.statistics.equity
    val today = metrix.info.todaysProfit ?: metrix.info.todaysRealizedProfit

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            StatusBadge(snapshot)
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "#${metrix.login}",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextMuted),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(text = context.getString(R.string.widget_equity), style = WidgetTheme.titleStyle())
        Spacer(GlanceModifier.height(2.dp))
        MoneyText(equity, fontSizeSp = 20)
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(text = context.getString(R.string.widget_today), style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            MoneyText(today, fontSizeSp = 14, withSign = true)
        }
        if (staleNote != null) {
            Text(
                text = "stale",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.Warning),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
