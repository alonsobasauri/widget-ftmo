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

@Composable
fun XLargeContent(state: WidgetState) {
    when (state) {
        WidgetState.Unconfigured -> UnconfiguredCard()
        WidgetState.Loading -> LoadingCard()
        is WidgetState.Error -> if (state.cached != null) {
            XLargeBody(state.cached, staleNote = state.message, refreshing = state.refreshing)
        } else ErrorCard(state.message)
        is WidgetState.Ready -> XLargeBody(state.snapshot, staleNote = null, refreshing = state.refreshing)
    }
}

@Composable
private fun XLargeBody(snapshot: WidgetSnapshot, staleNote: String?, refreshing: Boolean) {
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
        // Header
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

        // Equity / Today
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = context.getString(R.string.widget_equity), style = WidgetTheme.titleStyle())
                MoneyText(stats.equity, fontSizeSp = 22)
                Text(
                    text = "Bal: ${Format.money(stats.balance)}",
                    style = TextStyle(color = ColorProvider(WidgetTheme.TextSecondary), fontSize = 11.sp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = context.getString(R.string.widget_today), style = WidgetTheme.titleStyle())
                MoneyText(info.todaysProfit ?: info.todaysRealizedProfit, fontSizeSp = 18, withSign = true)
                Text(
                    text = "${stats.tradesCount}t · ${Format.ratio(stats.lots)} lots",
                    style = TextStyle(color = ColorProvider(WidgetTheme.TextSecondary), fontSize = 10.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        // Objectives
        SectionTitle(context.getString(R.string.widget_objectives))
        Spacer(GlanceModifier.height(4.dp))
        ProfitTargetRow(objectives.profit, currency, trackHalfWidth = 120.dp)
        Spacer(GlanceModifier.height(6.dp))
        BufferRow(
            label = context.getString(R.string.widget_max_daily_loss),
            objective = objectives.maxDailyLoss,
            currency = currency,
            trackWidth = 240.dp,
        )
        Spacer(GlanceModifier.height(6.dp))
        BufferRow(
            label = context.getString(R.string.widget_max_loss),
            objective = objectives.maxLoss ?: objectives.maxMidnightBalanceMaxLoss,
            currency = currency,
            trackWidth = 240.dp,
        )
        Spacer(GlanceModifier.height(10.dp))

        // Performance grid 3x3
        SectionTitle(context.getString(R.string.widget_performance))
        Spacer(GlanceModifier.height(4.dp))
        PerfRow(
            StatCell("WR", Format.percent(stats.winRate)),
            StatCell("PF", Format.ratio(stats.profitFactor), warn = stats.profitFactor < 1.0 && stats.tradesCount > 0),
            StatCell("Exp", Format.money(stats.expectancy, withSign = true), warn = (stats.expectancy?.amount ?: 0.0) < 0.0),
        )
        Spacer(GlanceModifier.height(4.dp))
        PerfRow(
            StatCell("RRR", Format.ratio(stats.avgRiskToRewardRate)),
            StatCell("Sharpe", Format.ratio(stats.sharpeRate), warn = stats.sharpeRate < 0.0),
            StatCell("Trades", "${stats.tradesCount}"),
        )
        Spacer(GlanceModifier.height(4.dp))
        PerfRow(
            StatCell("Avg+", Format.money(stats.avgProfit)),
            StatCell("Avg−", Format.money(stats.avgLoss)),
            StatCell("Lots", Format.ratio(stats.lots)),
        )
        Spacer(GlanceModifier.height(10.dp))

        // Daily summary (5 días)
        SectionTitle(context.getString(R.string.widget_daily_summary))
        Spacer(GlanceModifier.height(2.dp))
        metrix.dailySummary.take(5).forEach { day -> DailyRow(day) }

        if (staleNote != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "stale: ${staleNote.take(60)}",
                style = TextStyle(color = ColorProvider(WidgetTheme.Warning), fontSize = 9.sp),
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
        modifier = GlanceModifier.fillMaxWidth().height(18.dp),
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
            style = TextStyle(color = ColorProvider(WidgetTheme.TextMuted), fontSize = 10.sp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = Format.ratio(day.lots),
            style = TextStyle(color = ColorProvider(WidgetTheme.TextMuted), fontSize = 10.sp),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = Format.money(day.realizedProfit, withSign = true),
            style = TextStyle(color = ColorProvider(color), fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
}
