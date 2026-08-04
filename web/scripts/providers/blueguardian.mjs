// Blue Guardian's public share. See blueguardian-analysis.md for the endpoint
// survey; the objective arithmetic here mirrors BlueGuardianProvider.kt, which
// in turn mirrors what the Blue Guardian site computes client-side.

const BASE_URL = "https://api.trader.blueguardian.com/v1";
// A bare 24-hex ObjectId. The lookarounds keep it from matching a slice of a
// longer hex run; FTMO's hyphenated UUID has no unbroken 24-hex sequence.
const TOKEN_RE = /(?<![0-9a-fA-F])[0-9a-fA-F]{24}(?![0-9a-fA-F])/;

export const id = "blueguardian";
export const displayName = "Blue Guardian";

export function parse(input) {
  const m = TOKEN_RE.exec((input || "").trim());
  if (!m) return null;
  return { provider: id, token: m[0].toLowerCase() };
}

export function shareUrl(identity) {
  return `https://trader.blueguardian.com/shared/${identity.token}`;
}

function num(x) {
  return typeof x === "number" && isFinite(x) ? x : null;
}

async function getShared(token) {
  const url = `${BASE_URL}/accounts/shared/${token}`;
  const res = await fetch(url, { headers: { Accept: "application/json" } });
  const body = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status} from shared · ${body.slice(0, 200)}`);
  return JSON.parse(body);
}

// The growth endpoint is a POST — a GET returns 404 — and the JSON content type
// is load-bearing: without it the body is ignored and every point comes back
// labelled with the year instead of its date.
async function getDailyGrowth(token) {
  const url = `${BASE_URL}/accounts/shared/${token}/growth`;
  const res = await fetch(url, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ filter: "daily" }),
  });
  const body = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status} from growth · ${body.slice(0, 200)}`);
  const parsed = JSON.parse(body);
  return Array.isArray(parsed) ? parsed : [];
}

// Growth dates arrive as DD-MM-YYYY, not ISO.
function isoDate(raw) {
  const parts = String(raw || "").split("-");
  if (parts.length !== 3) return null;
  const [d, m, y] = parts;
  if (y.length !== 4 || !/^\d+$/.test(d) || !/^\d+$/.test(m) || !/^\d+$/.test(y)) return null;
  return `${y}-${m.padStart(2, "0")}-${d.padStart(2, "0")}`;
}

/**
 * Collapses the six multiplexed growth series into one row per day. There is no
 * per-day P&L in this API, so it is derived by differencing consecutive
 * end-of-day balances.
 */
function toDays(growth) {
  const byDate = new Map();
  for (const p of growth) {
    const date = isoDate(p.date);
    if (!date) continue;
    if (!byDate.has(date)) byDate.set(date, {});
    byDate.get(date)[String(p.name || "").toUpperCase()] = p.value;
  }
  // The endpoint does not return points in chronological order.
  const ordered = [...byDate.entries()].sort((a, b) => (a[0] < b[0] ? -1 : 1));
  let prevBalance = null;
  return ordered.map(([date, series]) => {
    const balance = num(series.BALANCE);
    const pnl = balance != null && prevBalance != null ? balance - prevBalance : null;
    if (balance != null) prevBalance = balance;
    return {
      date,
      pnl: num(pnl),
      trades: null,
      lots: null,
      balance,
      equity: num(series.EQUITY),
    };
  });
}

export async function fetchSnapshot(identity) {
  const shared = await getShared(identity.token);
  // The curve is a nice-to-have; a failure there should not lose the snapshot.
  const growth = await getDailyGrowth(identity.token).catch(() => []);

  const s = shared.statistics || {};
  const account = shared.account || {};
  const details = account.programDetails || {};
  const payout = shared.payout || null;
  const isFunded = (details.profitTargetPercentage ?? 0) === 0;

  const days = toDays(growth);

  const dailyLossLimit = num(s.maxDailyLossLimitPnLLevel);
  const maxLossLimit = num(s.maxLossLimitPnLLevel);
  const targetLevel = num(s.profitTargetRequiredPnLLevel);

  const todayPnl = num(s.dailyTotalPnL);
  const dailyConsumed = todayPnl != null && todayPnl < 0 ? Math.abs(todayPnl) : 0;

  // Static max loss measures the worst equity dip from the starting balance;
  // the default trailing mode uses the level the server already computed.
  const trailing = num(s.unrealizedPLFromMLLTSR) ?? 0;
  const maxLossConsumed = details.isStaticMaxLossEnabled
    ? Math.max(0, (num(s.startingBalance) ?? 0) - (num(s.lowestEquity) ?? num(s.startingBalance) ?? 0))
    : trailing > 0
      ? 0
      : Math.abs(trailing);

  const activeDays =
    isFunded && payout?.haveRecentWithdrawal
      ? num(s.activeTradingDaysSinceLastPayout)
      : num(s.activeTradingDays);

  const pct = (used, limit) =>
    limit != null && Math.abs(limit) > 0 ? (used / Math.abs(limit)) * 100 : null;

  return {
    provider: id,
    metrics: {
      equity: num(s.currentEquity),
      balance: num(s.currentBalance),
      todayPnl,
      // Funded accounts have no target, so there is no progress to report.
      profitPct: !isFunded && targetLevel ? pct(num(s.currentProfit) ?? 0, targetLevel) : null,
      profitResult: num(s.currentProfit),
      maxLossUsedPct: pct(maxLossConsumed, maxLossLimit),
      maxDailyLossUsedPct: pct(dailyConsumed, dailyLossLimit),
      winRate: num(s.winRate),
      profitFactor: num(s.profitFactor),
      expectancy: num(s.expectancy),
      // Blue Guardian publishes no Sharpe ratio, lot volume, or consistency and
      // discipline scores; left null so the dashboard shows a gap, not a zero.
      sharpe: null,
      avgRR: num(s.averageRRR),
      lots: null,
      tradesCount: num(s.trades),
      consistency: null,
      discipline: null,
      tradingDaysCount: activeDays,
    },
    account: {
      login: account.label ?? account.description ?? null,
      status: account.status ?? null,
      result: null,
      phase: details.programName ?? null,
      productLine: details.programName ?? null,
      currency: account.programCurrency ?? null,
      initialBalance: num(s.startingBalance),
      accountStart: account.startDate ?? null,
      accountEnd: null,
      profitTargetLimit: isFunded ? null : targetLevel != null ? Math.abs(targetLevel) : null,
      maxLossLimit: maxLossLimit != null ? Math.abs(maxLossLimit) : null,
      maxDailyLossLimit: dailyLossLimit != null ? Math.abs(dailyLossLimit) : null,
      minTradingDays: num(s.minTradingDays),
    },
    daily: days.map(({ date, pnl, trades, lots }) => ({ date, pnl, trades, lots })),
    /**
     * Unlike FTMO, this API ships the account's whole daily history, so a fresh
     * dashboard doesn't have to spend weeks accumulating hourly points before
     * the chart says anything. These are seeded once, only when there is no
     * prior history for this provider.
     */
    backfill: days
      .filter((d) => d.balance != null)
      .map((d) => ({
        t: `${d.date}T23:59:00.000Z`,
        equity: d.equity ?? d.balance,
        balance: d.balance,
        todayPnl: d.pnl,
        seeded: true,
      })),
  };
}
