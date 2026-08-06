package com.basauri.ftmowidget.data

import kotlinx.serialization.Serializable

/**
 * Blue Guardian's public share endpoints:
 *   GET  https://api.trader.blueguardian.com/v1/accounts/shared/{id}
 *   POST https://api.trader.blueguardian.com/v1/accounts/shared/{id}/growth
 *
 * Unlike FTMO, every amount is a plain float in `account.programCurrency` and
 * every percentage is already on a 0..100 scale — there is nothing to descale.
 * See blueguardian-analysis.md for the full field survey; only what the widget
 * renders is modelled here.
 */
@Serializable
data class BgSharedResponse(
    val statistics: BgStatistics = BgStatistics(),
    val account: BgAccount = BgAccount(),
    val payout: BgPayout? = null,
)

@Serializable
data class BgStatistics(
    val currentBalance: Double = 0.0,
    val currentEquity: Double = 0.0,
    val startingBalance: Double = 0.0,

    /**
     * Equity now minus equity at the previous day's close. This is what the Blue
     * Guardian banner shows, and — importantly — the basis the daily loss limit
     * is measured against, so the objective keeps using it.
     */
    val dailyTotalPnL: Double? = null,
    /** Closed-trade P&L since the broker's day roll. */
    val dailyTotalRealizedPnL: Double? = null,
    /** Cumulative P&L against the starting balance. */
    val currentProfit: Double? = null,

    // Objective levels. The API publishes thresholds, not progress; the mapper
    // derives current-vs-limit from them the way the site's own client does.
    val maxDailyLossLimitPnLLevel: Double? = null,
    val maxLossLimitPnLLevel: Double? = null,
    val profitTargetRequiredPnLLevel: Double? = null,
    /** P&L measured against the trailing max-loss level. */
    val unrealizedPLFromMLLTSR: Double? = null,
    val lowestEquity: Double? = null,
    val minTradingDays: Int? = null,
    val activeTradingDays: Int? = null,
    val activeTradingDaysSinceLastPayout: Int? = null,

    val winRate: Double? = null,
    val profitFactor: Double? = null,
    val expectancy: Double? = null,
    val averageRRR: Double? = null,
    val averageWin: Double? = null,
    val averageLoss: Double? = null,
    val trades: Int? = null,
)

@Serializable
data class BgAccount(
    val name: String? = null,
    val label: String? = null,
    val description: String? = null,
    val status: String? = null,
    val platform: String? = null,
    val programCurrency: String? = null,
    val haveOpenTrades: Boolean = false,
    val programDetails: BgProgramDetails = BgProgramDetails(),
)

@Serializable
data class BgProgramDetails(
    /** Zero on funded accounts, which have no profit target to hit. */
    val profitTargetPercentage: Double = 0.0,
    /** When set, overall loss is measured from the starting balance rather than trailing. */
    val isStaticMaxLossEnabled: Boolean = false,
    val programName: String? = null,
    val fundingBalance: Double? = null,
)

@Serializable
data class BgPayout(
    val haveRecentWithdrawal: Boolean = false,
)

/**
 * One point of one series from the growth endpoint. The response multiplexes six
 * series into a flat array, keyed by [name], and is **not** sorted by date.
 */
@Serializable
data class BgGrowthPoint(
    val date: String,
    val value: Double = 0.0,
    val name: String = "",
)
