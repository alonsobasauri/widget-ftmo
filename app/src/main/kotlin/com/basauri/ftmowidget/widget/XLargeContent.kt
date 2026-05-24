package com.basauri.ftmowidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
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
    val metrix = snapshot.metrix
    val days = metrix.dailySummary.take(5)

    // A flat Column generates too many RemoteViews nodes for a 4x4 widget and the
    // host silently drops the overflow. LazyColumn renders items through a collection
    // adapter, sidestepping that limit and adding scroll when content exceeds height.
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        item { HeaderAndEquity(snapshot, refreshing) }
        item { ObjectivesSection(snapshot) }
        item { PerformanceSection(snapshot) }
        item { SectionRow { SectionTitle(LocalContext.current.getString(R.string.widget_daily_summary)) } }
        days.forEach { day -> item { TapRow { DailyRow(day) } } }
        if (staleNote != null) {
            item {
                TapRow {
                    Text(
                        text = "stale: ${staleNote.take(60)}",
                        style = TextStyle(color = ColorProvider(WidgetTheme.Warning), fontSize = 9.sp),
                    )
                }
            }
        }
    }
}

/** Wraps a section so a tap anywhere on it triggers a refresh. */
@Composable
private fun TapRow(content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionRunCallback<RefreshAction>()),
    ) { content() }
}

@Composable
private fun SectionRow(content: @Composable () -> Unit) {
    Column(modifier = GlanceModifier.fillMaxWidth().clickable(actionRunCallback<RefreshAction>())) {
        Spacer(GlanceModifier.height(8.dp))
        content()
        Spacer(GlanceModifier.height(2.dp))
    }
}

@Composable
private fun HeaderAndEquity(snapshot: WidgetSnapshot, refreshing: Boolean) {
    val context = LocalContext.current
    val metrix = snapshot.metrix
    val stats = metrix.statistics
    val info = metrix.info
    TapRow {
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
    }
}

@Composable
private fun ObjectivesSection(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val objectives = snapshot.metrix.objectives
    val currency = snapshot.metrix.currency
    SectionRow {
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
    }
}

@Composable
private fun PerformanceSection(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val stats = snapshot.metrix.statistics
    SectionRow {
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
