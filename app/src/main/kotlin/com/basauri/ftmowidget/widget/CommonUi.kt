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
    val pct = objective?.percentage?.let {
        if (it.type == "fraction") it.value else it.value / 100.0
    } ?: 0.0
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
            Text(text = label, style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "$resultText / $limitText",
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

/** Small all-caps section header for the XL layout. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
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
    val pct = objective?.percentage?.let {
        if (it.type == "fraction") it.value else it.value / 100.0
    } ?: 0.0
    val resultText = Format.money(objective?.result?.amount, currency, withSign = true)
    val limitText = Format.money(objective?.limit?.amount, currency)
    val pctText = if (pct != 0.0) {
        val sign = if (pct > 0) "+" else ""
        "  $sign${(pct * 100).toInt()}%"
    } else ""
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = context.getString(R.string.widget_profit_target), style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "$resultText / $limitText$pctText",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TextSecondary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        BidirectionalScale(pct = pct.toFloat(), trackHalfWidth = trackHalfWidth)
    }
}

/**
 * Buffer bar: shows how much of the loss limit has been consumed. Colour shifts
 * green → amber → red as usage approaches the limit. Used for "Max Daily Loss"
 * and the overall loss buffer where the FTMO objective is a cap, not a target.
 */
@Composable
fun BufferRow(
    label: String,
    objective: Objective?,
    currency: String?,
    trackWidth: androidx.compose.ui.unit.Dp,
) {
    val pct = objective?.percentage?.let {
        if (it.type == "fraction") it.value else it.value / 100.0
    } ?: 0.0
    val pctAbs = kotlin.math.abs(pct).toFloat().coerceIn(0f, 1f)
    val color = when {
        pctAbs < 0.6f -> WidgetTheme.Success
        pctAbs < 0.85f -> WidgetTheme.Warning
        else -> WidgetTheme.Danger
    }
    val resultText = Format.money(objective?.result?.amount, currency, withSign = true)
    val limitText = Format.money(objective?.limit?.amount, currency)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = WidgetTheme.titleStyle())
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "$resultText / $limitText  (${(pctAbs * 100).toInt()}%)",
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

data class StatCell(val label: String, val value: String, val warn: Boolean = false)

/** Single cell in the 3×3 performance grid. */
@Composable
fun StatTile(cell: StatCell, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        Text(
            text = cell.label,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TextMuted),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = cell.value,
            style = TextStyle(
                color = ColorProvider(if (cell.warn) WidgetTheme.Warning else WidgetTheme.TextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** One row in the 3×3 performance grid; each cell takes an equal share of the row. */
@Composable
fun PerfRow(a: StatCell, b: StatCell, c: StatCell) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        StatTile(a, modifier = GlanceModifier.defaultWeight())
        StatTile(b, modifier = GlanceModifier.defaultWeight())
        StatTile(c, modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
fun MoneyText(money: Money?, fontSizeSp: Int, withSign: Boolean = false) {
    val amount = money?.amount ?: 0.0
    Text(
        text = com.basauri.ftmowidget.data.Format.money(money, withSign = withSign),
        style = TextStyle(
            color = ColorProvider(WidgetTheme.colorForAmount(amount)),
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}
