package com.basauri.ftmowidget.data

import kotlinx.serialization.Serializable

/**
 * Provider-neutral view of a prop-firm account. Every widget layout and the
 * refresh worker read this and nothing else, so adding a firm means writing a
 * [Provider] that produces one of these — not touching the UI.
 *
 * All monetary figures are plain amounts in [currency]; each provider's mapper
 * is responsible for undoing whatever encoding its API uses (FTMO ships
 * `{value, decimal}` envelopes, Blue Guardian ships bare floats).
 */
@Serializable
data class AccountSnapshot(
    val provider: ProviderId,
    /** Short identifier for the header, e.g. "#531303305" or "25k - Instant". */
    val accountLabel: String,
    /** Trading platform, when the provider reports one ("MT5", "MetaTrader5"). */
    val platform: String? = null,
    val currency: String? = null,
    val status: AccountStatus = AccountStatus.UNKNOWN,
    /** Raw provider status, shown when [status] is UNKNOWN. */
    val statusLabel: String? = null,

    val equity: Double,
    val balance: Double,
    val startingBalance: Double? = null,
    /** Today's P&L as the provider's own dashboard reports it. */
    val todaysProfit: Double? = null,

    val objectives: List<AccountObjective> = emptyList(),
    /** Chronological daily series; may be empty if the provider exposes none. */
    val days: List<DayPoint> = emptyList(),
    val stats: AccountStats = AccountStats(),

    val fetchedAtMillis: Long,
) {
    fun objective(kind: ObjectiveKind): AccountObjective? = objectives.firstOrNull { it.kind == kind }
}

@Serializable
enum class ProviderId { FTMO, BLUE_GUARDIAN }

@Serializable
enum class AccountStatus { ONGOING, ACTIVE, PASSED, FAILED, UNKNOWN }

@Serializable
enum class ObjectiveKind { PROFIT_TARGET, MAX_DAILY_LOSS, MAX_LOSS, MIN_TRADING_DAYS }

@Serializable
enum class ObjectiveStatus { IN_PROGRESS, PASSED, BREACHED, INELIGIBLE }

/**
 * One rule the account is measured against, already reduced to two comparable
 * numbers so the bars don't have to know which firm produced them.
 *
 * [limit] is always a positive magnitude. [current] is signed only for
 * [ObjectiveKind.PROFIT_TARGET] — where being below zero is the interesting
 * state and the UI draws it on a bidirectional scale. For the loss caps it is
 * the amount of buffer *consumed* (never negative: a profit consumes nothing),
 * and for [ObjectiveKind.MIN_TRADING_DAYS] it is a day count.
 */
@Serializable
data class AccountObjective(
    val kind: ObjectiveKind,
    val limit: Double,
    val current: Double,
    val status: ObjectiveStatus = ObjectiveStatus.IN_PROGRESS,
) {
    /** Signed for the profit target, 0..1+ for the loss caps. */
    val ratio: Double get() = if (limit == 0.0) 0.0 else current / limit

    /** Room left before the cap is hit. Meaningless for the profit target. */
    val remaining: Double get() = (limit - current).coerceAtLeast(0.0)
}

/**
 * One day of activity. Providers fill in what they have: FTMO reports realized
 * P&L with trade counts and lots but no end-of-day balance; Blue Guardian
 * reports balance and equity but no per-day trade breakdown.
 */
@Serializable
data class DayPoint(
    /** ISO `yyyy-MM-dd`, normalized by the mapper regardless of wire format. */
    val date: String,
    val pnl: Double? = null,
    val trades: Int? = null,
    val lots: Double? = null,
    val balance: Double? = null,
    val equity: Double? = null,
)

/**
 * Performance figures. Everything is nullable because coverage differs by
 * provider — Blue Guardian publishes no Sharpe ratio or lot volume — and the UI
 * drops cells it has no number for instead of printing a placeholder column.
 */
@Serializable
data class AccountStats(
    /** Already scaled to 0..100. */
    val winRatePct: Double? = null,
    val profitFactor: Double? = null,
    val expectancy: Double? = null,
    val sharpe: Double? = null,
    val avgRiskReward: Double? = null,
    val avgWin: Double? = null,
    val avgLoss: Double? = null,
    val lots: Double? = null,
    val tradesCount: Int? = null,
    val openTradesCount: Int? = null,
)
