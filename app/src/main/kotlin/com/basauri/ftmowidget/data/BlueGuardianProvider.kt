package com.basauri.ftmowidget.data

import kotlin.math.abs

/**
 * Blue Guardian's public share.
 *
 * The share link carries a single MongoDB ObjectId, which is the account
 * document's own `_id` — so the identity token is just that string.
 */
object BlueGuardianProvider : Provider {

    override val id = ProviderId.BLUE_GUARDIAN
    override val displayName = "Blue Guardian"

    private val client by lazy { BlueGuardianClient() }

    /**
     * A bare 24-hex run. The lookarounds keep it from matching a slice of a
     * longer hex string; FTMO's hyphenated UUID has no unbroken 24-hex run, so
     * the two patterns can't collide.
     */
    private val tokenRegex = Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{24}(?![0-9a-fA-F])")

    override fun parse(input: String): Identity? {
        val match = tokenRegex.find(input.trim()) ?: return null
        return Identity(id, match.value.lowercase())
    }

    override fun shareUrl(identity: Identity): String =
        "https://trader.blueguardian.com/shared/${identity.token}"

    override suspend fun fetch(identity: Identity): AccountSnapshot {
        val shared = client.fetchShared(identity.token)
        // The curve is a nice-to-have: a failure there shouldn't blank a widget
        // that could still show equity and objectives.
        val growth = runCatching { client.fetchDailyGrowth(identity.token) }.getOrDefault(emptyList())
        return shared.toSnapshot(growth)
    }
}

/**
 * Maps the shared-stats payload onto the neutral model.
 *
 * Blue Guardian publishes objective *thresholds*, not progress, so the
 * current-vs-limit figures are derived here exactly the way the site's own
 * client does it (see blueguardian-analysis.md). Getting this wrong would show
 * a plausible but incorrect bar, so the arithmetic is kept literal rather than
 * folded into something clever.
 */
fun BgSharedResponse.toSnapshot(
    growth: List<BgGrowthPoint> = emptyList(),
    fetchedAtMillis: Long = System.currentTimeMillis(),
): AccountSnapshot {
    val s = statistics
    val details = account.programDetails
    val isFunded = details.profitTargetPercentage == 0.0

    val objectives = buildList {
        // Funded accounts carry no profit target; the site hides the bar too.
        val targetLevel = s.profitTargetRequiredPnLLevel ?: 0.0
        if (!isFunded && targetLevel != 0.0) {
            val profit = s.currentProfit ?: 0.0
            add(
                AccountObjective(
                    kind = ObjectiveKind.PROFIT_TARGET,
                    limit = abs(targetLevel),
                    current = profit,
                    status = if (profit > 0 && profit >= targetLevel) ObjectiveStatus.PASSED
                    else ObjectiveStatus.IN_PROGRESS,
                )
            )
        }

        s.maxDailyLossLimitPnLLevel?.takeIf { it != 0.0 }?.let { level ->
            val today = s.dailyTotalPnL ?: 0.0
            add(
                AccountObjective(
                    kind = ObjectiveKind.MAX_DAILY_LOSS,
                    limit = abs(level),
                    current = if (today < 0) abs(today) else 0.0,
                    status = if (today < 0 && today <= level) ObjectiveStatus.BREACHED
                    else ObjectiveStatus.IN_PROGRESS,
                )
            )
        }

        s.maxLossLimitPnLLevel?.takeIf { it != 0.0 }?.let { level ->
            // Static max loss measures the worst equity dip from the starting
            // balance; the default trailing mode measures against the trailing
            // level the server already computed.
            val consumed = if (details.isStaticMaxLossEnabled) {
                ((s.startingBalance) - (s.lowestEquity ?: s.startingBalance)).coerceAtLeast(0.0)
            } else {
                val trailing = s.unrealizedPLFromMLLTSR ?: 0.0
                if (trailing > 0) 0.0 else abs(trailing)
            }
            val profit = s.currentProfit ?: 0.0
            add(
                AccountObjective(
                    kind = ObjectiveKind.MAX_LOSS,
                    limit = abs(level),
                    current = consumed,
                    status = if (profit < 0 && profit <= level) ObjectiveStatus.BREACHED
                    else ObjectiveStatus.IN_PROGRESS,
                )
            )
        }

        s.minTradingDays?.takeIf { it > 0 }?.let { required ->
            // After a payout on a funded account the counter restarts, so the
            // relevant figure is days since the last withdrawal.
            val done = if (isFunded && payout?.haveRecentWithdrawal == true) {
                s.activeTradingDaysSinceLastPayout ?: 0
            } else {
                s.activeTradingDays ?: 0
            }
            add(
                AccountObjective(
                    kind = ObjectiveKind.MIN_TRADING_DAYS,
                    limit = required.toDouble(),
                    current = done.toDouble(),
                    status = if (done >= required) ObjectiveStatus.PASSED else ObjectiveStatus.IN_PROGRESS,
                )
            )
        }
    }

    return AccountSnapshot(
        provider = ProviderId.BLUE_GUARDIAN,
        accountLabel = account.label ?: account.description ?: "Blue Guardian",
        platform = account.platform,
        currency = account.programCurrency,
        status = bgStatus(account.status),
        statusLabel = account.status,
        equity = s.currentEquity,
        balance = s.currentBalance,
        startingBalance = s.startingBalance.takeIf { it != 0.0 },
        todaysProfit = s.dailyTotalPnL,
        objectives = objectives,
        days = growth.toDayPoints(),
        stats = AccountStats(
            winRatePct = s.winRate,
            profitFactor = s.profitFactor,
            expectancy = s.expectancy,
            // No Sharpe ratio or lot volume in this API; left null so the UI
            // drops those cells rather than printing empty columns.
            avgRiskReward = s.averageRRR,
            avgWin = s.averageWin,
            avgLoss = s.averageLoss,
            tradesCount = s.trades,
        ),
        fetchedAtMillis = fetchedAtMillis,
    )
}

