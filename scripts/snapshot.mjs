// Fetches a prop-firm account's public share endpoint and appends a compact
// snapshot to the history time-series. Provider-specific parsing lives in
// ./providers/*.mjs and mirrors the app's Kotlin mappers, so the dashboard and
// the widget agree on every figure.
//
// Usage: node snapshot.mjs <priorHistoryFile> <outDir>
//   env SHARE_URL       full share link for FTMO or Blue Guardian
//   env FTMO_SHARE_URL  legacy name, still honoured
//
// Writes <outDir>/history.json (array, oldest-first) and <outDir>/latest.json
// (rich current state incl. daily summary). Exits non-zero on fetch failure so a
// missed run does not overwrite good data.

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { byId, parseShare } from "./providers/index.mjs";

function readHistory(file) {
  try {
    const parsed = JSON.parse(readFileSync(file, "utf8"));
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * History is per-account. When the configured share points at a different
 * provider than the stored points, keeping them in one series would draw a
 * straight line between two unrelated accounts — so start fresh instead.
 */
function historyForProvider(history, providerId) {
  if (!history.length) return [];
  const last = history[history.length - 1];
  // Points written before multi-provider support are all FTMO.
  const lastProvider = last.provider ?? "ftmo";
  return lastProvider === providerId ? history : [];
}

function buildLatest(snapshot, point, t, providerId) {
  return {
    updatedAt: t,
    provider: providerId,
    ...snapshot.account,
    ...point,
    daily: snapshot.daily,
  };
}

async function main() {
  const [priorFile, outDir] = process.argv.slice(2);
  if (!outDir) {
    console.error("Usage: node snapshot.mjs <priorHistoryFile> <outDir>");
    process.exit(2);
  }

  const raw = (process.env.SHARE_URL || process.env.FTMO_SHARE_URL || "")
    .trim()
    .replace(/^["']|["']$/g, "");
  const identity = parseShare(raw);
  if (!identity) {
    // Safe diagnostics: never print the value, only its shape, so we can tell
    // why neither provider's pattern matched.
    const hasLogin = /\d{4,}/.test(raw);
    const hasUuid =
      /[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/.test(raw);
    const hasObjectId = /(?<![0-9a-fA-F])[0-9a-fA-F]{24}(?![0-9a-fA-F])/.test(raw);
    console.error(
      `SHARE_URL did not parse. diag: empty=${raw.length === 0} length=${raw.length} ` +
        `has4digitLogin=${hasLogin} hasUUIDcode=${hasUuid} hasObjectId=${hasObjectId}. ` +
        `Expected an FTMO share link (https://trader.ftmo.com/live-metrix/<login>/share/<uuid>) ` +
        `or a Blue Guardian one (https://trader.blueguardian.com/shared/<24-hex id>)`
    );
    process.exit(2);
  }

  const provider = byId(identity.provider);
  const snapshot = await provider.fetchSnapshot(identity);

  const t = new Date().toISOString();
  const point = { t, provider: provider.id, ...snapshot.metrics };

  let history = historyForProvider(readHistory(priorFile), provider.id);
  let seeded = 0;
  // Providers that publish a full daily history let a fresh dashboard start with
  // a real chart instead of a single point. Only on a cold start: once there are
  // live points, appending backdated ones would reorder the series.
  if (!history.length && snapshot.backfill?.length) {
    history = snapshot.backfill.map((b) => ({ provider: provider.id, ...b }));
    seeded = history.length;
  }
  history.push(point);

  mkdirSync(outDir, { recursive: true });
  writeFileSync(`${outDir}/history.json`, JSON.stringify(history));
  writeFileSync(
    `${outDir}/latest.json`,
    JSON.stringify(buildLatest(snapshot, point, t, provider.id), null, 2)
  );
  const seedNote = seeded ? ` (seeded ${seeded} backfilled days)` : "";
  console.log(
    `[${provider.displayName}] appended snapshot @ ${t}; history now ${history.length} points${seedNote}.`
  );
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
