package com.basauri.ftmowidget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Public MetriX endpoint envelope:
 *   GET https://gw2.ftmo.com/public-api/v1/metrix/{login}/{sharingCode}
 *
 * Only fields the widget actually renders are kept; unknown JSON keys are ignored
 * by the Json instance configured in FtmoClient.
 */
@Serializable
data class MetrixResponse(
    val login: Long,
    val platform: String? = null,
    val region: String? = null,
    val currency: String? = null,
    val timestamp: String? = null,
    val consistencyScore: Score? = null,
    val disciplineScore: Score? = null,
    val tradingDays: List<String> = emptyList(),
    val info: Info,
    val statistics: Statistics,
    val objectives: Objectives,
    val dailySummary: List<DailyEntry> = emptyList(),
    val bestDayRuleDetails: BestDayRuleDetails? = null,
)

@Serializable
data class Money(
    val value: Long,
    val decimal: Int = 2,
    val currency: String? = null,
) {
    val amount: Double get() = value.toDouble() / pow10(decimal)
    companion object {
        private fun pow10(n: Int): Double {
            var r = 1.0
            repeat(n) { r *= 10 }
            return r
        }
    }
}

@Serializable
data class Score(
    val value: Double,
    val type: String? = null,
)

@Serializable
data class Info(
    val accountStatus: String? = null,
    val accountResult: String? = null,
    val accountStageType: String? = null,
    val accountProductType: String? = null,
    val productLine: String? = null,
    val accountStart: String? = null,
    val accountEnd: String? = null,
    val isUnlimited: Boolean = false,
    val initialBalance: Money? = null,
    val minAccountEquityLimit: Money? = null,
    val minTodaysEquityLimit: Money? = null,
    val maxMidnightBalance: Money? = null,
    val todaysProfit: Money? = null,
    val todaysRealizedProfit: Money? = null,
)

@Serializable
data class Statistics(
    val equity: Money,
    val balance: Money,
    val winRate: Score? = null,
    val avgProfit: Money? = null,
    val avgLoss: Money? = null,
    val lots: Double = 0.0,
    val avgRiskToRewardRate: Double = 0.0,
    val expectancy: Money? = null,
    val sharpeRate: Double = 0.0,
    val profitFactor: Double = 0.0,
    val openTradesCount: Int = 0,
    val tradesCount: Int = 0,
    val totalTradesCount: Int = 0,
)

@Serializable
data class Objectives(
    val profit: Objective? = null,
    val maxLoss: Objective? = null,
    val maxDailyLoss: Objective? = null,
    val bestDayRule: Objective? = null,
    val maxMidnightBalanceMaxLoss: Objective? = null,
    val minTradingDays: Objective? = null,
)

@Serializable
data class Objective(
    val limit: ObjectiveValue? = null,
    val result: ObjectiveValue? = null,
    val percentage: Score? = null,
    val status: String? = null,
)

/** Limits/results can be either a Money envelope or a fraction Score; tolerate both. */
@Serializable
data class ObjectiveValue(
    val value: Double? = null,
    val decimal: Int? = null,
    val currency: String? = null,
    val type: String? = null,
) {
    val amount: Double?
        get() = value?.let { v ->
            val d = decimal ?: 0
            v / pow10(d)
        }
    val isFraction: Boolean get() = type == "fraction"
    companion object {
        private fun pow10(n: Int): Double {
            var r = 1.0
            repeat(n) { r *= 10 }
            return r
        }
    }
}

/**
 * The realized P&L the user thinks of as "today". info.todaysProfit rolls over at
 * FTMO server midnight (reading 0 on a fresh server day), so prefer the most recent
 * daily-summary entry — the figure shown on the FTMO dashboard — and fall back to
 * the info fields only when the summary is empty.
 */
val MetrixResponse.todayPnl: Money?
    get() = dailySummary.maxByOrNull { it.date }?.realizedProfit
        ?: info.todaysProfit
        ?: info.todaysRealizedProfit

/** True when the objective carries usable numeric limit and result. */
val Objective.hasData: Boolean
    get() = limit?.amount != null && result?.amount != null

/**
 * Signed result/limit ratio. For the profit target this is the fraction of the
 * target reached (negative while equity is below the starting balance); for the
 * loss caps it is the fraction of the buffer consumed (positive, since result and
 * limit share the same sign). This is the correct basis for the progress bars —
 * the API's `percentage` field is loss/gain as a percent of the initial balance,
 * not progress toward the objective.
 */
val Objective.progressRatio: Double
    get() {
        val l = limit?.amount ?: return 0.0
        val r = result?.amount ?: return 0.0
        return if (l == 0.0) 0.0 else r / l
    }

/**
 * FTMO exposes the overall loss cap either as `maxLoss` or, when that is
 * "ineligible" for the account type, as `maxMidnightBalanceMaxLoss`. Pick
 * whichever actually carries data.
 */
val Objectives.overallMaxLoss: Objective?
    get() = maxLoss?.takeIf { it.hasData } ?: maxMidnightBalanceMaxLoss

@Serializable
data class DailyEntry(
    val date: String,
    val lots: Double = 0.0,
    val tradesCount: Int = 0,
    val realizedProfit: Money,
)

@Serializable
data class BestDayRuleDetails(
    val bestDay: Money? = null,
    val totalProfit: Money? = null,
    val limit: Money? = null,
    val tradingDays: Int = 0,
)

/**
 * Balance curve endpoint envelope:
 *   GET https://gw2.ftmo.com/public-api/v1/account/{login}/{sharingCode}/balance-curve
 *
 * Kept for completeness; the v1 widget does not draw the curve yet.
 */
@Serializable
data class BalanceCurveResponse(
    val login: Long,
    val currency: String? = null,
    val balanceCurve: BalanceCurve,
)

@Serializable
data class BalanceCurve(
    val balance: List<Double> = emptyList(),
    val time: List<String> = emptyList(),
    val ticket: List<String> = emptyList(),
)

@Serializable
data class WidgetSnapshot(
    val login: Long,
    val sharingCode: String,
    val fetchedAtMillis: Long,
    val metrix: MetrixResponse,
)
