// FTMO's public MetriX share. Mirrors the app's FtmoProvider mapping so the
// dashboard and the widget agree on every figure.

const BASE_URL = "https://gw2.ftmo.com/public-api/v1";
const SHARE_RE =
  /(\d{4,})\D+([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/;

export const id = "ftmo";
export const displayName = "FTMO";

export function parse(input) {
  const m = SHARE_RE.exec((input || "").trim());
  if (!m) return null;
  return { provider: id, token: `${m[1]}:${m[2].toLowerCase()}` };
}

// Walk wrapper objects the same way FtmoClient.decode does.
function unwrap(root, keys) {
  if (root && typeof root === "object" && !Array.isArray(root)) {
    if ("statistics" in root || "objectives" in root) return root;
    for (const k of keys) if (root[k]) return root[k];
    const vals = Object.values(root);
    if (vals.length === 1 && typeof vals[0] === "object") return vals[0];
  }
  return root;
}

function moneyAmount(m) {
  if (!m || typeof m.value !== "number") return null;
  return m.value / 10 ** (m.decimal ?? 0);
}

// Objective limit/result envelopes carry value+decimal like Money.
function objAmount(v) {
  if (!v || typeof v.value !== "number") return null;
  return v.value / 10 ** (v.decimal ?? 0);
}

// Scores are either a raw percent or a 0..1 fraction (type === "fraction").
function scorePct(s) {
  if (!s || typeof s.value !== "number") return null;
  return s.type === "fraction" ? s.value * 100 : s.value;
}

// Win rate is bounded [0,100]%, but FTMO sometimes tags it "fraction" while
// sending an already-percent value (29.03), which scorePct would blow up to
// 2903%. Treat <=1 as a real fraction, larger values as already a percent.
function winRatePct(s) {
  if (!s || typeof s.value !== "number") return null;
  let pct = s.value;
  if (pct <= 1) pct *= 100;
  while (pct > 100) pct /= 100;
  return pct;
}

function progressPct(obj) {
  if (!obj) return null;
  const l = objAmount(obj.limit);
  const r = objAmount(obj.result);
  if (l == null || r == null || l === 0) return null;
  return (r / l) * 100;
}

function num(x) {
  return typeof x === "number" && isFinite(x) ? x : null;
}

export function shareUrl(identity) {
  const [login, code] = identity.token.split(":");
  return `https://trader.ftmo.com/live-metrix/${login}/share/${code}`;
}

async function fetchMetrix(login, code) {
  const url = `${BASE_URL}/metrix/${login}/${code}`;
  const res = await fetch(url, {
    headers: {
      Accept: "application/json",
      Referer: "https://trader.ftmo.com/",
      "User-Agent": "PropDashboard/0.1",
    },
  });
  const body = await res.text();
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} from metrix · ${body.slice(0, 200)}`);
  }
  return unwrap(JSON.parse(body), ["metrixData", "data", "result", "payload"]);
}

export async function fetchSnapshot(identity) {
  const [login, code] = identity.token.split(":");
  const metrix = await fetchMetrix(login, code);

  const stats = metrix.statistics || {};
  const obj = metrix.objectives || {};
  const info = metrix.info || {};

  // Overall loss cap: maxLoss when it has data, else maxMidnightBalanceMaxLoss.
  const maxLossObj =
    obj.maxLoss && objAmount(obj.maxLoss.limit) != null && objAmount(obj.maxLoss.result) != null
      ? obj.maxLoss
      : obj.maxMidnightBalanceMaxLoss || obj.maxLoss;

  // "Today" P&L: latest daily-summary entry, falling back to info fields.
  const dailyRaw = Array.isArray(metrix.dailySummary) ? metrix.dailySummary : [];
  const latestDay = dailyRaw.length ? dailyRaw.reduce((a, b) => (a.date > b.date ? a : b)) : null;
  const realizedToday =
    (latestDay && moneyAmount(latestDay.realizedProfit)) ??
    moneyAmount(info.todaysProfit) ??
    moneyAmount(info.todaysRealizedProfit);
  // Same composition as the widget: realized today plus the whole floating
  // position, which for FTMO has to come from the equity/balance gap.
  const eq = moneyAmount(stats.equity);
  const bal = moneyAmount(stats.balance);
  const floating = eq != null && bal != null ? eq - bal : null;
  const todayPnl =
    realizedToday == null && floating == null ? null : (realizedToday ?? 0) + (floating ?? 0);

  return {
    provider: id,
    metrics: {
      equity: num(moneyAmount(stats.equity)),
      balance: num(moneyAmount(stats.balance)),
      todayPnl: num(todayPnl),
      profitPct: num(progressPct(obj.profit)),
      profitResult: num(objAmount(obj.profit?.result)),
      maxLossUsedPct: num(progressPct(maxLossObj)),
      maxDailyLossUsedPct: num(progressPct(obj.maxDailyLoss)),
      winRate: num(winRatePct(stats.winRate)),
      profitFactor: num(stats.profitFactor),
      expectancy: num(moneyAmount(stats.expectancy)),
      sharpe: num(stats.sharpeRate),
      avgRR: num(stats.avgRiskToRewardRate),
      lots: num(stats.lots),
      tradesCount: num(stats.totalTradesCount ?? stats.tradesCount),
      consistency: num(scorePct(metrix.consistencyScore)),
      discipline: num(scorePct(metrix.disciplineScore)),
      tradingDaysCount: Array.isArray(metrix.tradingDays) ? metrix.tradingDays.length : null,
    },
    account: {
      login: metrix.login ?? null,
      status: info.accountStatus ?? null,
      result: info.accountResult ?? null,
      phase: info.accountStageType ?? null,
      productLine: info.productLine ?? null,
      currency: metrix.currency ?? info.initialBalance?.currency ?? null,
      initialBalance: num(moneyAmount(info.initialBalance)),
      accountStart: info.accountStart ?? null,
      accountEnd: info.accountEnd ?? null,
      profitTargetLimit: num(objAmount(obj.profit?.limit)),
      maxLossLimit: num(objAmount((obj.maxLoss || obj.maxMidnightBalanceMaxLoss)?.limit)),
      maxDailyLossLimit: num(objAmount(obj.maxDailyLoss?.limit)),
      minTradingDays: num(objAmount(obj.minTradingDays?.limit)),
    },
    daily: dailyRaw.map((d) => ({
      date: d.date,
      pnl: num(moneyAmount(d.realizedProfit)),
      trades: num(d.tradesCount),
      lots: num(d.lots),
    })),
    // FTMO exposes only the present state, so there is nothing to backfill.
    backfill: [],
  };
}
