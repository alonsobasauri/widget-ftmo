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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.data.AccountSnapshot
import com.basauri.ftmowidget.data.Format

@Composable
fun MediumContent(state: WidgetState) {
    when (state) {
        WidgetState.Unconfigured -> UnconfiguredCard()
        WidgetState.Loading -> LoadingCard()
        is WidgetState.Error -> if (state.cached != null) {
            MediumContentBody(state.cached, staleNote = state.message, refreshing = state.refreshing)
        } else ErrorCard(state.message)
        is WidgetState.Ready -> MediumContentBody(state.snapshot, staleNote = null, refreshing = state.refreshing)
    }
}

@Composable
private fun MediumContentBody(snapshot: AccountSnapshot, staleNote: String?, refreshing: Boolean) {
    val context = LocalContext.current
    // Only three bars fit at this size; take whichever the provider reported, in
    // the order a trader checks them.
    val objectives = snapshot.objectives.sortedBy { it.kind.ordinal }.take(3)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
        // Header row
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            StatusBadge(snapshot)
            if (refreshing) {
                Spacer(GlanceModifier.width(6.dp))
                RefreshDot()
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = snapshot.accountLabel,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextMuted),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = context.getString(R.string.widget_equity), style = WidgetTheme.titleStyle())
                MoneyText(snapshot.equity, snapshot.currency, fontSizeSp = 20)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = context.getString(R.string.widget_today), style = WidgetTheme.titleStyle())
                MoneyText(snapshot.todaysProfit, snapshot.currency, fontSizeSp = 16, withSign = true)
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        objectives.forEachIndexed { index, objective ->
            if (index > 0) Spacer(GlanceModifier.height(6.dp))
            ObjectiveBar(objective, snapshot.currency, trackWidth = 200.dp)
        }

        if (staleNote != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = Format.staleNote(staleNote, snapshot.fetchedAtMillis),
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.Warning),
                    fontSize = 10.sp,
                ),
            )
        }
    }
}
