package com.basauri.ftmowidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.data.AccountStatus
import com.basauri.ftmowidget.data.AccountView
import com.basauri.ftmowidget.data.Format
import com.basauri.ftmowidget.data.ObjectiveKind

/**
 * Every configured account, one per row.
 *
 * This is deliberately not a stack of the single-account layouts: at four rows
 * those would overflow any home-screen cell. Each row carries only what answers
 * "do I need to look at this account right now" — equity, today's P&L, and how
 * much loss buffer is left — and tapping through opens the full view.
 *
 * Rendered with LazyColumn for the same reason XLargeContent is: a flat Column
 * blows past the RemoteViews node budget and the host silently drops the tail.
 */
@Composable
fun MultiContent(state: WidgetState.Multi, size: DpSize) {
    val context = LocalContext.current
    val accounts = state.accounts
    // Below this width the buffer bar has no room to read as a bar.
    val showBuffer = size.width >= FtmoWidget.MEDIUM.width
    val compact = size.height < FtmoWidget.LARGE.height

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        item {
            TapRow {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                    ShadowText(
                        text = context.getString(R.string.widget_accounts_count, accounts.size),
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.TextMuted),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (state.refreshing) {
                        Spacer(GlanceModifier.width(6.dp))
                        RefreshDot()
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    CombinedEquity(accounts)
                }
                Spacer(GlanceModifier.height(6.dp))
            }
        }
        accounts.forEach { view ->
            item { TapRow { AccountRow(view, showBuffer = showBuffer, compact = compact) } }
        }
    }
}

/**
 * Total equity across accounts, shown only when every account reports the same
 * currency — summing USD and EUR would produce a confidently wrong number.
 */
@Composable
private fun CombinedEquity(accounts: List<AccountView>) {
    val snapshots = accounts.mapNotNull { it.snapshot }
    if (snapshots.size < 2) return
    val currencies = snapshots.map { it.currency }.distinct()
    if (currencies.size != 1) return
    ShadowText(
        text = Format.moneyWhole(snapshots.sumOf { it.equity }, currencies.first()),
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(WidgetTheme.TextSecondary),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun AccountRow(view: AccountView, showBuffer: Boolean, compact: Boolean) {
    val snapshot = view.snapshot
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            StatusDot(snapshot?.status ?: AccountStatus.UNKNOWN, hasData = snapshot != null)
            Spacer(GlanceModifier.width(6.dp))
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
                ShadowText(
                    text = snapshot?.let { headerLabel(it) } ?: view.ref.displayName(),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TextPrimary),
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            if (snapshot == null) {
                // No snapshot yet: say so instead of rendering a zeroed row that
                // reads like a real balance.
                ShadowText(
                    text = if (view.error != null) "⚠" else "…",
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(WidgetTheme.Warning), fontSize = 12.sp),
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    ShadowText(
                        text = Format.moneyWhole(snapshot.equity, snapshot.currency),
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.TextPrimary),
                            fontSize = if (compact) 12.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    ShadowText(
                        text = Format.money(snapshot.todaysProfit, snapshot.currency, withSign = true),
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.colorForAmount(snapshot.todaysProfit ?: 0.0)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
        if (showBuffer && snapshot != null) {
            // The tightest loss cap is the one that can end the account today,
            // so that is the single bar worth the vertical space.
            val tightest = snapshot.objectives
                .filter { it.kind == ObjectiveKind.MAX_DAILY_LOSS || it.kind == ObjectiveKind.MAX_LOSS }
                .filter { it.limit > 0.0 }
                .maxByOrNull { it.ratio }
            if (tightest != null) {
                Spacer(GlanceModifier.height(3.dp))
                val used = (tightest.current / tightest.limit).toFloat().coerceIn(0f, 1f)
                ProgressBar(
                    progress = used,
                    color = when {
                        used < 0.6f -> WidgetTheme.Success
                        used < 0.85f -> WidgetTheme.Warning
                        else -> WidgetTheme.Danger
                    },
                    trackWidth = 240.dp,
                )
            }
        }
        Spacer(GlanceModifier.height(if (compact) 6.dp else 9.dp))
    }
}

/** Compact status indicator; the full badge is too wide for a list row. */
@Composable
private fun StatusDot(status: AccountStatus, hasData: Boolean) {
    val color = when {
        !hasData -> WidgetTheme.TextMuted
        status == AccountStatus.FAILED -> WidgetTheme.Danger
        status == AccountStatus.PASSED -> WidgetTheme.Success
        status == AccountStatus.ONGOING -> WidgetTheme.Accent
        status == AccountStatus.ACTIVE -> WidgetTheme.Success
        else -> WidgetTheme.TextMuted
    }
    Box(modifier = GlanceModifier.size(8.dp).background(color).cornerRadius(4.dp)) {}
}

@Composable
private fun TapRow(content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionRunCallback<RefreshAction>()),
    ) { content() }
}
