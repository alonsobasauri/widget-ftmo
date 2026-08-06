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
import androidx.glance.layout.Box
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

/**
 * Rows listed under Daily Summary, excluding today. The LazyColumn this layout
 * is built on renders through a collection adapter, so the row count is not
 * bound by the RemoteViews node budget that constrains the smaller layouts.
 */
private const val DAILY_SUMMARY_DAYS = 15

@Composable
fun XLargeContent(state: WidgetState) = SingleAccountScaffold(state) { snapshot, staleNote, refreshing ->
    XLargeBody(snapshot, staleNote, refreshing)
}

@Composable
private fun XLargeBody(snapshot: AccountSnapshot, staleNote: String?, refreshing: Boolean) {
    // The latest day is already surfaced as "Today" in the header; drop it here so
    // the log shows the days leading up to today instead of repeating it.
    // Consecutive calendar days: quiet days and weekends keep their row, so the
    // dates read as a continuous stretch rather than a filtered selection.
    val days = snapshot.days.sortedByDescending { it.date }.drop(1).take(DAILY_SUMMARY_DAYS)
    val hasTradeColumns = days.any { it.trades != null || it.lots != null }

    // A flat Column generates too many RemoteViews nodes for a 4x4 widget and the
    // host silently drops the overflow. LazyColumn renders items through a collection
    // adapter, sidestepping that limit and adding scroll when content exceeds height.
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        item { HeaderAndEquity(snapshot, refreshing) }
        item { ObjectivesSection(snapshot) }
        item { PerformanceSection(snapshot) }
        if (snapshot.days.count { it.pnl != null } >= 2) {
            item { SectionRow { SectionTitle(LocalContext.current.getString(R.string.widget_equity_trend)) } }
            item { TapRow { PnlSparkline(snapshot.days) } }
        }
        if (days.isNotEmpty()) {
            item { SectionRow { SectionTitle(LocalContext.current.getString(R.string.widget_daily_summary)) } }
            item { TapRow { DailyHeaderRow(hasTradeColumns) } }
            days.forEach { day -> item { TapRow { DailyRow(day, snapshot.currency, hasTradeColumns) } } }
        }
        if (staleNote != null) {
            item {
                TapRow {
                    Text(
                        text = Format.staleNote(staleNote, snapshot.fetchedAtMillis),
                        style = TextStyle(color = ColorProvider(WidgetTheme.Warning), fontSize = 10.sp),
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
private fun HeaderAndEquity(snapshot: AccountSnapshot, refreshing: Boolean) {
    val context = LocalContext.current
    TapRow {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            StatusBadge(snapshot)
            Spacer(GlanceModifier.defaultWeight())
            ShadowText(
                text = headerLabel(snapshot),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextMuted),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            RefreshButton(refreshing)
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                ShadowText(text = context.getString(R.string.widget_equity), maxLines = 1, style = WidgetTheme.titleStyle())
                MoneyText(snapshot.equity, snapshot.currency, fontSizeSp = 22)
                // Only show balance when it differs from equity (i.e. there is open
                // floating P&L); otherwise it just restates the equity figure.
                if (kotlin.math.abs(snapshot.equity - snapshot.balance) >= 0.01) {
                    ShadowText(
                        text = "Bal: ${Format.moneyWhole(snapshot.balance, snapshot.currency)}",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(WidgetTheme.TextSecondary), fontSize = 11.sp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                ShadowText(text = context.getString(R.string.widget_today), maxLines = 1, style = WidgetTheme.titleStyle())
                MoneyText(snapshot.todaysProfit, snapshot.currency, fontSizeSp = 18, withSign = true)
            }
        }
    }
}

@Composable
private fun ObjectivesSection(snapshot: AccountSnapshot) {
    val context = LocalContext.current
    SectionRow {
        SectionTitle(context.getString(R.string.widget_objectives))
        Spacer(GlanceModifier.height(4.dp))
        snapshot.objectives.sortedBy { it.kind.ordinal }.forEachIndexed { index, objective ->
            if (index > 0) Spacer(GlanceModifier.height(6.dp))
            // Room here for the bidirectional target scale, which makes "below
            // break-even" obvious in a way the plain bar does not.
            ObjectiveBar(objective, snapshot.currency, trackWidth = 240.dp, bidirectionalTarget = true)
        }
    }
}

@Composable
private fun PerformanceSection(snapshot: AccountSnapshot) {
    val context = LocalContext.current
    val stats = snapshot.stats
    // Cells are dropped when the provider has no figure for them, so a Blue
    // Guardian account shows a tighter grid instead of columns of dashes.
    val primary = listOfNotNull(
        stats.winRatePct?.let { StatCell("WR", Format.percent(it)) },
        stats.profitFactor?.let {
            StatCell("PF", Format.ratio(it), warn = it < 1.0 && (stats.tradesCount ?: 0) > 0)
        },
        stats.expectancy?.let { StatCell("Exp", Format.money(it, snapshot.currency, withSign = true), warn = it < 0.0) },
        stats.tradesCount?.let { StatCell("Trades", "$it") },
    )
    val secondary = listOfNotNull(
        stats.avgRiskReward?.let { StatCell("RRR", Format.ratio(it)) },
        stats.sharpe?.let { StatCell("Sharpe", Format.ratio(it), warn = it < 0.0) },
        stats.avgWin?.let { StatCell("Avg+", Format.money(it, snapshot.currency)) },
        stats.avgLoss?.let { StatCell("Avg−", Format.money(it, snapshot.currency)) },
        stats.lots?.let { StatCell("Lots", Format.ratio(it)) },
    )
    SectionRow {
        SectionTitle(context.getString(R.string.widget_performance))
        Spacer(GlanceModifier.height(4.dp))
        // Primary, glanceable stats — larger.
        if (primary.isNotEmpty()) PerfRowN(primary, valueSize = 17, labelSize = 10)
        if (secondary.isNotEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            // Secondary, advanced stats — compact.
            PerfRowN(secondary, valueSize = 11, labelSize = 9)
        }
    }
}

/** Column header for the daily summary, sharing widths with DailyRow so the
 *  numeric columns line up. */
@Composable
private fun DailyHeaderRow(withTradeColumns: Boolean) {
    val muted = TextStyle(
        color = ColorProvider(WidgetTheme.TextMuted),
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().height(14.dp)) {
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
            ShadowText(text = "Day", maxLines = 1, style = muted)
        }
        if (withTradeColumns) {
            DailyNumCol("Trades", muted, 40.dp)
            DailyNumCol("Lots", muted, 52.dp)
        }
        DailyNumCol("P&L", muted, 76.dp)
    }
}

@Composable
private fun DailyRow(day: DayPoint, currency: String?, withTradeColumns: Boolean) {
    val amount = day.pnl ?: 0.0
    val color = WidgetTheme.colorForAmount(amount)
    val muted = TextStyle(color = ColorProvider(WidgetTheme.TextMuted), fontSize = 10.sp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().height(18.dp),
    ) {
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
            ShadowText(
                text = Format.shortDate(day.date),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextSecondary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        if (withTradeColumns) {
            DailyNumCol(day.trades?.toString() ?: "—", muted, 40.dp)
            DailyNumCol(Format.ratio(day.lots), muted, 52.dp)
        }
        DailyNumCol(
            Format.money(day.pnl, currency, withSign = true),
            TextStyle(color = ColorProvider(color), fontSize = 11.sp, fontWeight = FontWeight.Bold),
            76.dp,
        )
    }
}

/** Fixed-width, right-aligned cell so daily columns line up with their header. */
@Composable
private fun DailyNumCol(text: String, style: TextStyle, width: androidx.compose.ui.unit.Dp) {
    Box(modifier = GlanceModifier.width(width), contentAlignment = Alignment.CenterEnd) {
        ShadowText(text = text, maxLines = 1, style = style)
    }
}
