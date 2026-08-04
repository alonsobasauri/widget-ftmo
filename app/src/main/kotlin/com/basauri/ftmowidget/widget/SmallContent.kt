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
fun SmallContent(state: WidgetState) = SingleAccountScaffold(state) { snapshot, staleNote, refreshing ->
    SmallContentBody(snapshot, staleNote, refreshing)
}

@Composable
private fun SmallContentBody(
    snapshot: AccountSnapshot,
    staleNote: String?,
    refreshing: Boolean,
) {
    val context = LocalContext.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
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
        Text(text = context.getString(R.string.widget_equity), style = WidgetTheme.titleStyle())
        Spacer(GlanceModifier.height(2.dp))
        MoneyText(snapshot.equity, snapshot.currency, fontSizeSp = 20)
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text(text = context.getString(R.string.widget_today), style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            MoneyText(snapshot.todaysProfit, snapshot.currency, fontSizeSp = 14, withSign = true)
        }
        if (staleNote != null) {
            Text(
                text = Format.staleNote(staleNote, snapshot.fetchedAtMillis),
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.Warning),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
