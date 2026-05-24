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
import com.basauri.ftmowidget.data.DailyEntry
import com.basauri.ftmowidget.data.Format
import com.basauri.ftmowidget.data.WidgetSnapshot
import com.basauri.ftmowidget.data.overallMaxLoss

@Composable
fun LargeContent(state: WidgetState) {
    when (state) {
        WidgetState.Unconfigured -> UnconfiguredCard()
        WidgetState.Loading -> LoadingCard()
        is WidgetState.Error -> if (state.cached != null) {
            LargeContentBody(state.cached, staleNote = state.message, refreshing = state.refreshing)
        } else ErrorCard(state.message)
        is WidgetState.Ready -> LargeContentBody(state.snapshot, staleNote = null, refreshing = state.refreshing)
    }
}

@Composable
private fun LargeContentBody(snapshot: WidgetSnapshot, staleNote: String?, refreshing: Boolean) {
    val context = LocalContext.current
    val metrix = snapshot.metrix
    val stats = metrix.statistics
    val info = metrix.info
    val objectives = metrix.objectives
    val currency = metrix.currency

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
                text = "#${metrix.login} · ${metrix.platform ?: ""}",
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
                MoneyText(stats.equity, fontSizeSp = 22)
                Text(
                    text = "Bal: ${Format.money(stats.balance)}",
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TextSecondary),
                        fontSize = 11.sp,
                    ),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = context.getString(R.string.widget_today), style = WidgetTheme.titleStyle())
                MoneyText(info.todaysProfit ?: info.todaysRealizedProfit, fontSizeSp = 18, withSign = true)
                Text(
                    text = "WR ${Format.percent(stats.winRate)} · PF ${Format.ratio(stats.profitFactor)}",
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TextSecondary),
                        fontSize = 10.sp,
                    ),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        ObjectiveRow(
            label = context.getString(R.string.widget_profit_target),
            objective = objectives.profit,
            currency = currency,
            trackWidth = 240.dp,
        )
        Spacer(GlanceModifier.height(6.dp))
        ObjectiveRow(
            label = context.getString(R.string.widget_max_daily_loss),
            objective = objectives.maxDailyLoss,
            currency = currency,
            trackWidth = 240.dp,
        )
        Spacer(GlanceModifier.height(6.dp))
        ObjectiveRow(
            label = context.getString(R.string.widget_max_loss),
            objective = objectives.overallMaxLoss,
            currency = currency,
            trackWidth = 240.dp,
        )

        Spacer(GlanceModifier.height(10.dp))
        Text(
            text = context.getString(R.string.widget_daily_summary),
            style = WidgetTheme.titleStyle(),
        )
        Spacer(GlanceModifier.height(4.dp))
        metrix.dailySummary.take(5).forEach { day -> DailyRow(day) }

        if (staleNote != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "stale: ${staleNote.take(60)}",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.Warning),
                    fontSize = 9.sp,
                ),
            )
        }
    }
}

@Composable
private fun DailyRow(day: DailyEntry) {
    val amount = day.realizedProfit.amount
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
        Text(
            text = "${day.tradesCount}t",
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TextMuted),
                fontSize = 10.sp,
            ),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = Format.money(day.realizedProfit, withSign = true),
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
