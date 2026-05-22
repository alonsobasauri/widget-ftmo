# widget-ftmo

Android home-screen widget for monitoring a public FTMO **Account MetriX** share URL.

It calls FTMO's unauthenticated public endpoint
`GET https://gw2.ftmo.com/public-api/v1/metrix/{login}/{sharingCode}` directly
from the device — no backend, no FTMO login. See `ftmo-analysis.md` for the
endpoint discovery write-up.

## What the widget shows

Three responsive layouts, switched automatically based on the cell size the
user gives the widget on their launcher:

| Size | Content |
|------|---------|
| **Small** (~2x1)  | Status badge, login id, Equity, today's P/L |
| **Medium** (~3x2) | Above + progress bars for Profit Target, Max Daily Loss, Max Loss |
| **Large** (~3x3+) | Above + Balance, Win Rate, Profit Factor, last 5 days' Daily Summary |

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

No DI framework, no Retrofit — two GET endpoints don't justify the weight.

## Project layout

```
app/src/main/kotlin/com/basauri/ftmowidget/
├── data/           # API client, DTOs, DataStore, formatters
├── widget/         # GlanceAppWidget + per-size composables
├── work/           # RefreshWorker (15-min WorkManager)
└── config/         # ConfigActivity (paste share URL, test, save)
```

The endpoint analysis lives in [`ftmo-analysis.md`](ftmo-analysis.md).

## Building

> **Note:** this sandbox can't reach `dl.google.com`, so the build was not
> verified end-to-end here. Build on a machine that can reach Google's
> Maven mirror.

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
2. Open the launcher's widget picker → **FTMO MetriX**.
3. The configuration screen opens automatically. Paste the public share URL:
   ```
   https://trader.ftmo.com/live-metrix/{login}/share/{sharingCode}
   ```
   Tap **Test connection** to validate, then **Save**.
4. The widget appears on the home screen and is updated immediately, then
   every 15 minutes by `WorkManager`.

## Endpoints used

| Purpose         | Method | URL |
|-----------------|--------|-----|
| Metrics + objectives + daily summary | GET | `https://gw2.ftmo.com/public-api/v1/metrix/{login}/{sharingCode}` |
| Balance curve (not rendered in v1)   | GET | `https://gw2.ftmo.com/public-api/v1/account/{login}/{sharingCode}/balance-curve` |

Both are public — only `Accept: application/json` is required. No FTMO
account session, no Cloudflare challenge, no CORS preflight on the device
(this isn't a browser).

## Roadmap / known gaps

- No balance curve chart in the widget yet. The endpoint is wired in
  `FtmoClient.fetchBalanceCurve`; rendering needs a custom drawable since
  Glance doesn't include a chart primitive.
- No live WebSocket updates (`wss://gw2.ftmo.com/fss/socket.io/`). Doing
  so would require a foreground service and isn't worth the battery cost
  in v1.
- `glance_default_loading_layout` is used as the initial layout — it ships
  with Glance and shows a small spinner until `provideContent` resolves.
- The launcher icon is a minimal vector mark; replace before publishing.
