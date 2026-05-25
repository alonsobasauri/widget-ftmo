package com.basauri.ftmowidget.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.config.ConfigActivity
import com.basauri.ftmowidget.data.Format
import com.basauri.ftmowidget.data.Money
import com.basauri.ftmowidget.data.Objective
import com.basauri.ftmowidget.data.WidgetSnapshot
import com.basauri.ftmowidget.data.progressRatio

@Composable
fun UnconfiguredCard() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(Intent(context, ConfigActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.widget_label),
            style = WidgetTheme.titleStyle(),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_tap_to_configure),
            style = WidgetTheme.primaryStyle(),
        )
    }
}

@Composable
fun LoadingCard() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.widget_loading),
            style = WidgetTheme.secondaryStyle(),
        )
    }
}

@Composable
fun ErrorCard(message: String) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RefreshAction>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.widget_error),
            style = TextStyle(
                color = ColorProvider(WidgetTheme.Danger),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = message.take(80),
            style = WidgetTheme.secondaryStyle(),
            maxLines = 3,
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_retry),
            style = TextStyle(
                color = ColorProvider(WidgetTheme.Accent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
fun StatusBadge(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val info = snapshot.metrix.info
    val (label, color) = when {
        info.accountStatus.equals("active", ignoreCase = true) &&
            info.accountResult.equals("ongoing", ignoreCase = true) ->
            context.getString(R.string.widget_status_ongoing) to WidgetTheme.Accent
        info.accountResult.equals("passed", ignoreCase = true) ->
            context.getString(R.string.widget_status_passed) to WidgetTheme.Success
        info.accountResult.equals("failed", ignoreCase = true) ->
            context.getString(R.string.widget_status_failed) to WidgetTheme.Danger
        info.accountStatus.equals("active", ignoreCase = true) ->
            context.getString(R.string.widget_status_active) to WidgetTheme.Success
        else -> (info.accountStatus ?: "—") to WidgetTheme.TextMuted
    }
    Box(
        modifier = GlanceModifier
            .background(color)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/**
 * A flat horizontal bar with a fill segment scaled to [progress] (0.0..1.0+).
 * Width given by [trackWidth]; fill width is clamped to the track width.
 */
@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    trackWidth: androidx.compose.ui.unit.Dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val fillWidth = (trackWidth.value * clamped).dp
    Box(
        modifier = GlanceModifier
            .width(trackWidth)
            .height(6.dp)
            .background(WidgetTheme.SurfaceMuted)
            .cornerRadius(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (clamped > 0f) {
            Box(
                modifier = GlanceModifier
                    .width(fillWidth)
                    .height(6.dp)
                    .background(color)
                    .cornerRadius(4.dp),
            ) {}
        }
    }
}

@Composable
fun MetricLabelValue(label: String, value: String, valueColor: Color = WidgetTheme.TextPrimary) {
    Column {
        Text(text = label, style = WidgetTheme.titleStyle())
        Spacer(GlanceModifier.height(2.dp))
        Text(text = value, style = WidgetTheme.valueStyle(valueColor))
    }
}

@Composable
fun ObjectiveRow(
    label: String,
    objective: Objective?,
    currency: String?,
    trackWidth: androidx.compose.ui.unit.Dp,
) {
    val pct = objective?.progressRatio ?: 0.0
    val color = when (objective?.status?.lowercase()) {
        "passed" -> WidgetTheme.Success
        "notpassed", "not_passed" -> WidgetTheme.Danger
        "ineligible" -> WidgetTheme.TextMuted
        else -> WidgetTheme.Warning
    }
    val resultMoney = objective?.result?.amount
    val limitMoney = objective?.limit?.amount
    val resultText = com.basauri.ftmowidget.data.Format.money(resultMoney, currency, withSign = true)
    val limitText = com.basauri.ftmowidget.data.Format.money(limitMoney, currency)

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShadowText(text = label, maxLines = 1, style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            ShadowText(
                text = "$resultText / $limitText",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextSecondary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        ProgressBar(
            progress = kotlin.math.abs(pct).toFloat(),
            color = color,
            trackWidth = trackWidth,
        )
    }
}

/**
 * Small accent-colored dot rendered while a refresh is in flight. Sized so it
 * fits inside the header row of every widget variant without pushing other
 * children around.
 */
@Composable
fun RefreshDot() {
    Box(
        modifier = GlanceModifier
            .size(8.dp)
            .background(WidgetTheme.Accent)
            .cornerRadius(4.dp),
    ) {}
}

/**
 * Explicit, always-visible refresh control. Tapping it runs RefreshAction. Gives a
 * clear affordance in the XL layout, where the scrollable list makes tap-to-refresh
 * on individual rows feel like row selection.
 */
@Composable
fun RefreshButton(refreshing: Boolean) {
    Box(
        modifier = GlanceModifier
            .clickable(actionRunCallback<RefreshAction>())
            .cornerRadius(8.dp)
            .background(WidgetTheme.SurfaceMuted)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (refreshing) "↻…" else "↻",
            style = TextStyle(
                color = ColorProvider(if (refreshing) WidgetTheme.TextMuted else WidgetTheme.Accent),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Small all-caps section header for the XL layout. */
@Composable
fun SectionTitle(text: String) {
    ShadowText(
        text = text.uppercase(),
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(WidgetTheme.TextMuted),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

/**
 * Bidirectional track: a center divider with red filling from center leftward when
 * progress is negative (below profit target) and green filling rightward when
 * progress is positive. Makes the "I'm below zero" state obvious at a glance,
 * which the old unidirectional bar made misleading.
 */
@Composable
fun BidirectionalScale(pct: Float, trackHalfWidth: androidx.compose.ui.unit.Dp = 120.dp) {
    val left = if (pct < 0f) (-pct).coerceAtMost(1f) else 0f
    val right = if (pct > 0f) pct.coerceAtMost(1f) else 0f
    Row(modifier = GlanceModifier.height(6.dp)) {
        Box(
            modifier = GlanceModifier
                .width(trackHalfWidth)
                .height(6.dp)
                .background(WidgetTheme.SurfaceMuted)
                .cornerRadius(3.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (left > 0f) {
                Box(
                    modifier = GlanceModifier
                        .width((trackHalfWidth.value * left).dp)
                        .height(6.dp)
                        .background(WidgetTheme.Danger)
                        .cornerRadius(3.dp),
                ) {}
            }
        }
        Spacer(GlanceModifier.width(2.dp))
        Box(
            modifier = GlanceModifier
                .width(trackHalfWidth)
                .height(6.dp)
                .background(WidgetTheme.SurfaceMuted)
                .cornerRadius(3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (right > 0f) {
                Box(
                    modifier = GlanceModifier
                        .width((trackHalfWidth.value * right).dp)
                        .height(6.dp)
                        .background(WidgetTheme.Success)
                        .cornerRadius(3.dp),
                ) {}
            }
        }
    }
}

/**
 * Profit target row: header text + bidirectional bar so the user can tell at a
 * glance whether they are above or below zero relative to the target.
 */
@Composable
fun ProfitTargetRow(objective: Objective?, currency: String?, trackHalfWidth: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val ratio = objective?.progressRatio ?: 0.0
    val resultText = Format.money(objective?.result?.amount, currency, withSign = true)
    val limitText = Format.money(objective?.limit?.amount, currency)
    val pctText = "  ${if (ratio > 0) "+" else ""}${(ratio * 100).toInt()}%"
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShadowText(text = context.getString(R.string.widget_profit_target), maxLines = 1, style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            ShadowText(
                text = "$resultText / $limitText$pctText",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextSecondary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        BidirectionalScale(pct = ratio.toFloat(), trackHalfWidth = trackHalfWidth)
    }
}

/**
 * Buffer bar: the bar shows how much of the loss limit is consumed (green → amber
 * → red as it fills), and the figure shows how much room is *left* — the number a
 * trader actually acts on — instead of restating the loss already shown elsewhere.
 * Only a result that shares the limit's sign (an actual loss) consumes the buffer;
 * a profit leaves it full.
 */
@Composable
fun BufferBarRow(
    label: String,
    resultAmount: Double?,
    limitAmount: Double?,
    currency: String?,
    trackWidth: androidx.compose.ui.unit.Dp,
) {
    val limitAbs = limitAmount?.let { kotlin.math.abs(it) }
    val consumedAbs = if (resultAmount != null && limitAmount != null && resultAmount * limitAmount > 0.0) {
        kotlin.math.abs(resultAmount)
    } else 0.0
    val pctAbs = if (limitAbs != null && limitAbs != 0.0) {
        (consumedAbs / limitAbs).toFloat().coerceIn(0f, 1f)
    } else 0f
    val remaining = limitAbs?.let { (it - consumedAbs).coerceAtLeast(0.0) }
    val color = when {
        pctAbs < 0.6f -> WidgetTheme.Success
        pctAbs < 0.85f -> WidgetTheme.Warning
        else -> WidgetTheme.Danger
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ShadowText(text = label, maxLines = 1, style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            ShadowText(
                text = "${Format.moneyWhole(remaining, currency)} / ${Format.moneyWhole(limitAbs, currency)} left",
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextSecondary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        ProgressBar(progress = pctAbs, color = color, trackWidth = trackWidth)
    }
}

/** Buffer bar driven straight from an FTMO objective's result/limit. */
@Composable
fun BufferRow(
    label: String,
    objective: Objective?,
    currency: String?,
    trackWidth: androidx.compose.ui.unit.Dp,
) = BufferBarRow(label, objective?.result?.amount, objective?.limit?.amount, currency, trackWidth)

data class StatCell(val label: String, val value: String, val warn: Boolean = false)

/** Single cell in the performance grid; value/label font sizes are tunable so the
 *  primary stats can read larger than the secondary ones. */
@Composable
fun StatTile(
    cell: StatCell,
    valueSize: Int = 13,
    labelSize: Int = 10,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        ShadowText(
            text = cell.label,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TextMuted),
                fontSize = labelSize.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        ShadowText(
            text = cell.value,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(if (cell.warn) WidgetTheme.Warning else WidgetTheme.TextPrimary),
                fontSize = valueSize.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** A row of equal-width stat tiles. */
@Composable
fun PerfRowN(cells: List<StatCell>, valueSize: Int = 13, labelSize: Int = 10) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        cells.forEach { StatTile(it, valueSize, labelSize, GlanceModifier.defaultWeight()) }
    }
}

/**
 * Bottom-aligned bar sparkline of the cumulative realized P&L across the supplied
 * days, so the recent trajectory ("climbing back" vs "sinking") reads at a glance —
 * a different view from the per-day list, not a restatement of it. Each bar is
 * coloured by whether the running total is in the green or the red at that point.
 */
@Composable
fun PnlSparkline(daily: List<com.basauri.ftmowidget.data.DailyEntry>, height: Int = 34) {
    val chrono = daily.sortedBy { it.date }.takeLast(16)
    if (chrono.size < 2) return
    var run = 0.0
    val cum = chrono.map { run += it.realizedProfit.amount; run }
    val min = cum.minOrNull() ?: 0.0
    val max = cum.maxOrNull() ?: 0.0
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        cum.forEachIndexed { i, v ->
            val h = (3.0 + (v - min) / span * (height - 3)).toInt().coerceIn(2, height)
            val color = if (v >= 0.0) WidgetTheme.Success else WidgetTheme.Danger
            Box(modifier = GlanceModifier.defaultWeight().height(h.dp).background(color)) {}
            if (i < cum.size - 1) Spacer(GlanceModifier.width(2.dp))
        }
    }
}

/**
 * Glance has no text shadow/outline support, so we fake a drop shadow by drawing a
 * dark copy of the text offset by 1dp underneath the real one. Keeps text legible
 * when the background opacity is low and the wallpaper shows through.
 */
@Composable
fun ShadowText(
    text: String,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    modifier: GlanceModifier = GlanceModifier,
) {
    val shadowStyle = TextStyle(
        color = ColorProvider(Color(0xB3000000)),
        fontSize = style.fontSize,
        fontWeight = style.fontWeight,
        fontStyle = style.fontStyle,
        textAlign = style.textAlign,
        textDecoration = style.textDecoration,
    )
    Box(modifier = modifier) {
        Text(
            text = text,
            style = shadowStyle,
            maxLines = maxLines,
            modifier = GlanceModifier.padding(start = 1.dp, top = 1.dp),
        )
        Text(text = text, style = style, maxLines = maxLines)
    }
}

@Composable
fun MoneyText(money: Money?, fontSizeSp: Int, withSign: Boolean = false) {
    val amount = money?.amount ?: 0.0
    ShadowText(
        text = com.basauri.ftmowidget.data.Format.money(money, withSign = withSign),
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(WidgetTheme.colorForAmount(amount)),
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}
