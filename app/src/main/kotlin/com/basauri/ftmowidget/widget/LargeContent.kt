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
import com.basauri.ftmowidget.data.DayPoint
import com.basauri.ftmowidget.data.Format

@Composable
fun LargeContent(state: WidgetState) = SingleAccountScaffold(state) { snapshot, staleNote, refreshing ->
    LargeContentBody(snapshot, staleNote, refreshing)
}

@Composable
private fun LargeContentBody(snapshot: AccountSnapshot, staleNote: String?, refreshing: Boolean) {
    val context = LocalContext.current
    val stats = snapshot.stats
    val currency = snapshot.currency
    val objectives = snapshot.objectives.sortedBy { it.kind.ordinal }.take(3)
    // The latest day is already surfaced as "Today" in the header.
    val days = snapshot.days.sortedByDescending { it.date }.drop(1).take(5)

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
                text = headerLabel(snapshot),
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
                MoneyText(snapshot.equity, currency, fontSizeSp = 22)
                // Only show balance when it differs from equity (i.e. there is open
                // floating P&L); otherwise it just restates the equity figure.
                if (kotlin.math.abs(snapshot.equity - snapshot.balance) >= 0.01) {
                    Text(
                        text = "Bal: ${Format.moneyWhole(snapshot.balance, currency)}",
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.TextSecondary),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = context.getString(R.string.widget_today), style = WidgetTheme.titleStyle())
                MoneyText(snapshot.todaysProfit, currency, fontSizeSp = 18, withSign = true)
                Text(
                    text = "WR ${Format.percent(stats.winRatePct)} · PF ${Format.ratio(stats.profitFactor)}",
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TextSecondary),
                        fontSize = 10.sp,
                    ),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        objectives.forEachIndexed { index, objective ->
            if (index > 0) Spacer(GlanceModifier.height(6.dp))
            ObjectiveBar(objective, currency, trackWidth = 240.dp)
        }

        if (days.isNotEmpty()) {
            Spacer(GlanceModifier.height(10.dp))
            Text(
                text = context.getString(R.string.widget_daily_summary),
                style = WidgetTheme.titleStyle(),
            )
            Spacer(GlanceModifier.height(4.dp))
            days.forEach { day -> DailyRow(day, currency) }
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

@Composable
private fun DailyRow(day: DayPoint, currency: String?) {
    val amount = day.pnl ?: 0.0
    val color = WidgetTheme.colorForAmount(amount)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().height(20.dp),
    ) {
        Text(
            text = Format.shortDate(day.date),
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TextSecondary),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        // Blue Guardian publishes no per-day trade count; skip rather than print "0t".
        day.trades?.let {
            Text(
                text = "${it}t",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextMuted),
                    fontSize = 10.sp,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            text = Format.money(day.pnl, currency, withSign = true),
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** "#531303305 · MT5" for FTMO, "25k - Instant - MT5" for Blue Guardian. */
internal fun headerLabel(snapshot: AccountSnapshot): String {
    val platform = snapshot.platform?.takeIf { it.isNotBlank() && !snapshot.accountLabel.contains(it, true) }
    return if (platform != null) "${snapshot.accountLabel} · $platform" else snapshot.accountLabel
}
