// Fetches the FTMO public MetriX endpoint and appends a compact snapshot to the
// history time-series. Mirrors the app's FtmoClient/FtmoModels parsing (wrapper
// unwrapping, Money value/decimal, fraction-vs-percent scores) so the web data
// matches what the widget shows.
//
// Usage: node snapshot.mjs <priorHistoryFile> <outDir>
//   env FTMO_SHARE_URL  full share link, or "login/sharingCode", or "login code"
//
// Writes <outDir>/history.json (array, oldest-first) and <outDir>/latest.json
// (rich current state incl. daily summary). Exits non-zero on fetch failure so a
// missed run does not overwrite good data.

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const BASE_URL = "https://gw2.ftmo.com/public-api/v1";
const SHARE_RE =
  /(\d{4,})\D+([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/;

function parseShare(input) {
  const m = SHARE_RE.exec((input || "").trim());
  if (!m) return null;
  return { login: m[1], code: m[2].toLowerCase() };
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
  if (pct <= 1) pct *= 100; // 0..1 fraction → percent
  while (pct > 100) pct /= 100; // over-scaled (e.g. 2903 → 29.03)
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

async function fetchMetrix(login, code) {
  const url = `${BASE_URL}/metrix/${login}/${code}`;
  const res = await fetch(url, {
    headers: {
      Accept: "application/json",
      Referer: "https://trader.ftmo.com/",
      "User-Agent": "FtmoDashboard/0.1",
    },
  });
  const body = await res.text();
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} from metrix · ${body.slice(0, 200)}`);
  }
  const root = JSON.parse(body);
  return unwrap(root, ["metrixData", "data", "result", "payload"]);
}

function extract(metrix) {
  const stats = metrix.statistics || {};
  const obj = metrix.objectives || {};
  const info = metrix.info || {};

  // Overall loss cap: maxLoss when it has data, else maxMidnightBalanceMaxLoss.
  const maxLossObj =
    obj.maxLoss && objAmount(obj.maxLoss.limit) != null && objAmount(obj.maxLoss.result) != null
      ? obj.maxLoss
      : obj.maxMidnightBalanceMaxLoss || obj.maxLoss;

  // "Today" P&L: latest daily-summary entry, falling back to info fields.
  const daily = Array.isArray(metrix.dailySummary) ? metrix.dailySummary : [];
  const latestDay = daily.length
    ? daily.reduce((a, b) => (a.date > b.date ? a : b))
    : null;
  const todayPnl =
    (latestDay && moneyAmount(latestDay.realizedProfit)) ??
    moneyAmount(info.todaysProfit) ??
    moneyAmount(info.todaysRealizedProfit);

  return {
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
  };
}

function buildLatest(metrix, snapshot, t) {
  const obj = metrix.objectives || {};
  const info = metrix.info || {};
  const daily = (Array.isArray(metrix.dailySummary) ? metrix.dailySummary : []).map((d) => ({
    date: d.date,
    pnl: num(moneyAmount(d.realizedProfit)),
    trades: num(d.tradesCount),
    lots: num(d.lots),
  }));
  return {
    updatedAt: t,
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
    ...snapshot,
    daily,
  };
}

async function main() {
  const [priorFile, outDir] = process.argv.slice(2);
  if (!outDir) {
    console.error("Usage: node snapshot.mjs <priorHistoryFile> <outDir>");
    process.exit(2);
  }
  const raw = (process.env.FTMO_SHARE_URL || "").trim().replace(/^["']|["']$/g, "");
  const id = parseShare(raw);
  if (!id) {
    // Safe diagnostics: never print the value, only its shape, so we can tell why
    // the regex (login digits + UUID sharing code) did not match.
    const hasLogin = /\d{4,}/.test(raw);
    const hasUuid =
      /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/.test(raw);
    console.error(
      `FTMO_SHARE_URL did not parse. diag: empty=${raw.length === 0} length=${raw.length} ` +
        `has4digitLogin=${hasLogin} hasUUIDcode=${hasUuid}. ` +
        `Expected the SHARE link, e.g. https://trader.ftmo.com/live-metrix/<login>/share/<uuid>`
    );
    process.exit(2);
  }

  let history = [];
  try {
    history = JSON.parse(readFileSync(priorFile, "utf8"));
    if (!Array.isArray(history)) history = [];
  } catch {
    history = [];
  }

  const metrix = await fetchMetrix(id.login, id.code);
  const t = new Date().toISOString();
  const snapshot = { t, ...extract(metrix) };
  history.push(snapshot);

  mkdirSync(outDir, { recursive: true });
  writeFileSync(`${outDir}/history.json`, JSON.stringify(history));
  writeFileSync(`${outDir}/latest.json`, JSON.stringify(buildLatest(metrix, snapshot, t), null, 2));
  console.log(`Appended snapshot @ ${t}; history now ${history.length} points.`);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