/**
 * Collapses the six multiplexed growth series into one point per day.
 *
 * There is no per-day P&L in this API, so it is derived by differencing
 * consecutive end-of-day balances — which makes the cumulative sum equal
 * `balance - startingBalance`, the same quantity the FTMO sparkline plots.
 */
private fun List<BgGrowthPoint>.toDayPoints(): List<DayPoint> {
    if (isEmpty()) return emptyList()
    val byDate = LinkedHashMap<String, MutableMap<String, Double>>()
    for (p in this) {
        val iso = isoDate(p.date) ?: continue
        byDate.getOrPut(iso) { mutableMapOf() }[p.name.uppercase()] = p.value
    }
    // The endpoint does not return points in chronological order.
    val ordered = byDate.entries.sortedBy { it.key }
    var previousBalance: Double? = null
    return ordered.map { (date, series) ->
        val balance = series["BALANCE"]
        val pnl = if (balance != null && previousBalance != null) balance - previousBalance!! else null
        if (balance != null) previousBalance = balance
        DayPoint(
            date = date,
            pnl = pnl,
            balance = balance,
            equity = series["EQUITY"],
        )
    }
}

/** Growth dates arrive as `DD-MM-YYYY`, not ISO; normalize or drop. */
private fun isoDate(raw: String): String? {
    val parts = raw.split('-')
    if (parts.size != 3) return null
    val (d, m, y) = parts
    if (y.length != 4 || d.length !in 1..2 || m.length !in 1..2) return null
    if (!y.all(Char::isDigit) || !d.all(Char::isDigit) || !m.all(Char::isDigit)) return null
    return "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
}

private fun bgStatus(raw: String?): AccountStatus = when (raw?.uppercase()) {
    "ACTIVE" -> AccountStatus.ACTIVE
    "PASSED", "APPROVED", "UPGRADED" -> AccountStatus.PASSED
    "BREACHED", "REJECTED" -> AccountStatus.FAILED
    else -> AccountStatus.UNKNOWN
}
