# widget-ftmo

Android home-screen widget for monitoring a prop-firm account from its public
share URL. Two firms are supported:

| Firm | Share URL |
|------|-----------|
| **FTMO** | `https://trader.ftmo.com/live-metrix/{login}/share/{sharingCode}` |
| **Blue Guardian** | `https://trader.blueguardian.com/shared/{sharedId}` |

Both firms' endpoints are unauthenticated and are called directly from the
device — no backend, no login for either. Paste either link into the config
screen and the right provider is detected automatically. The endpoint discovery
write-ups are in [`ftmo-analysis.md`](ftmo-analysis.md) and
[`blueguardian-analysis.md`](blueguardian-analysis.md).

## What the widget shows

Three responsive layouts, switched automatically based on the cell size the
user gives the widget on their launcher:

| Size | Content |
|------|---------|
| **Small** (~2x1)  | Status badge, account label, Equity, today's P/L |
| **Medium** (~3x2) | Above + up to three objective progress bars |
| **Large** (~3x3+) | Above + Balance, Win Rate, Profit Factor, last 5 days' Daily Summary |
| **XLarge** (~4x4) | Above + equity sparkline and the full performance grid |

Which objectives appear depends on the account: a funded account has no profit
target, so that bar is not drawn rather than shown empty. The same applies to
per-stat cells — Blue Guardian publishes no Sharpe ratio or lot volume, so those
tiles are dropped instead of printing dashes.

Tap anywhere on the widget to force-refresh. Errors keep the last good
snapshot visible with a small `stale:` annotation so you don't lose context
when the network blips.

## Stack

- Kotlin **2.0.21**, AGP **8.7.3**, Gradle **8.10.2**
- `minSdk 26`, `targetSdk 35`
- **Jetpack Glance** for the widget itself (`androidx.glance:glance-appwidget:1.1.1`)
- **WorkManager** for periodic refresh (15 min, the minimum periodic interval)
- **DataStore Preferences** for persistent config + cached snapshot
- **OkHttp** + **kotlinx.serialization** for the API client
- **Jetpack Compose** Material3 for the configuration screen

No DI framework, no Retrofit — a handful of endpoints don't justify the weight.

## Project layout

```
app/src/main/kotlin/com/basauri/ftmowidget/
├── data/           # providers, normalized model, DataStore, formatters
├── widget/         # GlanceAppWidget + per-size composables
├── work/           # RefreshWorker (15-min WorkManager)
└── config/         # ConfigActivity (paste share URL, test, save)
```

### How multi-provider works

Each firm implements `Provider` (`parse` a share link → fetch → map), and every
mapper produces the same `AccountSnapshot`: plain amounts in one currency, an
`objectives` list, and a daily series. The widget layouts and `RefreshWorker`
read only `AccountSnapshot` and never see a firm-specific DTO, so adding a third
firm means adding one file to `data/` and one entry to `Providers.all`.

The credential is stored as a single opaque token plus a provider id, so the
store doesn't grow a column per firm — FTMO packs its `login` and `sharingCode`
into `login:sharingCode`, Blue Guardian's token is just its ObjectId.

`web/scripts/providers/*.mjs` mirrors the same split on the dashboard side.

## Building

Requirements: JDK 17+ and Android SDK with platform 35 + build-tools 35.

```bash
git clone https://github.com/alonsobasauri/widget-ftmo.git
cd widget-ftmo
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install with `adb install`
or via Android Studio's *Run* command.

## Adding the widget

1. Install the debug build on the device.
2. Open the launcher's widget picker → **Prop Account**.
3. The configuration screen opens automatically. Paste either share URL:
   ```
   https://trader.ftmo.com/live-metrix/{login}/share/{sharingCode}
   https://trader.blueguardian.com/shared/{sharedId}
   ```
   Tap **Test connection** to validate — it reports which firm was detected —
   then **Save**.
4. The widget appears on the home screen and is updated immediately, then
   every 15 minutes by `WorkManager`.

## Endpoints used

| Firm | Purpose | Method | URL |
|------|---------|--------|-----|
| FTMO | Metrics + objectives + daily summary | GET | `https://gw2.ftmo.com/public-api/v1/metrix/{login}/{sharingCode}` |
| FTMO | Balance curve (not rendered)         | GET | `https://gw2.ftmo.com/public-api/v1/account/{login}/{sharingCode}/balance-curve` |
| Blue Guardian | Statistics + account + program rules | GET | `https://api.trader.blueguardian.com/v1/accounts/shared/{sharedId}` |
| Blue Guardian | Daily balance/equity series | POST | `https://api.trader.blueguardian.com/v1/accounts/shared/{sharedId}/growth` |

All are public. FTMO needs only `Accept: application/json`; Blue Guardian needs
no headers at all for the GET, and `Content-Type: application/json` for the POST
(whose body is `{"filter":"daily"}` — without the content type the body is
ignored). No account session, no Cloudflare challenge, no CORS preflight on the
device (this isn't a browser).

## Roadmap / known gaps

- Blue Guardian exposes no per-day trade count or lot volume, so those columns
  are dropped from the daily table for those accounts; per-day P&L is derived by
  differencing consecutive end-of-day balances.
- FTMO's per-trade balance curve is still unused. The endpoint is wired in
  `FtmoClient.fetchBalanceCurve`; the sparkline is currently driven by the daily
  series, which both providers supply.
- No live WebSocket updates (`wss://gw2.ftmo.com/fss/socket.io/`). Doing
  so would require a foreground service and isn't worth the battery cost
  in v1.
- `glance_default_loading_layout` is used as the initial layout — it ships
  with Glance and shows a small spinner until `provideContent` resolves.
- The launcher icon is a minimal vector mark; replace before publishing.
