package com.basauri.ftmowidget.data

/**
 * FTMO's public MetriX share.
 *
 * Identity token packs the two path segments the API needs as `login:sharingCode`.
 */
object FtmoProvider : Provider {

    override val id = ProviderId.FTMO
    override val displayName = "FTMO"

    private val client by lazy { FtmoClient() }

    private val shareRegex = Regex(
        "(?<login>\\d{4,})\\D+(?<code>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    )

    override fun parse(input: String): Identity? {
        val match = shareRegex.find(input.trim()) ?: return null
        val login = match.groups["login"]?.value?.toLongOrNull() ?: return null
        val code = match.groups["code"]?.value?.lowercase() ?: return null
        return Identity(id, "$login:$code")
    }

    override fun shareUrl(identity: Identity): String {
        val (login, code) = split(identity) ?: return ""
        return "https://trader.ftmo.com/live-metrix/$login/share/$code"
    }

    override suspend fun fetch(identity: Identity): AccountSnapshot {
        val (login, code) = split(identity)
            ?: throw IllegalArgumentException("Malformed FTMO token")
        return client.fetchMetrix(login, code).toSnapshot()
    }

    private fun split(identity: Identity): Pair<Long, String>? {
        val parts = identity.token.split(':', limit = 2)
        if (parts.size != 2) return null
        val login = parts[0].toLongOrNull() ?: return null
        return login to parts[1]
    }
}

/**
 * Maps the MetriX envelope onto the neutral model: money envelopes are
 * flattened to plain amounts, and the two loss objectives are converted from
 * FTMO's signed result/limit pairs into "buffer consumed" figures.
 */
fun MetrixResponse.toSnapshot(fetchedAtMillis: Long = System.currentTimeMillis()): AccountSnapshot {
    val objectives = buildList {
        objectives.profit?.let { obj ->
            val limit = obj.limit?.amount
            // Funded accounts report an ineligible/zero profit target; there is
            // no progress to draw, so leave it out entirely.
            if (limit != null && limit != 0.0 && obj.status?.lowercase() != "ineligible") {
                add(
                    AccountObjective(
                        kind = ObjectiveKind.PROFIT_TARGET,
                        limit = kotlin.math.abs(limit),
                        current = obj.result?.amount ?: 0.0,
                        status = obj.status.toObjectiveStatus(),
                    )
                )
            }
        }
        objectives.maxDailyLoss?.let { obj ->
            // The daily cap is measured against today's realized P&L, which
            // matches what the FTMO dashboard shows; the objective's own result
            // lags behind the day roll-over.
            buffer(ObjectiveKind.MAX_DAILY_LOSS, todayPnl?.amount, obj.limit?.amount, obj.status)?.let(::add)
        }
        objectives.overallMaxLoss?.let { obj ->
            buffer(ObjectiveKind.MAX_LOSS, obj.result?.amount, obj.limit?.amount, obj.status)?.let(::add)
        }
        objectives.minTradingDays?.let { obj ->
            val limit = obj.limit?.amount
            if (limit != null && limit > 0.0) {
                add(
                    AccountObjective(
                        kind = ObjectiveKind.MIN_TRADING_DAYS,
                        limit = limit,
                        current = obj.result?.amount ?: 0.0,
                        status = obj.status.toObjectiveStatus(),
                    )
                )
            }
        }
    }

    val currency = currency ?: statistics.equity.currency

    return AccountSnapshot(
        provider = ProviderId.FTMO,
        accountLabel = "#$login",
        platform = platform,
        currency = currency,
        status = ftmoStatus(info.accountStatus, info.accountResult),
        statusLabel = info.accountResult ?: info.accountStatus,
        equity = statistics.equity.amount,
        balance = statistics.balance.amount,
        startingBalance = info.initialBalance?.amount,
        todaysProfit = todaysProfit(
            // FTMO's daily summary is realized-only, so the floating leg has to
            // come from the equity/balance gap, same as for Blue Guardian.
            realizedToday = todayPnl?.amount,
            equity = statistics.equity.amount,
            balance = statistics.balance.amount,
        ),
        objectives = objectives,
        days = dailySummary
            .sortedBy { it.date }
            .map { d ->
                DayPoint(
                    date = d.date.take(10),
                    pnl = d.realizedProfit.amount,
                    trades = d.tradesCount,
                    lots = d.lots,
                )
            },
        stats = AccountStats(
            winRatePct = statistics.winRate?.let { normalizedWinRate(it) },
            profitFactor = statistics.profitFactor,
            expectancy = statistics.expectancy?.amount,
            sharpe = statistics.sharpeRate,
            avgRiskReward = statistics.avgRiskToRewardRate,
            avgWin = statistics.avgProfit?.amount,
            avgLoss = statistics.avgLoss?.amount,
            lots = statistics.lots,
            tradesCount = statistics.tradesCount,
            openTradesCount = statistics.openTradesCount,
        ),
        fetchedAtMillis = fetchedAtMillis,
    )
}

/**
 * Turns a signed result/limit pair into consumed-vs-limit. Only a result that
 * shares the limit's sign is an actual loss eating into the buffer; a profit
 * leaves it untouched.
 */
private fun buffer(
    kind: ObjectiveKind,
    result: Double?,
    limit: Double?,
    status: String?,
): AccountObjective? {
    if (limit == null || limit == 0.0) return null
    val consumed = if (result != null && result * limit > 0.0) kotlin.math.abs(result) else 0.0
    return AccountObjective(
        kind = kind,
        limit = kotlin.math.abs(limit),
        current = consumed,
        status = status.toObjectiveStatus(),
    )
}

private fun String?.toObjectiveStatus(): ObjectiveStatus = when (this?.lowercase()) {
    "passed" -> ObjectiveStatus.PASSED
    "notpassed", "not_passed", "failed", "breached" -> ObjectiveStatus.BREACHED
    "ineligible" -> ObjectiveStatus.INELIGIBLE
    else -> ObjectiveStatus.IN_PROGRESS
}

private fun ftmoStatus(accountStatus: String?, accountResult: String?): AccountStatus = when {
    accountResult.equals("passed", ignoreCase = true) -> AccountStatus.PASSED
    accountResult.equals("failed", ignoreCase = true) -> AccountStatus.FAILED
    accountStatus.equals("active", ignoreCase = true) &&
        accountResult.equals("ongoing", ignoreCase = true) -> AccountStatus.ONGOING
    accountStatus.equals("active", ignoreCase = true) -> AccountStatus.ACTIVE
    else -> AccountStatus.UNKNOWN
}

/**
 * Win rate is bounded [0,100]%, but FTMO sometimes tags it `type="fraction"`
 * while sending an already-percent value (e.g. 29.03), which naive scaling would
 * blow up to 2903%. Treat <=1 as a real 0..1 fraction, anything larger as an
 * already-percent value that may have been over-scaled.
 */
private fun normalizedWinRate(score: Score): Double {
    var pct = score.value
    if (pct <= 1.0) pct *= 100.0
    while (pct > 100.0) pct /= 100.0
    return pct
}
